package com.tuan.docreel.voice;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * Quota TTS tháng này ({@code GET /api/tts/usage}). Không khả dụng (thiếu key, lỗi mạng/HTTP) → {@code available =
 * false} + {@code reason} ngắn, các số liệu bỏ trống. Không bao giờ chứa API key.
 *
 * @param creditsPerChar đơn giá cấu hình (credit / ký tự) để web đổi "ký tự TB / video" sang credit
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TtsUsageView(
        boolean available,
        String provider,
        Long used,
        Long limit,
        Long remaining,
        Instant resetAt,
        String reason,
        double creditsPerChar) {

    static TtsUsageView of(String provider, ElevenLabsProvider.Subscription sub, double creditsPerChar) {
        return new TtsUsageView(
                true,
                provider,
                sub.used(),
                sub.limit(),
                Math.max(0, sub.limit() - sub.used()),
                sub.resetAt(),
                null,
                creditsPerChar);
    }

    static TtsUsageView unavailable(String provider, String reason, double creditsPerChar) {
        return new TtsUsageView(false, provider, null, null, null, null, reason, creditsPerChar);
    }
}
