package com.tuan.docreel.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.common.ProcessRequest;
import com.tuan.docreel.common.ProcessResult;
import com.tuan.docreel.common.ProcessRunner;
import com.tuan.docreel.support.TestAppProperties;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolChecksTest {

    @TempDir
    Path root;

    private FfmpegCheck ffmpeg(Function<ProcessRequest, ProcessResult> behaviour) {
        ProcessRunner runner = new ProcessRunner() {
            @Override
            public ProcessResult run(ProcessRequest request) {
                return behaviour.apply(request);
            }
        };
        return new FfmpegCheck(runner, TestAppProperties.withDirsUnder(root));
    }

    private static ProcessResult out(String text) {
        return new ProcessResult(0, text, "", Duration.ZERO);
    }

    @Test
    void ffmpegOkWhenBothToolsRunAndVersionIsNewEnough() {
        CheckResult r =
                ffmpeg(req -> out("ffmpeg version 9.1-full_build\nmore")).check();
        assertThat(r.status()).isEqualTo(CheckStatus.OK);
        assertThat(r.detail()).contains("9.1");
    }

    @Test
    void ffmpegAcceptsGitBuildVersionString() {
        assertThat(ffmpeg(req -> out("ffmpeg version N-117000-gabcdef")).check().status())
                .isEqualTo(CheckStatus.OK);
    }

    @Test
    void ffmpegTooOldIsCritical() {
        CheckResult r = ffmpeg(req -> out("ffmpeg version 4.4.2")).check();
        assertThat(r.status()).isEqualTo(CheckStatus.CRITICAL);
        assertThat(r.fix()).isNotBlank();
    }

    @Test
    void ffmpegMissingIsCritical() {
        CheckResult r = ffmpeg(req -> {
                    throw new UncheckedIOException(new java.io.IOException("không có trong PATH"));
                })
                .check();
        assertThat(r.status()).isEqualTo(CheckStatus.CRITICAL);
        assertThat(r.detail()).contains("ffmpeg");
    }

    @Test
    void diskThresholds() {
        assertThat(new DiskSpaceCheck(root, p -> 10L << 30).check().status()).isEqualTo(CheckStatus.OK);
        assertThat(new DiskSpaceCheck(root, p -> 1L << 30).check().status()).isEqualTo(CheckStatus.WARN);
        assertThat(new DiskSpaceCheck(root, p -> 100L << 20).check().status()).isEqualTo(CheckStatus.CRITICAL);
    }
}
