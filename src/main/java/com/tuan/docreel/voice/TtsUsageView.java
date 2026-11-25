package com.tuan.docreel.voice;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Quota TTS tháng này ({@code GET /api/tts/usage}). Không khả dụng (thiếu key, lỗi mạng/HTTP) → {@code available =
 * false} + {@code reason} ngắn, các số liệu bỏ trống. Không bao giờ chứa API key.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TtsUsageView(
        boolean available, String provider, Long used, Long limit, Long remaining, Instant resetAt, String reason) {

    static TtsUsageView of(String provider, ElevenLabsProvider.Subscription sub) {
        return new TtsUsageView(
                true, provider, sub.used(), sub.limit(), Math.max(0, sub.limit() - sub.used()), sub.resetAt(), null);
    }

    static TtsUsageView unavailable(String provider, String reason) {
        return new TtsUsageView(false, provider, null, null, null, null, reason);
    }
}
