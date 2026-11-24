package com.tuan.docreel.pipeline;

import com.tuan.docreel.common.AppProperties;
import com.tuan.docreel.job.JobAttempt;
import com.tuan.docreel.job.JobAttemptRepository;
import com.tuan.docreel.job.JobRepository;
import com.tuan.docreel.job.JobState;
import com.tuan.docreel.job.JobStep;
import com.tuan.docreel.job.VideoJob;
import com.tuan.docreel.notify.JobStateChangedEvent;
import com.tuan.docreel.script.VideoScript;
import com.tuan.docreel.system.SystemSelfCheck;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Điều phối pipeline (UC1) + retry (UC4). Mỗi lần {@link #runOnce()} làm đúng MỘT bước của MỘT job trong một
 * transaction: claim bằng {@code FOR UPDATE SKIP LOCKED} (khóa giữ tới commit nên hai worker không lấy trùng), chạy
 * {@link Step} theo state, ghi {@code job_attempt}, đổi state và phát {@link JobStateChangedEvent} (listener chỉ nhận
 * sau commit). Giữ khóa suốt bước là chủ ý: không cần cột "đang chạy", crash thì DB tự nhả khóa.
 *
 * <p>Retry tự động: lỗi tạm → ghi attempt {@code RETRY}, job giữ nguyên state, chờ backoff (nhớ trong RAM; restart
 * app thì thử lại ngay, số lần vẫn đếm được từ {@code job_attempt}). Hết lượt hoặc lỗi nội dung → {@code FAILED}.
 */
@Component
public class PipelineWorker {

    static final String OUTCOME_SUCCESS = "SUCCESS";
    static final String OUTCOME_SKIPPED = "SKIPPED";
    static final String OUTCOME_RETRY = "RETRY";
    static final String OUTCOME_FAILED = "FAILED";
    private static final int ERROR_MAX = 2000;
    private static final UUID NIL = new UUID(0, 0);

    private static final Logger log = LoggerFactory.getLogger(PipelineWorker.class);

    private final JobRepository jobs;
    private final JobAttemptRepository attempts;
    private final List<Step> steps;
    private final RetryPolicy retryPolicy;
    private final AppProperties.Dirs dirs;
    private final SystemSelfCheck selfCheck;
    private final ApplicationEventPublisher events;
    private final JsonMapper mapper;
    private final TransactionTemplate tx;
    private final Map<UUID, Instant> notBefore = new ConcurrentHashMap<>();

    public PipelineWorker(
            JobRepository jobs,
            JobAttemptRepository attempts,
            List<Step> steps,
            RetryPolicy retryPolicy,
            AppProperties props,
            SystemSelfCheck selfCheck,
            ApplicationEventPublisher events,
            JsonMapper mapper,
            PlatformTransactionManager txManager) {
        this.jobs = jobs;
        this.attempts = attempts;
        this.steps = List.copyOf(steps);
        this.retryPolicy = retryPolicy;
        this.dirs = props.dirs();
        this.selfCheck = selfCheck;
        this.events = events;
        this.mapper = mapper;
        this.tx = new TransactionTemplate(txManager);
    }

    /** @return true nếu đã xử lý một bước (nên gọi tiếp ngay), false nếu không còn việc / đang tạm dừng / có lỗi */
    public boolean runOnce() {
        if (selfCheck.hasCriticalFailure()) {
            log.warn("Self-check có mục CRITICAL → worker không nhận job");
            return false;
        }
        Processed done;
        try {
            done = tx.execute(status -> processNext());
        } catch (RuntimeException e) {
            // vd khóa lạc quan: job bị sửa giữa chừng → transaction rollback, không event, lần sau làm lại
            log.warn("Không commit được bước pipeline, bỏ qua lần này: {}", e.toString());
            return false;
        }
        if (done == null) {
            return false;
        }
        if (done.uploaded()) {
            archiveInput(done.jobId());
        }
        return true;
    }

    private record Processed(UUID jobId, boolean uploaded) {}

    private Processed processNext() {
        VideoJob job = jobs.claimNext(blockedIds()).orElse(null);
        if (job == null) {
            return null;
        }
        MDC.put("jobId", job.getId().toString());
        try {
            return process(job);
        } finally {
            MDC.remove("jobId");
        }
    }

    private Processed process(VideoJob job) {
        JobState current = job.pipelineState();
        JobState before = job.getState();
        Step step = current == JobState.UPLOADED
                ? null
                : steps.stream().filter(s -> s.supports(current)).findFirst().orElse(null);
        if (step == null) {
            fail(job, before, "Không có bước nào xử lý state " + current);
            return new Processed(job.getId(), false);
        }
        JobStep jobStep = JobStep.values()[current.ordinal()];
        int attemptNo = failedAttempts(job.getId(), jobStep) + 1;
        long start = System.nanoTime();
        log.info("Chạy bước {} (lần {})", jobStep, attemptNo);
        try {
            Path workDir = dirs.work().resolve(job.getId().toString());
            Files.createDirectories(workDir);
            VideoScript script = mapper.readValue(job.getScript(), VideoScript.class);
            StepResult result = step.execute(new StepContext(job.getId(), current, script, workDir, attemptNo));
            if (result.nextState() != jobStep.resultingState()) {
                throw new IllegalStateException(
                        "Bước " + jobStep + " trả state " + result.nextState() + ", cần " + jobStep.resultingState());
            }
            if (result.ttsProvider() != null) {
                job.pinTtsProvider(result.ttsProvider());
            }
            job.resume();
            if (jobStep == JobStep.UPLOAD) {
                job.markUploaded(readYoutubeId(workDir));
            } else {
                job.completeStep(jobStep);
            }
            attempts.save(new JobAttempt(
                    job.getId(),
                    jobStep,
                    attemptNo,
                    result.skipped() ? OUTCOME_SKIPPED : OUTCOME_SUCCESS,
                    millis(start),
                    null));
            notBefore.remove(job.getId());
            events.publishEvent(new JobStateChangedEvent(job.getId(), job.getTopicId(), before, job.getState(), null));
            return new Processed(job.getId(), job.getState() == JobState.UPLOADED);
        } catch (IOException | RuntimeException e) {
            return onError(job, before, jobStep, attemptNo, start, e);
        }
    }

    private Processed onError(VideoJob job, JobState before, JobStep jobStep, int attemptNo, long start, Exception e) {
        String message = message(e);
        RetryPolicy.Decision decision = retryPolicy.decide(e, attemptNo);
        if (decision.retry()) {
            attempts.save(new JobAttempt(job.getId(), jobStep, attemptNo, OUTCOME_RETRY, millis(start), message));
            notBefore.put(job.getId(), Instant.now().plus(decision.delay()));
            log.warn("Bước {} lỗi (lần {}): {} → thử lại sau {}", jobStep, attemptNo, message, decision.delay());
        } else {
            attempts.save(new JobAttempt(job.getId(), jobStep, attemptNo, OUTCOME_FAILED, millis(start), message));
            notBefore.remove(job.getId());
            log.error("Bước {} lỗi (lần {}), dừng job: {}", jobStep, attemptNo, message, e);
            fail(job, before, message);
        }
        return new Processed(job.getId(), false);
    }

    private void fail(VideoJob job, JobState before, String message) {
        job.fail(message);
        events.publishEvent(new JobStateChangedEvent(job.getId(), job.getTopicId(), before, JobState.FAILED, message));
    }

    /** Số lần liên tiếp bước này đã lỗi-và-chờ-retry (gặp SUCCESS / FAILED / bước khác thì dừng đếm). */
    private int failedAttempts(UUID jobId, JobStep step) {
        List<JobAttempt> history = attempts.findByJobIdOrderByStartedAt(jobId);
        int count = 0;
        for (int i = history.size() - 1; i >= 0; i--) {
            JobAttempt a = history.get(i);
            if (!a.getStep().equals(step.name()) || !OUTCOME_RETRY.equals(a.getOutcome())) {
                break;
            }
            count++;
        }
        return count;
    }

    private List<UUID> blockedIds() {
        Instant now = Instant.now();
        notBefore.values().removeIf(t -> !t.isAfter(now));
        List<UUID> ids = new ArrayList<>(notBefore.keySet());
        ids.add(NIL); // NOT IN () rỗng là lỗi SQL
        return ids;
    }

    private static String readYoutubeId(Path workDir) throws IOException {
        Path idFile = workDir.resolve("youtube-id.txt");
        if (!Files.isRegularFile(idFile)) {
            throw new IllegalStateException("Bước upload xong nhưng thiếu " + idFile.getFileName());
        }
        String id = Files.readString(idFile, StandardCharsets.UTF_8).strip();
        if (id.isEmpty()) {
            throw new IllegalStateException(idFile.getFileName() + " rỗng");
        }
        return id;
    }

    /** UPLOADED → work/{id}/input.json sang done/{id}-input.json. Lỗi chỉ log vì job đã xong. */
    private void archiveInput(UUID jobId) {
        Path input = dirs.work().resolve(jobId.toString()).resolve("input.json");
        if (!Files.isRegularFile(input)) {
            return; // job tạo qua REST: không có file
        }
        try {
            Files.createDirectories(dirs.done());
            Files.move(input, dirs.done().resolve(jobId + "-input.json"), StandardCopyOption.REPLACE_EXISTING);
            log.info("Job {} xong → done/{}-input.json", jobId, jobId);
        } catch (IOException e) {
            log.warn("Không chuyển được input của job {} sang done/: {}", jobId, e.getMessage());
        }
    }

    private static int millis(long startNanos) {
        return (int) Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private static String message(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return m.length() > ERROR_MAX ? m.substring(0, ERROR_MAX) : m;
    }
}
