package com.tuan.docreel.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.tuan.docreel.job.JobAttempt;
import com.tuan.docreel.job.JobState;
import com.tuan.docreel.job.JobStep;
import com.tuan.docreel.voice.TtsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class PipelineWorkerIT extends PipelineTestBase {

    @Test
    void twoWorkersTenJobsEachStepRunsExactlyOncePerJob() throws Exception {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ids.add(newJob("Video " + i));
        }
        List<FakeStep> steps = fakeSteps();
        PipelineWorker w1 = worker(RetryPolicy.DEFAULT, arr(steps));
        PipelineWorker w2 = worker(RetryPolicy.DEFAULT, arr(steps));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Integer> f1 = pool.submit(() -> drain(w1));
        Future<Integer> f2 = pool.submit(() -> drain(w2));
        int total = f1.get(60, TimeUnit.SECONDS) + f2.get(60, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(total).isEqualTo(40); // 10 job x 4 bước, không thừa không thiếu
        for (UUID id : ids) {
            assertThat(job(id).getState()).isEqualTo(JobState.UPLOADED);
            assertThat(job(id).getYoutubeId()).isEqualTo(youtubeIdOf(id));
            for (FakeStep s : steps) {
                assertThat(s.runs(id)).as("bước %s của %s", s.supported, id).isEqualTo(1);
            }
        }
    }

    @Test
    void failedStepResumesFromThatStepAfterManualRetry() {
        UUID id = newJob("Video retry tay");
        List<FakeStep> steps = fakeSteps();
        FakeStep voice = steps.get(1);
        voice.behaviour = ctx -> {
            if (voice.runs(ctx.jobId()) == 1) {
                throw new TtsException("ElevenLabs 401"); // lỗi không retry tự động
            }
        };
        PipelineWorker worker = worker(RetryPolicy.DEFAULT, arr(steps));

        drain(worker);

        var failed = job(id);
        assertThat(failed.getState()).isEqualTo(JobState.FAILED);
        assertThat(failed.getLastSuccessfulStep()).isEqualTo(JobStep.RENDER);
        assertThat(failed.getErrorMessage()).contains("401");

        template(tm).executeWithoutResult(s -> {
            var j = repository.findById(id).orElseThrow();
            j.retry();
            repository.save(j);
        });
        drain(worker);

        assertThat(job(id).getState()).isEqualTo(JobState.UPLOADED);
        assertThat(steps.get(0).runs(id)).as("render không chạy lại").isEqualTo(1);
        assertThat(voice.runs(id)).isEqualTo(2);
        assertThat(steps.get(2).runs(id)).isEqualTo(1);
        assertThat(steps.get(3).runs(id)).isEqualTo(1);
    }

    @Test
    void selfCheckCriticalMeansWorkerClaimsNothing() {
        UUID id = newJob("Video bị tạm dừng");
        List<FakeStep> steps = fakeSteps();
        when(selfCheck.hasCriticalFailure()).thenReturn(true);
        PipelineWorker worker = worker(RetryPolicy.DEFAULT, arr(steps));

        assertThat(worker.runOnce()).isFalse();

        assertThat(job(id).getState()).isEqualTo(JobState.RECEIVED);
        assertThat(steps.stream().mapToInt(FakeStep::totalRuns).sum()).isZero();

        when(selfCheck.hasCriticalFailure()).thenReturn(false);
        assertThat(worker.runOnce()).isTrue();
    }

    @Test
    void providerFromStepIsSavedOnJob() {
        UUID id = newJob("Video fallback");
        List<FakeStep> steps = fakeSteps();
        steps.get(1).ttsProvider = "EDGE";

        drain(worker(RetryPolicy.DEFAULT, arr(steps)));

        assertThat(job(id).getTtsProvider()).isEqualTo("EDGE");
    }

    @Test
    void uploadedJobKeepsYoutubeIdAndInputMovesToDone() throws Exception {
        UUID id = newJob("Video có file input");
        Path jobDir = Files.createDirectories(props.dirs().work().resolve(id.toString()));
        Files.writeString(jobDir.resolve("input.json"), "{}");

        drain(worker(RetryPolicy.DEFAULT, arr(fakeSteps())));

        assertThat(job(id).getYoutubeId()).isEqualTo(youtubeIdOf(id));
        assertThat(jobDir.resolve("input.json")).doesNotExist();
        assertThat(props.dirs().done().resolve(id + "-input.json")).exists();
    }

    @Test
    void everyStepWritesAnAttemptRow() {
        UUID id = newJob("Video attempt");
        List<FakeStep> steps = fakeSteps();

        drain(worker(RetryPolicy.DEFAULT, arr(steps)));

        List<JobAttempt> rows = attempts.findByJobIdOrderByStartedAt(id);
        assertThat(rows).extracting(JobAttempt::getStep).containsExactly("RENDER", "VOICE", "ASSEMBLE", "UPLOAD");
        assertThat(rows).extracting(JobAttempt::getOutcome).containsOnly("SUCCESS");
        assertThat(rows).extracting(JobAttempt::getAttemptNo).containsOnly(1);
    }

    @Test
    void skippedStepIsRecordedAsSkipped() {
        UUID id = newJob("Video bỏ qua");
        Step render = new Step() {
            @Override
            public boolean supports(JobState state) {
                return state == JobState.RECEIVED;
            }

            @Override
            public StepResult execute(StepContext context) {
                return new StepResult(JobState.RENDERED, true);
            }
        };
        List<FakeStep> rest = fakeSteps().subList(1, 4);
        List<Step> all = new ArrayList<>(List.of(render));
        all.addAll(rest);

        drain(worker(RetryPolicy.DEFAULT, all.toArray(new Step[0])));

        assertThat(attempts.findByJobIdOrderByStartedAt(id).getFirst().getOutcome())
                .isEqualTo("SKIPPED");
        assertThat(job(id).getState()).isEqualTo(JobState.UPLOADED);
    }

    /** Step tự xóa "jobId" khỏi MDC (như RenderStep...) → log lỗi của worker sau bước vẫn phải có jobId. */
    @Test
    void errorLogAfterStepKeepsJobIdEvenIfStepClearsMdc() {
        UUID id = newJob("Video MDC");
        List<FakeStep> steps = fakeSteps();
        steps.getFirst().behaviour = ctx -> {
            org.slf4j.MDC.remove("jobId");
            throw new TtsException("lỗi nội dung");
        };
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(PipelineWorker.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>() {
            @Override
            protected void append(ch.qos.logback.classic.spi.ILoggingEvent event) {
                event.prepareForDeferredProcessing(); // chụp MDC lúc ghi
                super.append(event);
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            drain(worker(RetryPolicy.DEFAULT, arr(steps)));
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(job(id).getState()).isEqualTo(JobState.FAILED);
        assertThat(appender.list)
                .filteredOn(e -> e.getLevel() == ch.qos.logback.classic.Level.ERROR)
                .isNotEmpty()
                .allSatisfy(e -> assertThat(e.getMDCPropertyMap()).containsEntry("jobId", id.toString()));
    }
}
