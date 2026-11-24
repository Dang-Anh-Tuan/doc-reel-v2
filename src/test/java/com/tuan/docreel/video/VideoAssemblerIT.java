package com.tuan.docreel.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.tuan.docreel.common.ProcessRequest;
import com.tuan.docreel.common.ProcessRunner;
import com.tuan.docreel.support.TestAppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Cần ffmpeg trong PATH: {@code ./mvnw verify -Pmedia -Dit.test='VideoAssembler*'}. */
@Tag("media")
class VideoAssemblerIT {

    private static final double FRAME = 1.0 / 30;
    private static final Duration TIMEOUT = Duration.ofMinutes(3);
    private static final String[] COLORS = {"red", "green", "blue"};
    private static final double[] SECONDS = {2.0, 3.0, 1.5};

    private static final ProcessRunner RUNNER = new ProcessRunner();

    @TempDir
    static Path dir;

    private static List<SceneMedia> scenes;
    private static VideoAssembler assembler;

    @BeforeAll
    static void generateSamples() {
        assembler = new VideoAssembler(
                RUNNER, TestAppProperties.withDirsUnder(dir), new TimelinePlanner(), new FfmpegCommandBuilder());
        scenes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Path png = dir.resolve("s" + i + ".png");
            Path mp3 = dir.resolve("s" + i + ".mp3");
            run(
                    "ffmpeg",
                    "-y",
                    "-loglevel",
                    "error",
                    "-f",
                    "lavfi",
                    "-i",
                    "color=c=" + COLORS[i] + ":s=1080x1920",
                    "-frames:v",
                    "1",
                    png.toString());
            run(
                    "ffmpeg",
                    "-y",
                    "-loglevel",
                    "error",
                    "-f",
                    "lavfi",
                    "-i",
                    "sine=frequency=" + (330 + i * 110) + ":duration=" + SECONDS[i],
                    "-c:a",
                    "libmp3lame",
                    mp3.toString());
            scenes.add(new SceneMedia("s" + i, List.of(png), List.of(mp3)));
        }
    }

    private static String run(String... cmd) {
        return RUNNER.run(ProcessRequest.of(TIMEOUT, cmd)).stdout().strip();
    }

    private static double videoDuration(Path file) {
        return Double.parseDouble(run(
                "ffprobe",
                "-v",
                "error",
                "-select_streams",
                "v:0",
                "-show_entries",
                "stream=duration",
                "-of",
                "default=noprint_wrappers=1:nokey=1",
                file.toString()));
    }

    @Test
    void producesPortraitVideoWithTotalDuration() {
        Path out = dir.resolve("out.mp4");

        Timeline t = assembler.assemble(scenes, null, out);

        String size = run(
                "ffprobe",
                "-v",
                "error",
                "-select_streams",
                "v:0",
                "-show_entries",
                "stream=width,height",
                "-of",
                "csv=p=0:s=x",
                out.toString());
        assertThat(size).isEqualTo("1080x1920");
        assertThat(videoDuration(out)).isCloseTo(t.totalSeconds(), within(FRAME));
    }

    @Test
    void sceneChangeMatchesXfadeOffset() throws IOException {
        Path out = dir.resolve("marks.mp4");
        Path raw = dir.resolve("marks.raw");
        Timeline t = assembler.assemble(scenes, null, out);
        run(
                "ffmpeg",
                "-y",
                "-loglevel",
                "error",
                "-i",
                out.toString(),
                "-vf",
                "scale=1:1",
                "-pix_fmt",
                "rgb24",
                "-f",
                "rawvideo",
                raw.toString());
        byte[] px = Files.readAllBytes(raw);

        for (int k = 1; k < 3; k++) {
            int firstFrame = -1;
            for (int f = 0; f * 3 + 2 < px.length; f++) {
                int r = px[f * 3] & 0xff;
                int g = px[f * 3 + 1] & 0xff;
                int b = px[f * 3 + 2] & 0xff;
                int dominant = r >= g && r >= b ? 0 : g >= b ? 1 : 2;
                if (dominant == k) {
                    firstFrame = f;
                    break;
                }
            }
            // giữa xfade (hai màu bằng nhau) = offset + T/2
            double expected = t.frames().get(k).xfadeOffset() + t.transitionSeconds() / 2;
            assertThat(firstFrame).isGreaterThanOrEqualTo(0);
            assertThat(firstFrame * FRAME).as("mốc đổi cảnh %d", k).isCloseTo(expected, within(FRAME * 1.5));
        }
    }

    @Test
    void burnsSubtitlesWithoutBreakingOutput() throws IOException {
        Path srt = dir.resolve("sub.srt");
        Files.writeString(srt, "1\n00:00:00,500 --> 00:00:01,500\nXin chào DocReel\n");
        Path out = dir.resolve("sub.mp4");

        Timeline t = assembler.assemble(scenes, srt, out);

        assertThat(videoDuration(out)).isCloseTo(t.totalSeconds(), within(FRAME));
    }
}
