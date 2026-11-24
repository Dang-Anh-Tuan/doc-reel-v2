package com.tuan.docreel.voice;

import com.tuan.docreel.common.AppProperties;
import com.tuan.docreel.common.ProcessRequest;
import com.tuan.docreel.common.ProcessRunner;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class FfmpegAudioTool implements AudioTool {

    private static final Duration TIMEOUT = Duration.ofMinutes(2);

    private final ProcessRunner runner;
    private final AppProperties props;

    public FfmpegAudioTool(ProcessRunner runner, AppProperties props) {
        this.runner = runner;
        this.props = props;
    }

    @Override
    public double durationSeconds(Path audio) {
        String out = runner.run(ProcessRequest.of(
                        TIMEOUT,
                        props.tools().ffprobe(),
                        "-v",
                        "error",
                        "-show_entries",
                        "format=duration",
                        "-of",
                        "csv=p=0",
                        audio.toAbsolutePath().toString()))
                .stdout()
                .strip();
        try {
            return Double.parseDouble(out);
        } catch (NumberFormatException e) {
            throw new TtsException("ffprobe không trả thời lượng cho " + audio + ": '" + out + "'", e);
        }
    }

    @Override
    public void concat(List<Path> parts, Path output, double leadSeconds, double tailSeconds) {
        List<String> cmd = new ArrayList<>(List.of(props.tools().ffmpeg(), "-y", "-v", "error"));
        StringBuilder filter = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            cmd.add("-i");
            cmd.add(parts.get(i).toAbsolutePath().toString());
            filter.append(
                    String.format("[%d:a]aresample=44100,aformat=sample_fmts=fltp:channel_layouts=mono[a%d];", i, i));
        }
        for (int i = 0; i < parts.size(); i++) {
            filter.append("[a").append(i).append(']');
        }
        filter.append(String.format(
                Locale.ROOT,
                "concat=n=%d:v=0:a=1[c];[c]adelay=%d:all=1,apad=pad_dur=%.3f[o]",
                parts.size(),
                Math.round(leadSeconds * 1000),
                tailSeconds));
        cmd.addAll(List.of(
                "-filter_complex",
                filter.toString(),
                "-map",
                "[o]",
                "-c:a",
                "libmp3lame",
                "-q:a",
                "4",
                output.toAbsolutePath().toString()));
        runner.run(ProcessRequest.of(TIMEOUT, cmd));
    }
}
