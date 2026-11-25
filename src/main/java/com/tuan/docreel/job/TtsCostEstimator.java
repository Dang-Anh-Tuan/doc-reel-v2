package com.tuan.docreel.job;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Ước tính chi phí TTS của một video từ số ký tự đã gửi provider. Chỉ là ước tính: credit thật do ElevenLabs tính
 * (model, gói, làm tròn) có thể lệch. edge-tts miễn phí.
 *
 * <p>Cấu hình (có mặc định trong code): {@code docreel.tts.elevenlabs.credits-per-char} (1.0) và
 * {@code docreel.tts.elevenlabs.usd-per-credit} (0.0002 = 6 USD / 30.000 credit gói Starter).
 */
@Component
public class TtsCostEstimator {

    private static final String EDGE = "edge";

    private final double creditsPerChar;
    private final double usdPerCredit;

    public TtsCostEstimator(
            @Value("${docreel.tts.elevenlabs.credits-per-char:1.0}") double creditsPerChar,
            @Value("${docreel.tts.elevenlabs.usd-per-credit:0.0002}") double usdPerCredit) {
        this.creditsPerChar = creditsPerChar;
        this.usdPerCredit = usdPerCredit;
    }

    /** Credit ước tính (2 chữ số thập phân); edge / không có ký tự → 0. */
    public double credits(String provider, long chars) {
        if (isFree(provider) || chars <= 0) {
            return 0;
        }
        return round(chars * creditsPerChar, 2);
    }

    /** USD ước tính (6 chữ số thập phân); edge / không có ký tự → 0. */
    public double usd(String provider, long chars) {
        if (isFree(provider) || chars <= 0) {
            return 0;
        }
        return round(chars * creditsPerChar * usdPerCredit, 6);
    }

    private static boolean isFree(String provider) {
        return provider != null && EDGE.equals(provider.toLowerCase(Locale.ROOT));
    }

    private static double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
