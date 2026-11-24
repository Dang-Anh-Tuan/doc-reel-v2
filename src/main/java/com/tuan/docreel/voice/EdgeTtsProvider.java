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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * edge-tts (Python CLI) qua {@link ProcessRunner}. Câu được ghi ra file UTF-8 rồi truyền {@code --file} (không đưa chữ
 * qua tham số → tránh lỗi codepage Windows, giữ nguyên dấu và ký tự {@code " &}). Gọi tuần tự, nghỉ giữa các câu.
 *
 * <p>Dịch vụ của Microsoft thỉnh thoảng trả {@code NoAudioReceived} cho câu hoàn toàn hợp lệ (hay gặp khi câu tiếng
 * Việt có lẫn từ tiếng Anh), gọi lại vài lần là được → mỗi câu thử lại tối đa {@code attempts} lần, chờ tăng dần. Thiếu
 * lệnh hoặc timeout không thử lại (lỗi cấu hình / treo, gọi lại vô ích).
 */
@Component
public class EdgeTtsProvider implements TtsProvider {

    private static final Logger log = LoggerFactory.getLogger(EdgeTtsProvider.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(1);
    // Đo thực tế (11/2026): NoAudioReceived ngẫu nhiên 10–50% tùy lúc, nghỉ vài giây thường hết → 8 lần, chờ 3, 6, 9…
    // s.
    private static final int DEFAULT_ATTEMPTS = 8;
    private static final Duration DEFAULT_BACKOFF = Duration.ofSeconds(3);

    private final ProcessRunner runner;
    private final AppProperties.Edge config;
    private final Duration timeout;
    private final int attempts;
    private final Duration backoff;
    private long lastCallNanos;
    private boolean called;

    @Autowired
    public EdgeTtsProvider(ProcessRunner runner, AppProperties props) {
        this(runner, props, DEFAULT_TIMEOUT, DEFAULT_ATTEMPTS, DEFAULT_BACKOFF);
    }

    /**
     * @param attempts số lần gọi tối đa cho mỗi câu (≥ 1)
     * @param backoff thời gian chờ trước lần thử thứ n là {@code backoff × (n − 1)}
     */
    public EdgeTtsProvider(
            ProcessRunner runner, AppProperties props, Duration timeout, int attempts, Duration backoff) {
        this.runner = runner;
        this.config = props.tts().edge();
        this.timeout = timeout;
        this.attempts = Math.max(1, attempts);
        this.backoff = backoff;
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
    public String voiceKey(VoiceSettings settings) {
        return settings.edgeVoice() + "|" + settings.edgeRate();
    }

    @Override
    public synchronized byte[] synthesize(String sentence, String previous, String next, VoiceSettings settings) {
        for (int attempt = 1; ; attempt++) {
            try {
                return synthesizeOnce(sentence, settings);
            } catch (RetryableEdgeFailure e) {
                if (attempt >= attempts) {
                    log.warn("edge-tts bỏ cuộc sau {} lần, lỗi cuối: {}", attempts, e.getMessage());
                    throw new TtsUnavailableException(
                            "edge-tts: " + e.getMessage() + " — đã thử " + attempts + " lần", e.failure);
                }
                log.warn(
                        "edge-tts lỗi lần {}/{} ({}), thử lại câu sau {} ms",
                        attempt,
                        attempts,
                        e.getMessage(),
                        backoff.toMillis() * attempt);
                sleep(backoff.multipliedBy(attempt));
            }
        }
    }

    /** Một lần gọi edge-tts; lỗi đáng thử lại được gói trong {@link RetryableEdgeFailure}. */
    private byte[] synthesizeOnce(String sentence, VoiceSettings settings) {
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
                throw new RetryableEdgeFailure(
                        "không tạo ra audio (file rỗng)",
                        new TtsUnavailableException("edge-tts không tạo ra audio (file rỗng)"));
            }
            return Files.readAllBytes(media);
        } catch (ProcessFailedException e) {
            // stderr là traceback Python dài → chỉ giữ dòng cuối (tên exception + lời nhắn) cho job; đủ chi tiết ở log
            log.debug("edge-tts stderr đầy đủ:\n{}", e.stderr());
            throw new RetryableEdgeFailure(
                    summarize(e.exitCode(), e.stderr()),
                    new TtsUnavailableException("edge-tts lỗi: " + e.getMessage(), e));
        } catch (ProcessTimeoutException | UncheckedIOException e) {
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

    private static void sleep(Duration d) {
        if (d.isZero() || d.isNegative()) {
            return;
        }
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TtsTransientException("Bị ngắt khi chờ thử lại edge-tts", e);
        }
    }

    /**
     * Tóm tắt stderr của edge-tts thành 1 dòng: dòng cuối traceback ({@code edge_tts.exceptions.NoAudioReceived: ...})
     * → {@code NoAudioReceived (Microsoft không trả audio)}; lỗi khác: {@code thoát mã N: <dòng cuối, ≤ 200 ký tự>}.
     */
    static String summarize(int exitCode, String stderr) {
        String s = stderr == null ? "" : stderr.strip();
        int nl = s.lastIndexOf('\n');
        String last = (nl < 0 ? s : s.substring(nl + 1)).strip();
        if (last.contains("NoAudioReceived")) {
            return "NoAudioReceived (Microsoft không trả audio)";
        }
        if (last.length() > 200) {
            last = last.substring(0, 200) + "…";
        }
        return "thoát mã " + exitCode + (last.isEmpty() ? "" : ": " + last);
    }

    /** Lỗi một lần gọi mà gọi lại có thể được (exit ≠ 0, file rỗng). Chỉ dùng nội bộ vòng thử lại. */
    private static final class RetryableEdgeFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient TtsUnavailableException failure;

        /** @param summary 1 dòng ngắn (message của exception này), {@code failure} giữ chi tiết làm cause */
        RetryableEdgeFailure(String summary, TtsUnavailableException failure) {
            super(summary, failure, false, false);
            this.failure = failure;
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
