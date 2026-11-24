package com.tuan.docreel.voice;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.common.AppProperties;
import com.tuan.docreel.common.ProcessRequest;
import com.tuan.docreel.common.ProcessRunner;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Cần ffmpeg/ffprobe thật: nối 2 câu + đệm 0,2s đầu / 0,4s cuối. */
@Tag("media")
class FfmpegAudioToolMediaTest {

    @TempDir
    Path dir;

    private Path sine(ProcessRunner runner, String name, double seconds) {
        Path out = dir.resolve(name);
        runner.run(ProcessRequest.of(
                Duration.ofSeconds(30),
                "ffmpeg",
                "-y",
                "-v",
                "error",
                "-f",
                "lavfi",
                "-i",
                "sine=frequency=440:duration=" + seconds,
                "-c:a",
                "libmp3lame",
                out.toString()));
        return out;
    }

    @Test
    void concatPadsSilenceAndProbeMatches() {
        ProcessRunner runner = new ProcessRunner();
        AppProperties props = VoiceFixtures.props(dir, URI.create("http://localhost:1"), "", "edge-tts", 0);
        FfmpegAudioTool tool = new FfmpegAudioTool(runner, props);
        Path a = sine(runner, "a.mp3", 1.0);
        Path b = sine(runner, "b.mp3", 2.0);

        Path out = dir.resolve("scene.mp3");
        tool.concat(List.of(a, b), out, 0.2, 0.4);

        assertThat(Files.exists(out)).isTrue();
        assertThat(tool.durationSeconds(out)).isBetween(3.5, 3.8); // 1 + 2 + 0.2 + 0.4 ± padding của MP3
        assertThat(tool.durationSeconds(a)).isBetween(0.95, 1.1);
    }
}
