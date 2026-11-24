package com.tuan.docreel.pipeline;

import java.time.Duration;
import java.util.List;

/**
 * Quyết định retry tự động mỗi bước (SPEC mục 5): tối đa 2 lần, backoff 30s → 2 phút. Lỗi tự khai báo
 * {@link RetryAdvice#retryable()} = false (slide tràn, TTS 401...) → FAILED ngay. Lỗi không khai báo (ffmpeg, IO...)
 * coi là lỗi tạm.
 */
public class RetryPolicy {

    public static final RetryPolicy DEFAULT = new RetryPolicy(List.of(Duration.ofSeconds(30), Duration.ofMinutes(2)));

    /** @param delay thời gian chờ trước lần thử lại; null = không retry, job FAILED */
    public record Decision(Duration delay) {

        public boolean retry() {
            return delay != null;
        }
    }

    private final List<Duration> backoffs;

    public RetryPolicy(List<Duration> backoffs) {
        this.backoffs = List.copyOf(backoffs);
    }

    /** @param failedAttempt số lần chạy bước này đã lỗi, tính cả lần vừa rồi (1 = lỗi đầu tiên) */
    public Decision decide(Throwable error, int failedAttempt) {
        if (!retryable(error) || failedAttempt > backoffs.size()) {
            return new Decision(null);
        }
        return new Decision(backoffs.get(failedAttempt - 1));
    }

    static boolean retryable(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof RetryAdvice advice) {
                return advice.retryable();
            }
        }
        return true;
    }
}
