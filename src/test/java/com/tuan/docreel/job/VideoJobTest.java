package com.tuan.docreel.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VideoJobTest {

    @Test
    void newJobIsReceivedWithKey() {
        VideoJob job = JobFixtures.newJob();
        assertThat(job.getState()).isEqualTo(JobState.RECEIVED);
        assertThat(job.getLastSuccessfulStep()).isNull();
        assertThat(job.getIdempotencyKey()).isEqualTo(IdempotencyKey.of("R-08", 1, job.getContentHash()));
    }

    @Test
    void retryAfterFailureKeepsLastSuccessfulStep() {
        VideoJob job = JobFixtures.newJob();
        job.completeStep(JobStep.RENDER);
        job.completeStep(JobStep.VOICE);
        job.fail("ffmpeg lỗi");
        assertThat(job.getErrorMessage()).isEqualTo("ffmpeg lỗi");

        job.retry();

        assertThat(job.getState()).isEqualTo(JobState.RECEIVED);
        assertThat(job.getLastSuccessfulStep()).isEqualTo(JobStep.VOICE);
        assertThat(job.getErrorMessage()).isNull();
    }

    @Test
    void retryOnlyFromFailed() {
        VideoJob job = JobFixtures.newJob();
        assertThatThrownBy(job::retry).isInstanceOf(IllegalStateTransitionException.class);
    }

    @Test
    void uploadRecordsYoutubeId() {
        VideoJob job = JobFixtures.jobIn(JobState.ASSEMBLED);
        job.markUploaded("abc123");
        assertThat(job.getState()).isEqualTo(JobState.UPLOADED);
        assertThat(job.getYoutubeId()).isEqualTo("abc123");
        assertThat(job.getLastSuccessfulStep()).isEqualTo(JobStep.UPLOAD);
    }

    @Test
    void cannotSkipSteps() {
        VideoJob job = JobFixtures.newJob();
        assertThatThrownBy(() -> job.completeStep(JobStep.VOICE)).isInstanceOf(IllegalStateTransitionException.class);
        assertThat(job.getLastSuccessfulStep()).isNull();
    }

    @Test
    void currentStepFollowsLastSuccessfulStep() {
        assertThat(JobFixtures.jobIn(JobState.RECEIVED).currentStep()).isEqualTo(JobStep.RENDER);
        assertThat(JobFixtures.jobIn(JobState.RENDERED).currentStep()).isEqualTo(JobStep.VOICE);
        assertThat(JobFixtures.jobIn(JobState.VOICED).currentStep()).isEqualTo(JobStep.ASSEMBLE);
        assertThat(JobFixtures.jobIn(JobState.ASSEMBLED).currentStep()).isEqualTo(JobStep.UPLOAD);
        assertThat(JobFixtures.jobIn(JobState.UPLOADED).currentStep()).isNull();
        assertThat(JobFixtures.jobIn(JobState.FAILED).currentStep()).isEqualTo(JobStep.RENDER);
    }

    @Test
    void failedAndRetriedJobPointsAtFailedStep() {
        VideoJob job = JobFixtures.newJob();
        job.completeStep(JobStep.RENDER);
        job.completeStep(JobStep.VOICE);
        job.fail("ffmpeg lỗi");
        assertThat(job.currentStep()).isEqualTo(JobStep.ASSEMBLE);
        job.retry();
        assertThat(job.currentStep()).isEqualTo(JobStep.ASSEMBLE);
    }

    @Test
    void stepNext() {
        assertThat(JobStep.RENDER.next()).isEqualTo(JobStep.VOICE);
        assertThat(JobStep.ASSEMBLE.next()).isEqualTo(JobStep.UPLOAD);
        assertThat(JobStep.UPLOAD.next()).isNull();
    }

    @Test
    void attemptStartIsBackdatedByDuration() {
        java.time.Instant before = java.time.Instant.now();
        JobAttempt a = new JobAttempt(java.util.UUID.randomUUID(), JobStep.VOICE, 1, "SUCCESS", 60_000, null);
        java.time.Instant after = java.time.Instant.now();

        assertThat(a.getStartedAt()).isBetween(before.minusMillis(60_000), after.minusMillis(60_000));
        assertThat(a.getFinishedAt()).isEqualTo(a.getStartedAt().plusMillis(60_000));

        JobAttempt noDuration = new JobAttempt(java.util.UUID.randomUUID(), JobStep.VOICE, 1, "FAILED", null, "x");
        assertThat(noDuration.getFinishedAt()).isNull();
        assertThat(noDuration.getStartedAt()).isAfterOrEqualTo(before);
    }
}
