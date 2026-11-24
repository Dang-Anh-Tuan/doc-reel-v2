package com.tuan.docreel.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tuan.docreel.script.Origin;
import com.tuan.docreel.script.VideoScript;
import com.tuan.docreel.settings.TtsSettings;
import com.tuan.docreel.settings.TtsSettingsProvider;
import com.tuan.docreel.support.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, JobServiceIT.MutableTts.class})
class JobServiceIT {

    static final AtomicReference<TtsSettings> TTS =
            new AtomicReference<>(new TtsSettings("ELEVENLABS", "{\"voice\":\"A\"}"));

    @TestConfiguration(proxyBeanMethods = false)
    static class MutableTts {
        @Bean
        @Primary
        TtsSettingsProvider mutableTtsSettings() {
            return TTS::get;
        }
    }

    @Autowired
    JobService service;

    @Autowired
    JobRepository repository;

    @Autowired
    JobAttemptRepository attempts;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PlatformTransactionManager txManager;

    @BeforeEach
    void clean() {
        jdbc.update("delete from job_attempt");
        jdbc.update("delete from video_job");
        TTS.set(new TtsSettings("ELEVENLABS", "{\"voice\":\"A\"}"));
    }

    @Test
    void secondSubmitOfSameContentIsDuplicate() {
        VideoScript script = JobFixtures.sampleScript();

        SubmitResult first = service.submit(script, new Origin("telegram", "42", "7"));
        SubmitResult second = service.submit(script, new Origin("zalo", "9", "9"));

        assertThat(first.status()).isEqualTo(SubmitResult.Status.CREATED);
        assertThat(second.status()).isEqualTo(SubmitResult.Status.DUPLICATE);
        assertThat(second.jobId()).isEqualTo(first.jobId());
        assertThat(repository.count()).isEqualTo(1);
        VideoJob job = repository.findById(first.jobId()).orElseThrow();
        assertThat(job.getState()).isEqualTo(JobState.RECEIVED);
        assertThat(job.getSourceChannel()).isEqualTo("telegram");
        assertThat(job.getSourceRef()).isEqualTo("42/7");
        assertThat(job.getScript()).contains(script.topicId());
    }

    @Test
    void concurrentSubmitsCreateExactlyOne() throws Exception {
        VideoScript script = JobFixtures.sampleScript();
        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<SubmitResult>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return service.submit(script, null);
                }));
            }
            ready.await();
            go.countDown();
            List<SubmitResult> results = new ArrayList<>();
            for (Future<SubmitResult> f : futures) {
                results.add(f.get());
            }
            assertThat(results).filteredOn(SubmitResult::created).hasSize(1);
            assertThat(results).filteredOn(r -> !r.created()).hasSize(threads - 1);
            assertThat(results.stream().map(SubmitResult::jobId).distinct()).hasSize(1);
            assertThat(repository.count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void differentContentCreatesNewJob() {
        VideoScript script = JobFixtures.sampleScript();
        service.submit(script, null);
        SubmitResult other = service.submit(JobFixtures.withTitle(script, script.title() + " v2"), null);
        assertThat(other.created()).isTrue();
        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findById(other.jobId()).orElseThrow().getSourceChannel())
                .isEqualTo("api");
    }

    @Test
    void editingSameJobFromTwoTransactionsFailsOptimistically() {
        UUIDHolder id =
                new UUIDHolder(service.submit(JobFixtures.sampleScript(), null).jobId());
        VideoJob a = repository.findById(id.id).orElseThrow();
        VideoJob b = repository.findById(id.id).orElseThrow();

        a.completeStep(JobStep.RENDER);
        repository.saveAndFlush(a);
        b.fail("song song");

        assertThatThrownBy(() -> repository.saveAndFlush(b)).isInstanceOf(OptimisticLockingFailureException.class);
        VideoJob stored = repository.findById(id.id).orElseThrow();
        assertThat(stored.getState()).isEqualTo(JobState.RENDERED);
        assertThat(stored.getVersion()).isEqualTo(1);
    }

    @Test
    void ttsSettingsAreSnapshotAtCreation() {
        UUIDHolder id =
                new UUIDHolder(service.submit(JobFixtures.sampleScript(), null).jobId());
        TTS.set(new TtsSettings("EDGE", "{\"voice\":\"B\"}"));

        VideoJob later = repository.findById(id.id).orElseThrow();
        assertThat(later.getTtsProvider()).isEqualTo("ELEVENLABS");
        assertThat(jdbc.queryForObject(
                        "select tts_settings ->> 'voice' from video_job where id = ?", String.class, id.id))
                .isEqualTo("A");

        VideoScript script = JobFixtures.sampleScript();
        SubmitResult second = service.submit(JobFixtures.withTitle(script, "khác"), null);
        assertThat(repository.findById(second.jobId()).orElseThrow().getTtsProvider())
                .isEqualTo("EDGE");
    }

    @Test
    void retryPersistsAndKeepsLastSuccessfulStep() {
        UUIDHolder id =
                new UUIDHolder(service.submit(JobFixtures.sampleScript(), null).jobId());
        new TransactionTemplate(txManager).executeWithoutResult(s -> {
            VideoJob j = repository.findById(id.id).orElseThrow();
            j.completeStep(JobStep.RENDER);
            j.fail("lỗi TTS");
        });
        new TransactionTemplate(txManager)
                .executeWithoutResult(
                        s -> repository.findById(id.id).orElseThrow().retry());

        VideoJob j = repository.findById(id.id).orElseThrow();
        assertThat(j.getState()).isEqualTo(JobState.RECEIVED);
        assertThat(j.getLastSuccessfulStep()).isEqualTo(JobStep.RENDER);
        assertThat(j.getErrorMessage()).isNull();
    }

    @Test
    void attemptsAreStoredPerJob() {
        UUIDHolder id =
                new UUIDHolder(service.submit(JobFixtures.sampleScript(), null).jobId());
        attempts.save(new JobAttempt(id.id, JobStep.RENDER, 1, "FAILED", 120, "tràn chữ"));
        attempts.save(new JobAttempt(id.id, JobStep.RENDER, 2, "OK", 90, null));

        List<JobAttempt> list = attempts.findByJobIdOrderByStartedAt(id.id);
        assertThat(list).extracting(JobAttempt::getAttemptNo).containsExactly(1, 2);
        assertThat(list.get(0).getError()).isEqualTo("tràn chữ");
        assertThat(list.get(0).getStep()).isEqualTo("RENDER");
    }

    private record UUIDHolder(java.util.UUID id) {}
}
