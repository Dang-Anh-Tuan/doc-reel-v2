package com.tuan.docreel.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tuan.docreel.job.JobState;
import com.tuan.docreel.pipeline.StepContext;
import com.tuan.docreel.script.QuizScene;
import com.tuan.docreel.script.TitleScene;
import com.tuan.docreel.script.VideoScript;
import com.tuan.docreel.script.VideoType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RenderStepTest {

    @TempDir
    Path work;

    SlideRenderer renderer = mock(SlideRenderer.class);
    SlideCapturer capturer = mock(SlideCapturer.class);
    RenderStep step = new RenderStep(renderer, capturer);
    VideoScript script;

    @BeforeEach
    void setUp() {
        var title = new TitleScene("s01", "Mở đầu.", null, "chip", "JPA", " là gì", "hook");
        var quiz = new QuizScene("s02", "Câu hỏi.", null, "q?", List.of("a", "b"), 0, "e", "Đáp án.");
        script = new VideoScript(
                "t1", "java", "Tiêu đề", 1, VideoType.SLIDES, null, List.of(), List.of(title, quiz), null);
        when(renderer.render(
                        any(),
                        any(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        any()))
                .thenReturn("<html/>");
    }

    private StepContext ctx() {
        return new StepContext(UUID.randomUUID(), JobState.RECEIVED, script, work, 1);
    }

    @Test
    void writesOnePngPerSceneAndTwoForQuiz() throws Exception {
        when(capturer.capture(anyString(), anyString()))
                .thenReturn(new SlideCapturer.Capture(List.of(new byte[] {1}), List.of()))
                .thenReturn(new SlideCapturer.Capture(List.of(new byte[] {2}, new byte[] {3}), List.of()));

        var result = step.execute(ctx());

        assertThat(result.nextState()).isEqualTo(JobState.RENDERED);
        assertThat(result.skipped()).isFalse();
        assertThat(work.resolve("s01.png")).exists();
        assertThat(Files.readAllBytes(work.resolve("s02.png"))).containsExactly(2);
        assertThat(Files.readAllBytes(work.resolve("s02-reveal.png"))).containsExactly(3);
    }

    @Test
    void skipsWhenAllPngsAlreadyExist() throws Exception {
        for (String name : List.of("s01.png", "s02.png", "s02-reveal.png")) {
            Files.write(work.resolve(name), new byte[] {9});
        }

        var result = step.execute(ctx());

        assertThat(result.skipped()).isTrue();
        verify(capturer, never()).capture(anyString(), anyString());
    }

    @Test
    void emptyPngIsNotValidOutput() throws Exception {
        for (String name : List.of("s01.png", "s02.png", "s02-reveal.png")) {
            Files.write(work.resolve(name), new byte[0]);
        }
        when(capturer.capture(anyString(), anyString()))
                .thenReturn(new SlideCapturer.Capture(List.of(new byte[] {1}), List.of()))
                .thenReturn(new SlideCapturer.Capture(List.of(new byte[] {2}, new byte[] {3}), List.of()));

        assertThat(step.execute(ctx()).skipped()).isFalse();
    }

    @Test
    void overflowPropagatesAsNonRetryable() {
        when(capturer.capture(anyString(), anyString())).thenThrow(new SlideOverflowException("s01", List.of("title")));

        assertThatThrownBy(() -> step.execute(ctx())).isInstanceOf(SlideOverflowException.class);
    }

    @Test
    void wrongFrameCountIsError() {
        when(capturer.capture(anyString(), anyString()))
                .thenReturn(new SlideCapturer.Capture(List.of(new byte[] {1}), List.of()))
                .thenReturn(new SlideCapturer.Capture(List.of(new byte[] {2}), List.of()));

        assertThatThrownBy(() -> step.execute(ctx()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("s02");
    }
}
