package com.tuan.docreel.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Snapshot chuỗi lệnh: đổi lệnh có chủ ý thì sửa chuỗi mong đợi. */
class FfmpegCommandBuilderTest {

    private static final String HEAD = "ffmpeg -y -hide_banner -nostdin -loglevel error ";
    private static final String TAIL = " -map [vout] -map [aout] -c:v libx264 -preset medium -crf 20 -r 30"
            + " -c:a aac -b:a 192k -ar 44100 -movflags +faststart out.mp4";

    private final TimelinePlanner planner = new TimelinePlanner();
    private final FfmpegCommandBuilder builder = new FfmpegCommandBuilder();

    private static String zoom(int i, int frames, String step) {
        return "[" + i + ":v]scale=1620:2880,zoompan=z='min(zoom+" + step + ",1.06)'"
                + ":x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)':d=" + frames + ":s=1080x1920:fps=30,setsar=1[v" + i + "]";
    }

    private static String still(int i, int frames) {
        return "[" + i + ":v]scale=1080:1920,zoompan=z=1:d=" + frames + ":s=1080x1920:fps=30,setsar=1[v" + i + "]";
    }

    @Test
    void defaultIsStillSlideWithoutZoom() {
        assertThat(VideoSettings.DEFAULT.zoom()).isFalse();
        Timeline t = planner.plan(List.of(new SceneTiming("a", List.of(5.0)), new SceneTiming("b", List.of(7.0))), 0.3);
        String cmd = String.join(
                " ",
                builder.build(
                        "ffmpeg",
                        t,
                        List.of(Path.of("i0.png"), Path.of("i1.png")),
                        List.of(Path.of("a0.mp3"), Path.of("a1.mp3")),
                        null,
                        Path.of("out.mp4"),
                        VideoSettings.DEFAULT));

        assertThat(cmd)
                .isEqualTo(HEAD + "-i i0.png -i i1.png -i a0.mp3 -i a1.mp3 -filter_complex "
                        + still(0, 159) + ";" + still(1, 210)
                        + ";[v0][v1]xfade=transition=fade:duration=0.3:offset=5[x1]"
                        + ";[x1]format=yuv420p[vout]"
                        + ";[2:a][3:a]concat=n=2:v=0:a=1[aout]" + TAIL)
                .doesNotContain("zoom+");
    }

    private String build(List<SceneTiming> scenes, int files, String srt) {
        Timeline t = planner.plan(scenes, 0.3);
        List<Path> images = new ArrayList<>();
        List<Path> audios = new ArrayList<>();
        for (int i = 0; i < files; i++) {
            images.add(Path.of("i" + i + ".png"));
            audios.add(Path.of("a" + i + ".mp3"));
        }
        return String.join(
                " ", builder.build("ffmpeg", t, images, audios, srt, Path.of("out.mp4"), VideoSettings.ZOOM));
    }

    @Test
    void singleScene() {
        String cmd = build(List.of(new SceneTiming("a", List.of(5.0))), 1, null);

        assertThat(cmd)
                .isEqualTo(HEAD + "-i i0.png -i a0.mp3 -filter_complex " + zoom(0, 150, "0.000400")
                        + ";[v0]format=yuv420p[vout];[1:a]concat=n=1:v=0:a=1[aout]" + TAIL);
    }

    @Test
    void multipleScenes() {
        String cmd = build(
                List.of(
                        new SceneTiming("a", List.of(5.0)),
                        new SceneTiming("b", List.of(7.0)),
                        new SceneTiming("c", List.of(4.0))),
                3,
                null);

        assertThat(cmd)
                .isEqualTo(HEAD + "-i i0.png -i i1.png -i i2.png -i a0.mp3 -i a1.mp3 -i a2.mp3 -filter_complex "
                        + zoom(0, 159, "0.000377") + ";" + zoom(1, 219, "0.000274") + ";" + zoom(2, 120, "0.000500")
                        + ";[v0][v1]xfade=transition=fade:duration=0.3:offset=5[x1]"
                        + ";[x1][v2]xfade=transition=fade:duration=0.3:offset=12[x2]"
                        + ";[x2]format=yuv420p[vout]"
                        + ";[3:a][4:a][5:a]concat=n=3:v=0:a=1[aout]" + TAIL);
    }

    @Test
    void quizWithSubtitles() {
        String cmd = build(List.of(new SceneTiming("q", List.of(3.0, 2.0))), 2, "sub.srt");

        assertThat(cmd)
                .isEqualTo(HEAD + "-i i0.png -i i1.png -i a0.mp3 -i a1.mp3 -filter_complex "
                        + zoom(0, 99, "0.000606") + ";" + zoom(1, 60, "0.001000")
                        + ";[v0][v1]xfade=transition=fade:duration=0.3:offset=3[x1]"
                        + ";[x1]format=yuv420p,subtitles=sub.srt:force_style="
                        + "'FontName=Arial,FontSize=8,Outline=1,Alignment=2,MarginV=45'[vout]"
                        + ";[2:a][3:a]concat=n=2:v=0:a=1[aout]" + TAIL);
    }

    @Test
    void rejectsMismatchedFileCount() {
        Timeline t = planner.plan(List.of(new SceneTiming("a", List.of(5.0))), 0.3);
        List<Path> noImages = List.of();
        List<Path> audio = List.of(Path.of("a.mp3"));
        assertThatThrownBy(() ->
                        builder.build("ffmpeg", t, noImages, audio, null, Path.of("o.mp4"), VideoSettings.DEFAULT))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
