package com.tuan.docreel.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tuan.docreel.common.ProcessRequest;
import com.tuan.docreel.common.ProcessResult;
import com.tuan.docreel.common.ProcessRunner;
import com.tuan.docreel.support.TestAppProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class VideoAssemblerTest {

    @TempDir
    Path dir;

    private VideoAssembler assembler(ProcessRunner runner) {
        return new VideoAssembler(
                runner, TestAppProperties.withDirsUnder(dir), new TimelinePlanner(), new FfmpegCommandBuilder());
    }

    private static ProcessResult ok(String out) {
        return new ProcessResult(0, out, "", Duration.ZERO);
    }

    private SceneMedia scene() {
        return new SceneMedia("a", List.of(dir.resolve("a.png")), List.of(dir.resolve("a.mp3")));
    }

    @Test
    void probesAudioThenRunsFfmpegInSrtDirectory() throws Exception {
        Path out = dir.resolve("out.mp4");
        Path srt = Files.writeString(dir.resolve("sub.srt"), "x");
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any())).thenAnswer(inv -> {
            ProcessRequest r = inv.getArgument(0);
            if (r.program().equals("ffprobe")) {
                return ok("5.0\n");
            }
            Files.writeString(out, "mp4");
            return ok("");
        });

        Timeline t = assembler(runner).assemble(List.of(scene()), srt, out);

        assertThat(t.totalSeconds()).isEqualTo(5.0);
        ArgumentCaptor<ProcessRequest> captor = ArgumentCaptor.forClass(ProcessRequest.class);
        verify(runner, times(2)).run(captor.capture());
        ProcessRequest ffmpeg = captor.getAllValues().get(1);
        assertThat(ffmpeg.program()).isEqualTo("ffmpeg");
        assertThat(ffmpeg.workingDir()).isEqualTo(dir.toAbsolutePath());
        assertThat(String.join(" ", ffmpeg.command())).contains("subtitles=sub.srt");
    }

    @Test
    void failsWhenProbeOutputIsNotANumber() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any())).thenReturn(ok("N/A"));
        List<SceneMedia> scenes = List.of(scene());
        Path out = dir.resolve("o.mp4");

        assertThatThrownBy(() -> assembler(runner).assemble(scenes, null, out))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ffprobe");
    }

    @Test
    void failsWhenOutputMissing() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any())).thenReturn(ok("5.0"));
        List<SceneMedia> scenes = List.of(scene());
        Path out = dir.resolve("o.mp4");

        assertThatThrownBy(() -> assembler(runner).assemble(scenes, null, out))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("không tạo ra");
    }

    @Test
    void sceneMediaRequiresMatchingCounts() {
        List<Path> images = List.of(dir);
        List<Path> none = List.of();
        assertThatThrownBy(() -> new SceneMedia("a", images, none)).isInstanceOf(IllegalArgumentException.class);
    }
}
