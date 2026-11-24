package com.tuan.docreel.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Nơi duy nhất trong app được dùng {@link ProcessBuilder} (luật ArchUnit). Chạy lệnh ngoài có timeout, gom
 * stdout/stderr song song (tránh treo vì đầy pipe), quá giờ thì kill cả cây process.
 */
@Component
public class ProcessRunner {

    private static final Logger log = LoggerFactory.getLogger(ProcessRunner.class);

    /** Thời gian chờ luồng đọc stdout/stderr đóng lại sau khi process đã kết thúc / bị kill. */
    private static final Duration STREAM_DRAIN_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Chạy lệnh và chờ kết thúc.
     *
     * @throws ProcessTimeoutException quá {@link ProcessRequest#timeout()} (process đã bị kill)
     * @throws ProcessFailedException exit code khác 0 (message chứa stderr)
     * @throws UncheckedIOException không khởi động được lệnh (vd không có trong PATH)
     */
    public ProcessResult run(ProcessRequest request) {
        ProcessBuilder builder = new ProcessBuilder(request.command());
        if (request.workingDir() != null) {
            builder.directory(request.workingDir().toFile());
        }
        builder.environment().putAll(request.environment());

        long started = System.nanoTime();
        Process process;
        try {
            process = builder.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Không chạy được lệnh " + request.program() + ": " + e.getMessage(), e);
        }
        log.debug("Chạy {} (pid {}, timeout {})", request.command(), process.pid(), request.timeout());

        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // stdin không dùng; đóng lỗi không ảnh hưởng kết quả
        }
        CompletableFuture<String> stdout = drain(process.getInputStream());
        CompletableFuture<String> stderr = drain(process.getErrorStream());

        boolean finished = waitFor(process, request.timeout());
        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        if (!finished) {
            killTree(process);
            log.warn("Lệnh {} quá {} → đã kill (pid {})", request.program(), request.timeout(), process.pid());
            throw new ProcessTimeoutException(
                    request.command(), request.timeout(), process.pid(), await(stdout), await(stderr));
        }

        ProcessResult result = new ProcessResult(process.exitValue(), await(stdout), await(stderr), elapsed);
        log.debug("Lệnh {} xong: exit {} sau {} ms", request.program(), result.exitCode(), elapsed.toMillis());
        if (result.exitCode() != 0) {
            throw new ProcessFailedException(request.command(), result);
        }
        return result;
    }

    private static boolean waitFor(Process process, Duration timeout) {
        try {
            return process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            killTree(process);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bị ngắt khi chờ process " + process.pid(), e);
        }
    }

    /** Kill con cháu trước (vd {@code cmd /c} trên Windows sinh process con) rồi tới process chính. */
    private static void killTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        try {
            process.waitFor(STREAM_DRAIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static CompletableFuture<String> drain(InputStream stream) {
        CompletableFuture<String> future = new CompletableFuture<>();
        Thread.ofVirtual().name("process-drain").start(() -> {
            try (stream) {
                future.complete(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                future.complete("");
            }
        });
        return future;
    }

    private static String await(CompletableFuture<String> output) {
        try {
            return output.get(STREAM_DRAIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "";
        } catch (ExecutionException | TimeoutException e) {
            return "";
        }
    }
}
