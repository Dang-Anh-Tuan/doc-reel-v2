package com.tuan.docreel.voice;

import com.tuan.docreel.common.AppProperties;
import com.tuan.docreel.common.AppProperties.TtsProviderName;
import com.tuan.docreel.common.ProcessFailedException;
import com.tuan.docreel.common.ProcessRequest;
import com.tuan.docreel.common.ProcessRunner;
import com.tuan.docreel.common.ProcessTimeoutException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * edge-tts (Python CLI) qua {@link ProcessRunner}. Câu được ghi ra file UTF-8 rồi truyền {@code --file} (không đưa chữ
 * qua tham số → tránh lỗi codepage Windows, giữ nguyên dấu và ký tự {@code " &}). Gọi tuần tự, nghỉ giữa các câu.
 */
@Component
public class EdgeTtsProvider implements TtsProvider {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(1);

    private final ProcessRunner runner;
    private final AppProperties.Edge config;
    private final Duration timeout;
    private long lastCallNanos;
    private boolean called;

    @Autowired
    public EdgeTtsProvider(ProcessRunner runner, AppProperties props) {
        this(runner, props, DEFAULT_TIMEOUT);
    }

    public EdgeTtsProvider(ProcessRunner runner, AppProperties props, Duration timeout) {
        this.runner = runner;
        this.config = props.tts().edge();
        this.timeout = timeout;
    }

    @Override
    public TtsProviderName name() {
        return TtsProviderName.EDGE;
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public long remainingChars() {
        return Long.MAX_VALUE;
    }

    @Override
    public String voiceKey(TtsSettings settings) {
        return settings.edgeVoice() + "|" + settings.edgeRate();
    }

    @Override
    public synchronized byte[] synthesize(String sentence, String previous, String next, TtsSettings settings) {
        pause();
        Path dir = null;
        try {
            dir = Files.createTempDirectory("edge-tts-");
            Path text = dir.resolve("sentence.txt");
            Path media = dir.resolve("sentence.mp3");
            Files.writeString(text, sentence, StandardCharsets.UTF_8);
            // "--rate=-10%" dạng một tham số: argparse không hiểu "--rate -10%" (số âm bị coi là cờ)
            List<String> command = List.of(
                    config.command(),
                    "--voice",
                    settings.edgeVoice(),
                    "--rate=" + settings.edgeRate(),
                    "--file",
                    text.toString(),
                    "--write-media",
                    media.toString());
            runner.run(ProcessRequest.of(timeout, command)
                    .withEnv("PYTHONUTF8", "1")
                    .withEnv("PYTHONIOENCODING", "utf-8"));
            if (!Files.exists(media) || Files.size(media) == 0) {
                throw new TtsUnavailableException("edge-tts không tạo ra audio (file rỗng)");
            }
            return Files.readAllBytes(media);
        } catch (ProcessFailedException | ProcessTimeoutException | UncheckedIOException e) {
            throw new TtsUnavailableException("edge-tts lỗi: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new TtsUnavailableException("edge-tts lỗi file tạm: " + e.getMessage(), e);
        } finally {
            called = true;
            lastCallNanos = System.nanoTime();
            deleteQuietly(dir);
        }
    }

    private void pause() {
        if (!called || config.pauseBetweenCallsMs() <= 0) {
            return;
        }
        long waitMs = config.pauseBetweenCallsMs() - (System.nanoTime() - lastCallNanos) / 1_000_000;
        if (waitMs > 0) {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TtsTransientException("Bị ngắt khi nghỉ giữa các câu", e);
            }
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // thư mục tạm, không ảnh hưởng kết quả
        }
    }
}
