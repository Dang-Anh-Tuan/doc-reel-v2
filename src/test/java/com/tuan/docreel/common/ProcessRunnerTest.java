package com.tuan.docreel.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProcessRunnerTest {

    private static final Duration GENEROUS = Duration.ofSeconds(30);

    private final ProcessRunner runner = new ProcessRunner();

    @Test
    void returnsExitZeroAndStdoutWhenCommandSucceeds() {
        ProcessResult result = runner.run(
                new ProcessRequest(FakeProcess.command("echo", "xin chào ffmpeg"), null, Map.of(), GENEROUS));

        assertThat(result.exitCode()).isZero();
        assertThat(result.stdout()).isEqualTo("xin chào ffmpeg");
        assertThat(result.stderr()).isEmpty();
        assertThat(result.elapsed()).isPositive();
    }

    @Test
    void throwsTimeoutAndKillsProcessWhenCommandRunsTooLong() {
        assertThatThrownBy(() ->
                        runner.run(ProcessRequest.of(Duration.ofMillis(1500), FakeProcess.command("sleep", "60000"))))
                .isInstanceOfSatisfying(ProcessTimeoutException.class, e -> {
                    assertThat(e.timeout()).isEqualTo(Duration.ofMillis(1500));
                    assertThat(e.getMessage()).contains("quá");
                    await().atMost(Duration.ofSeconds(10)).until(() -> !isAlive(e.pid()));
                });
    }

    @Test
    void killsChildProcessesOnTimeout(@TempDir Path tmp) throws Exception {
        Path pidFile = tmp.resolve("child.pid");

        assertThatThrownBy(() -> runner.run(ProcessRequest.of(
                        Duration.ofSeconds(4), FakeProcess.command("spawn-and-sleep", pidFile.toString()))))
                .isInstanceOf(ProcessTimeoutException.class);

        assertThat(pidFile).exists();
        long childPid = Long.parseLong(Files.readString(pidFile, StandardCharsets.UTF_8));
        await().atMost(Duration.ofSeconds(10)).until(() -> !isAlive(childPid));
    }

    @Test
    void throwsFailureContainingStderrWhenExitCodeNonZero() {
        assertThatThrownBy(() ->
                        runner.run(ProcessRequest.of(GENEROUS, FakeProcess.command("fail", "3", "Invalid data found"))))
                .isInstanceOfSatisfying(ProcessFailedException.class, e -> {
                    assertThat(e.exitCode()).isEqualTo(3);
                    assertThat(e.stderr()).isEqualTo("Invalid data found");
                    assertThat(e.getMessage()).contains("thoát mã 3").contains("Invalid data found");
                    assertThat(e.command()).contains("fail");
                    assertThat(e.stdout()).isEmpty();
                });
    }

    @Test
    void readsLargeStdoutAndStderrWithoutDeadlock() {
        ProcessResult result =
                runner.run(ProcessRequest.of(GENEROUS, FakeProcess.command("big", "512"))); // 512 KB mỗi luồng

        assertThat(result.stdout()).hasSize(512 * 1024);
        assertThat(result.stderr()).hasSize(512 * 1024);
    }

    @Test
    void passesEnvironmentAndWorkingDirectory(@TempDir Path tmp) {
        ProcessRequest base = ProcessRequest.of(GENEROUS, FakeProcess.command("env", "DOCREEL_FAKE_MODE"));

        assertThat(runner.run(base.withEnv("DOCREEL_FAKE_MODE", "exit-error")).stdout())
                .isEqualTo("exit-error");
        assertThat(runner.run(ProcessRequest.of(GENEROUS, FakeProcess.command("pwd"))
                                .withWorkingDir(tmp))
                        .stdout())
                .isEqualTo(tmp.toAbsolutePath().toString());
    }

    @Test
    void throwsUncheckedIoWhenProgramDoesNotExist() {
        assertThatThrownBy(() -> runner.run(ProcessRequest.of(GENEROUS, "docreel-no-such-program-xyz")))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("docreel-no-such-program-xyz");
    }

    @Test
    void rejectsInvalidRequests() {
        assertThatThrownBy(() -> ProcessRequest.of(GENEROUS, List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProcessRequest.of(Duration.ZERO, "ffmpeg"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ProcessRequest.of(GENEROUS, "ffmpeg", "-version").program()).isEqualTo("ffmpeg");
    }

    @Test
    void keepsOnlyTailOfVeryLongStderrInMessage() {
        String longErr = "a".repeat(10_000) + "LỖI CUỐI";

        String tail = ProcessFailedException.tail(longErr);

        assertThat(tail).startsWith("…").endsWith("LỖI CUỐI");
        assertThat(tail).hasSize(ProcessFailedException.MAX_STDERR_IN_MESSAGE + 1);
        assertThat(ProcessFailedException.tail(null)).isEmpty();
    }

    private static boolean isAlive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }
}
