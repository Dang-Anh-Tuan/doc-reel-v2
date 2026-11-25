package com.tuan.docreel.youtube;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tuan.docreel.job.JobState;
import com.tuan.docreel.pipeline.StepContext;
import com.tuan.docreel.pipeline.StepResult;
import com.tuan.docreel.script.TitleScene;
import com.tuan.docreel.script.VideoScript;
import com.tuan.docreel.script.VideoType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ApplicationEventPublisher;

class YouTubeUploadStepTest {

    @TempDir
    Path work;

    YouTubeUploader uploader = mock(YouTubeUploader.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    YouTubeUploadStep step = new YouTubeUploadStep(uploader, events);
    VideoScript script = new VideoScript(
            "J-02",
            "Java",
            "Tiêu đề",
            1,
            VideoType.SLIDES,
            null,
            List.of(),
            List.of(new TitleScene("s01", "Mở đầu.", null, "chip", "JPA", " là gì", "hook")),
            null);

    private StepContext ctx() {
        return new StepContext(UUID.randomUUID(), JobState.ASSEMBLED, script, work, 1);
    }

    @Test
    void uploadsWritesIdThenAddsToPlaylist() throws Exception {
        when(uploader.upload(any(), any())).thenReturn("vid1");

        StepResult result = step.execute(ctx());

        assertThat(result.nextState()).isEqualTo(JobState.UPLOADED);
        assertThat(result.skipped()).isFalse();
        assertThat(Files.readString(work.resolve(YouTubeUploadStep.ID_FILE))).isEqualTo("vid1");
        verify(uploader).addToPlaylist("Java", "vid1");
    }

    @Test
    void playlistFailureAfterUploadKeepsIdSoRetryDoesNotUploadAgain() throws Exception {
        when(uploader.upload(any(), any())).thenReturn("vid1");
        when(uploader.addToPlaylist("Java", "vid1")).thenThrow(new YouTubeApiException(409, "YouTube trả HTTP 409"));

        assertThatThrownBy(() -> step.execute(ctx())).isInstanceOf(YouTubeApiException.class);
        assertThat(work.resolve(YouTubeUploadStep.ID_FILE)).exists();
    }

    @Test
    void retryWithExistingIdSkipsUploadButStillAddsToPlaylist() throws Exception {
        Files.writeString(work.resolve(YouTubeUploadStep.ID_FILE), "vid1\n");

        StepResult result = step.execute(ctx());

        assertThat(result.nextState()).isEqualTo(JobState.UPLOADED);
        assertThat(result.skipped()).isTrue();
        verify(uploader, never()).upload(any(), any());
        verify(uploader).addToPlaylist("Java", "vid1");
    }
}
