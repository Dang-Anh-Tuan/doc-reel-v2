package com.tuan.docreel.video;

import com.tuan.docreel.common.AppProperties;
import com.tuan.docreel.common.ProcessRequest;
import com.tuan.docreel.common.ProcessRunner;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Ghép PNG + MP3 từng scene thành MP4 dọc. Thời lượng mỗi slide = audio scene (ffprobe). */
@Component
public class VideoAssembler {

    private static final Logger log = LoggerFactory.getLogger(VideoAssembler.class);
    private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration ENCODE_TIMEOUT = Duration.ofMinutes(20);

    private final ProcessRunner runner;
    private final AppProperties props;
    private final TimelinePlanner planner;
    private final FfmpegCommandBuilder builder;

    public VideoAssembler(
            ProcessRunner runner, AppProperties props, TimelinePlanner planner, FfmpegCommandBuilder builder) {
        this.runner = runner;
        this.props = props;
        this.planner = planner;
        this.builder = builder;
    }

    /**
     * @param srt phụ đề cần burn, null = không
     * @return timeline đã dùng (để bước sau đối chiếu mốc scene)
     */
    public Timeline assemble(List<SceneMedia> scenes, Path srt, Path output, VideoSettings settings) {
        List<SceneTiming> timings = new ArrayList<>();
        List<Path> images = new ArrayList<>();
        List<Path> audios = new ArrayList<>();
        for (SceneMedia scene : scenes) {
            List<Double> durations =
                    scene.audios().stream().map(this::probeSeconds).toList();
            timings.add(new SceneTiming(scene.sceneId(), durations));
            images.addAll(scene.images());
            audios.addAll(scene.audios());
        }
        Timeline timeline = planner.plan(timings, settings.transitionSeconds());

        Path absSrt = srt == null ? null : srt.toAbsolutePath();
        List<String> command = builder.build(
                props.tools().ffmpeg(),
                timeline,
                images.stream().map(Path::toAbsolutePath).toList(),
                audios.stream().map(Path::toAbsolutePath).toList(),
                absSrt == null ? null : absSrt.getFileName().toString(),
                output.toAbsolutePath(),
                settings);
        ProcessRequest request = ProcessRequest.of(ENCODE_TIMEOUT, command);
        if (absSrt != null) {
            request = request.withWorkingDir(absSrt.getParent());
        }
        log.info(
                "Ghép video: {} frame, tổng {}s -> {}",
                timeline.frames().size(),
                timeline.totalSeconds(),
                output.getFileName());
        runner.run(request);
        if (sizeOf(output) == 0) {
            throw new IllegalStateException("ffmpeg không tạo ra " + output);
        }
        return timeline;
    }

    public Timeline assemble(List<SceneMedia> scenes, Path srt, Path output) {
        return assemble(scenes, srt, output, VideoSettings.DEFAULT);
    }

    double probeSeconds(Path media) {
        String out = runner.run(ProcessRequest.of(
                        PROBE_TIMEOUT,
                        props.tools().ffprobe(),
                        "-v",
                        "error",
                        "-show_entries",
                        "format=duration",
                        "-of",
                        "default=noprint_wrappers=1:nokey=1",
                        media.toAbsolutePath().toString()))
                .stdout()
                .strip();
        try {
            return Double.parseDouble(out);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("ffprobe không trả thời lượng cho " + media + ": '" + out + "'", e);
        }
    }

    private static long sizeOf(Path p) {
        try {
            return Files.exists(p) ? Files.size(p) : 0;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
