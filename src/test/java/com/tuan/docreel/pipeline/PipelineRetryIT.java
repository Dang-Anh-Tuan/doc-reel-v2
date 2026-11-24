package com.tuan.docreel.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.job.JobAttempt;
import com.tuan.docreel.job.JobState;
import com.tuan.docreel.render.SlideOverflowException;
import com.tuan.docreel.voice.TtsTransientException;
import com.tuan.docreel.voice.TtsUnavailableException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PipelineRetryIT extends PipelineTestBase {

    private static final RetryPolicy FAST = new RetryPolicy(List.of(Duration.ofMillis(30), Duration.ofMillis(30)));

    /** Chạy tới khi job vào trạng thái cuối (UPLOADED / FAILED), chờ qua các khoảng backoff ngắn. */
    private void runUntilSettled(PipelineWorker worker, UUID id) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            worker.runOnce();
            JobState s = job(id).getState();
            if (s == JobState.UPLOADED || s == JobState.FAILED) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("Job không kết thúc: " + job(id).getState());
    }

    @Test
    void transientErrorRetriesThenSucceeds() throws Exception {
        UUID id = newJob("Video lỗi mạng");
        List<FakeStep> steps = fakeSteps();
        FakeStep voice = steps.get(1);
        voice.behaviour = ctx -> {
            if (voice.runs(ctx.jobId()) <= 2) {
                throw new TtsTransientException("ElevenLabs 503");
            }
        };

        runUntilSettled(worker(FAST, arr(steps)), id);

        assertThat(job(id).getState()).isEqualTo(JobState.UPLOADED);
        assertThat(voice.runs(id)).isEqualTo(3);
        List<JobAttempt> rows = attempts.findByJobIdOrderByStartedAt(id).stream()
                .filter(a -> a.getStep().equals("VOICE"))
                .toList();
        assertThat(rows).extracting(JobAttempt::getOutcome).containsExactly("RETRY", "RETRY", "SUCCESS");
        assertThat(rows).extracting(JobAttempt::getAttemptNo).containsExactly(1, 2, 3);
        assertThat(rows.getFirst().getError()).contains("503");
    }

    @Test
    void ttsUnavailableFailsAfterRetriesAreExhausted() throws Exception {
        UUID id = newJob("Video edge-tts hỏng");
        List<FakeStep> steps = fakeSteps();
        steps.get(1).behaviour = ctx -> {
            throw new TtsUnavailableException("edge-tts thoát 1");
        };

        runUntilSettled(worker(FAST, arr(steps)), id);

        var failed = job(id);
        assertThat(failed.getState()).isEqualTo(JobState.FAILED);
        assertThat(failed.getErrorMessage()).startsWith("TTS_UNAVAILABLE");
        assertThat(steps.get(1).runs(id)).isEqualTo(3); // 1 lần đầu + 2 retry
        assertThat(attempts.findByJobIdOrderByStartedAt(id).stream()
                        .filter(a -> a.getStep().equals("VOICE"))
                        .map(JobAttempt::getOutcome))
                .containsExactly("RETRY", "RETRY", "FAILED");
    }

    @Test
    void slideOverflowFailsAtOnceWithoutRetry() throws Exception {
        UUID id = newJob("Video tràn chữ");
        List<FakeStep> steps = fakeSteps();
        steps.get(0).behaviour = ctx -> {
            throw new SlideOverflowException("s01", List.of("title"));
        };

        runUntilSettled(worker(FAST, arr(steps)), id);

        assertThat(job(id).getState()).isEqualTo(JobState.FAILED);
        assertThat(steps.get(0).runs(id)).isEqualTo(1);
        assertThat(attempts.findByJobIdOrderByStartedAt(id)).hasSize(1);
    }

    @Test
    void jobWaitsForBackoffBeforeRunningAgain() {
        UUID id = newJob("Video chờ backoff");
        List<FakeStep> steps = fakeSteps();
        steps.get(0).behaviour = ctx -> {
            throw new TtsTransientException("503");
        };
        PipelineWorker worker = worker(RetryPolicy.DEFAULT, arr(steps)); // 30s

        assertThat(worker.runOnce()).isTrue(); // chạy, lỗi, lên lịch retry
        assertThat(worker.runOnce()).isFalse(); // còn trong thời gian chờ → không nhặt lại

        assertThat(steps.get(0).runs(id)).isEqualTo(1);
        assertThat(job(id).getState()).isEqualTo(JobState.RECEIVED);
    }
}
