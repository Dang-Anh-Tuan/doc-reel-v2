package com.tuan.docreel.system;

import com.tuan.docreel.common.AppProperties;
import com.tuan.docreel.common.ProcessRequest;
import com.tuan.docreel.common.ProcessRunner;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Mục "ffmpeg" (CRITICAL): {@code ffmpeg -version} và {@code ffprobe -version} chạy được, bản chính >= 5. */
@Component
@Order(30)
public class FfmpegCheck implements SelfCheckItem {

    static final String NAME = "ffmpeg";
    static final int MIN_MAJOR = 5;
    private static final Pattern VERSION = Pattern.compile("version\\s+[nN]?(\\d+)");
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String FIX =
            "Cài ffmpeg (winget install Gyan.FFmpeg) hoặc đặt FFMPEG_PATH / FFPROBE_PATH tới bản >= " + MIN_MAJOR;

    private final ProcessRunner runner;
    private final AppProperties.Tools tools;

    public FfmpegCheck(ProcessRunner runner, AppProperties props) {
        this.runner = runner;
        this.tools = props.tools();
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public CheckResult check() {
        StringBuilder detail = new StringBuilder();
        for (String tool : new String[] {tools.ffmpeg(), tools.ffprobe()}) {
            String out;
            try {
                out = runner.run(ProcessRequest.of(TIMEOUT, tool, "-version")).stdout();
            } catch (RuntimeException e) {
                return CheckResult.critical(NAME, tool + " không chạy được: " + e.getMessage(), FIX);
            }
            String first = out.lines().findFirst().orElse("").trim();
            Matcher m = VERSION.matcher(first);
            if (m.find() && Integer.parseInt(m.group(1)) < MIN_MAJOR) {
                return CheckResult.critical(NAME, tool + " quá cũ: " + first, FIX);
            }
            if (detail.length() > 0) {
                detail.append("; ");
            }
            detail.append(first.isEmpty() ? tool : first);
        }
        return CheckResult.ok(NAME, detail.toString());
    }
}
