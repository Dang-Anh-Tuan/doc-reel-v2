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
}
