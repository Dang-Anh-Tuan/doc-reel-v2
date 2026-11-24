package com.tuan.docreel.pipeline;

import static org.mockito.Mockito.mock;

import com.tuan.docreel.common.AppProperties;
import com.tuan.docreel.job.JobAttemptRepository;
import com.tuan.docreel.job.JobRepository;
import com.tuan.docreel.job.JobService;
import com.tuan.docreel.job.JobState;
import com.tuan.docreel.job.VideoJob;
import com.tuan.docreel.notify.JobStateChangedEvent;
import com.tuan.docreel.script.ScriptParser;
import com.tuan.docreel.script.VideoScript;
import com.tuan.docreel.support.TestcontainersConfiguration;
import com.tuan.docreel.system.SystemSelfCheck;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Dựng worker thật (Postgres Testcontainers) với bước giả: kiểm điều phối độc lập với media. */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, PipelineTestBase.Events.class})
abstract class PipelineTestBase {

    /** Gom event đã commit (AFTER_COMMIT) để test đếm. */
    static final List<JobStateChangedEvent> EVENTS = new CopyOnWriteArrayList<>();

    @TestConfiguration(proxyBeanMethods = false)
    static class Events {
        @Bean
        Collector collector() {
            return new Collector();
        }
    }

    static class Collector {
        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void on(JobStateChangedEvent event) {
            EVENTS.add(event);
        }
    }

    @Autowired
    JobService jobs;

    @Autowired
    JobRepository repository;

    @Autowired
    JobAttemptRepository attempts;

    @Autowired
    AppProperties props;

    @Autowired
    JsonMapper mapper;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    PlatformTransactionManager tm;

    @Autowired
    JdbcTemplate jdbc;

    SystemSelfCheck selfCheck = mock(SystemSelfCheck.class);

    @BeforeEach
    void clean() throws IOException {
        jdbc.update("delete from job_attempt");
        jdbc.update("delete from video_job");
        EVENTS.clear();
        Path work = props.dirs().work();
        if (Files.exists(work)) {
            try (var walk = Files.walk(work)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    PipelineWorker worker(RetryPolicy policy, Step... steps) {
        return new PipelineWorker(repository, attempts, List.of(steps), policy, props, selfCheck, events, mapper, tm);
    }

    /** 4 bước giả đủ pipeline; {@code behaviours} tùy chỉnh theo state. */
    List<FakeStep> fakeSteps() {
        List<FakeStep> list = new ArrayList<>();
        for (JobState s : List.of(JobState.RECEIVED, JobState.RENDERED, JobState.VOICED, JobState.ASSEMBLED)) {
            list.add(new FakeStep(s));
        }
        return list;
    }

    static Step[] arr(List<FakeStep> steps) {
        return steps.toArray(new Step[0]);
    }

    /** Job mới (title khác nhau → khóa idempotency khác nhau). */
    UUID newJob(String title) {
        try {
            VideoScript base = new ScriptParser()
                    .parse(Files.readAllBytes(Path.of("samples", "full", "sample-all-scenes.json")))
                    .value();
            VideoScript s = new VideoScript(
                    base.topicId(),
                    base.track(),
                    title,
                    base.version(),
                    base.videoType(),
                    base.theme(),
                    base.sources(),
                    base.scenes(),
                    base.origin());
            return jobs.submit(s, null).jobId();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    VideoJob job(UUID id) {
        return repository.findById(id).orElseThrow();
    }

    /** Chạy worker tới khi hết việc (tối đa vài trăm bước). */
    static int drain(PipelineWorker worker) {
        int n = 0;
        while (worker.runOnce() && n < 500) {
            n++;
        }
        return n;
    }

    /** Bước giả: đếm số lần chạy theo job, hành vi tùy chỉnh; bước ASSEMBLED ghi youtube-id.txt như bước thật. */
    static class FakeStep implements Step {
        final JobState supported;
        final Map<UUID, AtomicInteger> runs = new ConcurrentHashMap<>();
        Consumer<StepContext> behaviour = ctx -> {};
        String ttsProvider;

        FakeStep(JobState supported) {
            this.supported = supported;
        }

        int runs(UUID job) {
            AtomicInteger n = runs.get(job);
            return n == null ? 0 : n.get();
        }

        int totalRuns() {
            return runs.values().stream().mapToInt(AtomicInteger::get).sum();
        }

        @Override
        public boolean supports(JobState state) {
            return state == supported;
        }

        @Override
        public StepResult execute(StepContext ctx) {
            runs.computeIfAbsent(ctx.jobId(), k -> new AtomicInteger()).incrementAndGet();
            behaviour.accept(ctx);
            if (supported == JobState.ASSEMBLED) {
                try {
                    Files.writeString(ctx.workDir().resolve("youtube-id.txt"), youtubeIdOf(ctx.jobId()));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            JobState next = JobState.values()[supported.ordinal() + 1];
            return new StepResult(next, false, ttsProvider);
        }
    }

    /** Id YouTube giả, ngắn như thật (cột youtube_id dài tối đa 32). */
    static String youtubeIdOf(UUID jobId) {
        return "yt-" + jobId.toString().substring(0, 8);
    }

    static TransactionTemplate template(PlatformTransactionManager tm) {
        return new TransactionTemplate(tm);
    }
}
