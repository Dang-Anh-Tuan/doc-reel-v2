package com.tuan.docreel.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import org.mockito.ArgumentCaptor;

class AssembleStepTest {

    @TempDir
    Path work;

    VideoAssembler assembler = mock(VideoAssembler.class);
    AssembleStep step = new AssembleStep(assembler);
    VideoScript script;

    @BeforeEach
    void setUp() throws Exception {
        var title = new TitleScene("s01", "Mở đầu.", null, "chip", "JPA", " là gì", "hook");
        var quiz = new QuizScene("s02", "Câu hỏi.", null, "q?", List.of("a", "b"), 0, "e", "Đáp án.");
        script = new VideoScript(
                "t1", "java", "Tiêu đề", 1, VideoType.SLIDES, null, List.of(), List.of(title, quiz), null);
        for (String f : List.of("s01.png", "s01.mp3", "s02.png", "s02.mp3", "s02-reveal.png", "s02-reveal.mp3")) {
            Files.write(work.resolve(f), new byte[] {1});
        }
    }

    private StepContext ctx() {
        return new StepContext(UUID.randomUUID(), JobState.VOICED, script, work, 1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void quizGetsTwoImagesAndTwoAudiosAndOutputIsRenamedAfterEncoding() throws Exception {
        Files.writeString(work.resolve("subtitles.srt"), "1\n");
        doAnswer(inv -> {
                    Files.write(inv.<Path>getArgument(2), new byte[] {7});
                    return null;
                })
                .when(assembler)
                .assemble(any(), any(), any());

        var result = step.execute(ctx());

        assertThat(result.nextState()).isEqualTo(JobState.ASSEMBLED);
        assertThat(work.resolve("video.mp4")).exists();
        assertThat(work.resolve("video.part.mp4")).doesNotExist();
        ArgumentCaptor<List<SceneMedia>> media = ArgumentCaptor.forClass(List.class);
        verify(assembler).assemble(media.capture(), eq(work.resolve("subtitles.srt")), any());
        assertThat(media.getValue()).hasSize(2);
        SceneMedia quiz = media.getValue().get(1);
        assertThat(quiz.images()).containsExactly(work.resolve("s02.png"), work.resolve("s02-reveal.png"));
        assertThat(quiz.audios()).containsExactly(work.resolve("s02.mp3"), work.resolve("s02-reveal.mp3"));
    }

    @Test
    void skipsWhenVideoAlreadyExists() throws Exception {
        Files.write(work.resolve("video.mp4"), new byte[] {1});

        assertThat(step.execute(ctx()).skipped()).isTrue();
        verify(assembler, never()).assemble(any(), any(), any());
    }

    @Test
    void missingAudioIsError() throws Exception {
        Files.delete(work.resolve("s02-reveal.mp3"));

        assertThatThrownBy(() -> step.execute(ctx()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("s02-reveal.mp3");
    }
}
