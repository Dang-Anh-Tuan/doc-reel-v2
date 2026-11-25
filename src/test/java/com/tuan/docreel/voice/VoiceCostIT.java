package com.tuan.docreel.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;

import com.tuan.docreel.common.AppProperties;
import com.tuan.docreel.common.AppProperties.TtsProviderName;
import com.tuan.docreel.job.JobApiService;
import com.tuan.docreel.job.JobAttemptRepository;
import com.tuan.docreel.job.JobDetail;
import com.tuan.docreel.job.JobRepository;
import com.tuan.docreel.job.JobService;
import com.tuan.docreel.job.JobState;
import com.tuan.docreel.job.JobSummary;
import com.tuan.docreel.pipeline.PipelineWorker;
import com.tuan.docreel.pipeline.RetryPolicy;
import com.tuan.docreel.pipeline.Step;
import com.tuan.docreel.pipeline.StepContext;
import com.tuan.docreel.pipeline.StepResult;
import com.tuan.docreel.script.ScriptParser;
import com.tuan.docreel.script.VideoScript;
import com.tuan.docreel.support.TestcontainersConfiguration;
import com.tuan.docreel.system.SystemSelfCheck;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * P13b: worker thật + Postgres + VoiceStep thật (provider giả) → {@code video_job.tts_chars} bằng đúng số ký tự đã
 * gửi provider; {@code GET /api/jobs/{id}} có khối {@code cost} theo công thức.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class VoiceCostIT {

    @Autowired
    JobService jobs;

    @Autowired
    JobRepository repository;

    @Autowired
    JobAttemptRepository attempts;

    @Autowired
    JobApiService api;

    @Autowired
    AppProperties props;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    JsonMapper mapper;

    @Autowired
    PlatformTransactionManager tm;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() throws Exception {
        jdbc.update("delete from job_attempt");
        jdbc.update("delete from video_job");
        Path work = props.dirs().work();
        if (Files.exists(work)) {
            try (var walk = Files.walk(work)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    /** Bước render giả: RECEIVED → RENDERED. */
    private static Step fakeRender() {
        return new Step() {
            @Override
            public boolean supports(JobState state) {
                return state == JobState.RECEIVED;
            }

            @Override
            public StepResult execute(StepContext context) {
                return new StepResult(JobState.RENDERED, false);
            }
        };
    }

    private UUID newJob() throws Exception {
        VideoScript script = new ScriptParser()
                .parse(Files.readAllBytes(Path.of("samples", "full", "sample-all-scenes.json")))
                .value();
        return jobs.submit(script, null).jobId();
    }

    private Long storedChars(UUID id) {
        return jdbc.queryForObject("select tts_chars from video_job where id = ?", Long.class, id);
    }

    @Test
    void charsSentBeforeMidwayFailureAreKeptAndRetryAddsOnlyNewOnes() throws Exception {
        UUID id = newJob();
        AtomicInteger calls = new AtomicInteger();
        TtsServiceTest.StubProvider eleven =
                new TtsServiceTest.StubProvider(TtsProviderName.ELEVENLABS, true, 10_000_000) {
                    @Override
                    public byte[] synthesize(String s, String p, String n, VoiceSettings settings) {
                        if (calls.incrementAndGet() == 3) { // câu thứ 3 lỗi mạng (một lần)
                            throw new TtsTransientException("mạng rớt");
                        }
                        return super.synthesize(s, p, n, settings);
                    }
                };
        TtsService tts = new TtsService(
                List.of(eleven),
                new Lexicon(props.dirs().work().resolve("none.yaml"), false),
                new TtsCache(props),
                new FakeAudioTool(),
                (provider, chars) -> {});
        PipelineWorker worker = new PipelineWorker(
                repository,
                attempts,
                List.of(fakeRender(), new VoiceStep(tts, repository)),
                new RetryPolicy(List.of(Duration.ZERO)),
                props,
                mock(SystemSelfCheck.class),
                events,
                mapper,
                tm);

        assertThat(worker.runOnce()).isTrue(); // render giả
        assertThat(worker.runOnce()).isTrue(); // voice lỗi ở câu 3 → RETRY
        assertThat(eleven.received).hasSize(2);
        long firstTwo = eleven.received.stream().mapToLong(String::length).sum();
        assertThat(storedChars(id)).isEqualTo(firstTwo); // 2 câu đã gửi (đã tính tiền) không bị mất
        assertThat(api.detail(id).attempts())
                .extracting(JobDetail.Attempt::outcome)
                .contains("RETRY");

        assertThat(worker.runOnce()).isTrue(); // retry: 2 câu đầu lấy cache, chỉ gửi phần còn lại
        assertThat(api.detail(id).state()).isEqualTo(JobState.VOICED);
        long totalSent = eleven.received.stream().mapToLong(String::length).sum();
        assertThat(storedChars(id)).isEqualTo(totalSent); // = tổng ký tự thực gửi, mỗi câu đúng 1 lần
        assertThat(eleven.received).doesNotHaveDuplicates();
    }

    @Test
    void voiceStoresCharsSentToProviderAndDetailHasCost() throws Exception {
        UUID id = newJob();
        TtsServiceTest.StubProvider eleven =
                new TtsServiceTest.StubProvider(TtsProviderName.ELEVENLABS, true, 10_000_000);
        TtsService tts = new TtsService(
                List.of(eleven),
                new Lexicon(props.dirs().work().resolve("none.yaml"), false),
                new TtsCache(props),
                new FakeAudioTool(),
                (provider, chars) -> {});
        PipelineWorker worker = new PipelineWorker(
                repository,
                attempts,
                List.of(fakeRender(), new VoiceStep(tts, repository)),
                RetryPolicy.DEFAULT,
                props,
                mock(SystemSelfCheck.class),
                events,
                mapper,
                tm);

        assertThat(worker.runOnce()).isTrue(); // render giả
        assertThat(worker.runOnce()).isTrue(); // voice

        long sent = eleven.received.stream().mapToLong(String::length).sum();
        assertThat(sent).isPositive();
        Long stored = jdbc.queryForObject("select tts_chars from video_job where id = ?", Long.class, id);
        Long audioMs = jdbc.queryForObject("select audio_ms from video_job where id = ?", Long.class, id);
        assertThat(stored).isEqualTo(sent);
        assertThat(audioMs).isPositive();

        JobDetail detail = api.detail(id);
        assertThat(detail.state()).isEqualTo(JobState.VOICED);
        JobDetail.Cost cost = detail.cost();
        assertThat(cost.ttsProvider()).isEqualTo("ELEVENLABS");
        assertThat(cost.ttsChars()).isEqualTo(sent);
        assertThat(cost.estimatedCredits()).isCloseTo(sent * 1.0, within(0.01));
        assertThat(cost.estimatedUsd()).isCloseTo(sent * 1.0 * 0.0002, within(0.000001));
        assertThat(cost.audioMs()).isEqualTo(audioMs);
        long processing = detail.attempts().stream()
                .mapToLong(a -> a.durationMs() == null ? 0 : a.durationMs())
                .sum();
        assertThat(cost.processingMs()).isEqualTo(processing);
        assertThat(cost.wallMs())
                .isEqualTo(
                        Duration.between(detail.createdAt(), detail.updatedAt()).toMillis());

        JobSummary row = api.list(null, 0, 20).items().getFirst();
        assertThat(row.ttsChars()).isEqualTo(sent);
        assertThat(row.estimatedUsd()).isEqualTo(cost.estimatedUsd());
    }
}
