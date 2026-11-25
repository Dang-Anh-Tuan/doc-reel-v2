package com.tuan.docreel.job;

import com.tuan.docreel.common.Result;
import com.tuan.docreel.script.FieldError;
import com.tuan.docreel.script.ScriptParser;
import com.tuan.docreel.script.VideoScript;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Các thao tác REST trên job (UC2 gửi, UC3 xem, UC4 retry). Controller chỉ gọi lớp này. */
@Service
public class JobApiService {

    /** Cỡ trang tối đa (cũng là mặc định: giữ hành vi P3 — không truyền {@code size} thì nhận tối đa 200 dòng). */
    public static final int MAX_PAGE_SIZE = 200;

    private final JobService jobs;
    private final JobRepository repository;
    private final JobAttemptRepository attempts;
    private final JobSlides slides;
    private final JsonMapper mapper;
    private final TtsCostEstimator costs;
    private final ScriptParser parser = new ScriptParser();

    public JobApiService(
            JobService jobs,
            JobRepository repository,
            JobAttemptRepository attempts,
            JobSlides slides,
            JsonMapper mapper,
            TtsCostEstimator costs) {
        this.jobs = jobs;
        this.repository = repository;
        this.attempts = attempts;
        this.slides = slides;
        this.mapper = mapper;
        this.costs = costs;
    }

    /** Validate rồi tạo job. Không {@code @Transactional}: {@link JobService#submit} tự quản transaction. */
    public UUID submit(byte[] json) {
        Result<VideoScript, List<FieldError>> parsed = parser.parse(json);
        if (!parsed.isOk()) {
            throw new InvalidScriptException(parsed.error());
        }
        SubmitResult r = jobs.submit(parsed.value(), null);
        if (!r.created()) {
            JobState state =
                    repository.findById(r.jobId()).map(VideoJob::getState).orElse(null);
            throw new DuplicateJobException(r.jobId(), state);
        }
        return r.jobId();
    }

    /**
     * Job mới nhất trước; {@code state} null = mọi trạng thái. {@code page} &lt; 0 → 0, {@code size} kẹp vào
     * 1..{@value #MAX_PAGE_SIZE}.
     */
    @Transactional(readOnly = true)
    public JobPage list(JobState state, int page, int size) {
        int p = Math.max(0, page);
        int s = Math.clamp(size, 1, MAX_PAGE_SIZE);
        var request =
                PageRequest.of(p, s, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        Page<VideoJob> rows = state == null ? repository.findAll(request) : repository.findByState(state, request);
        List<JobSummary> items = rows.getContent().stream().map(this::summary).toList();
        return new JobPage(items, rows.getTotalElements(), p, s);
    }

    @Transactional(readOnly = true)
    public JobDetail detail(UUID id) {
        VideoJob job = repository.findById(id).orElseThrow(() -> new JobNotFoundException(id));
        VideoScript script = slides.script(job).orElse(null);
        return JobDetail.of(
                job,
                script == null ? null : script.title(),
                attempts.findByJobIdOrderByStartedAt(id),
                slides.rendered(id, script),
                costs);
    }

    /** PNG thứ {@code n} của job; job không có / {@code n} sai / file không thuộc job → rỗng (controller trả 404). */
    @Transactional(readOnly = true)
    public Optional<Path> slide(UUID id, String n) {
        return repository.findById(id).flatMap(job -> slides.find(job, n));
    }

    /** FAILED → RECEIVED. Hai retry đồng thời: bên thua gặp lỗi khóa lạc quan (→ 409 ở handler). */
    @Transactional
    public JobSummary retry(UUID id) {
        VideoJob job = repository.findById(id).orElseThrow(() -> new JobNotFoundException(id));
        if (job.getState() != JobState.FAILED) {
            throw new JobNotRetryableException(id, job.getState());
        }
        job.retry();
        return summary(repository.saveAndFlush(job));
    }

    private JobSummary summary(VideoJob job) {
        return JobSummary.of(job, title(job.getScript()), costs);
    }

    /** Chỉ đọc trường {@code title} (rẻ hơn parse cả kịch bản cho mỗi dòng danh sách). */
    private String title(String scriptJson) {
        if (scriptJson == null) {
            return null;
        }
        try {
            JsonNode title = mapper.readTree(scriptJson).get("title");
            return title != null && title.isString() ? title.asString() : null;
        } catch (JacksonException e) {
            return null;
        }
    }
}
