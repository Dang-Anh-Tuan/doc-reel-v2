package com.tuan.docreel.voice;

import com.tuan.docreel.common.AppProperties.TtsProviderName;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Quota ElevenLabs tháng này, lấy thẳng từ {@code /v1/user/subscription} (nguồn sự thật, khác {@code tts_usage} chỉ
 * đếm phía app). Cache trong bộ nhớ {@value #TTL_SECONDS} s để trang Jobs tự làm mới 10 s không gọi API liên tục.
 */
@Service
public class TtsUsageService {

    static final long TTL_SECONDS = 60;
    private static final Duration TTL = Duration.ofSeconds(TTL_SECONDS);
    private static final String PROVIDER = TtsProviderName.ELEVENLABS.name();
    private static final Logger log = LoggerFactory.getLogger(TtsUsageService.class);

    private final ElevenLabsProvider elevenLabs;
    private final Clock clock;
    private volatile Cached cached;

    private record Cached(TtsUsageView view, Instant at) {}

    @Autowired
    public TtsUsageService(ElevenLabsProvider elevenLabs) {
        this(elevenLabs, Clock.systemUTC());
    }

    TtsUsageService(ElevenLabsProvider elevenLabs, Clock clock) {
        this.elevenLabs = elevenLabs;
        this.clock = clock;
    }

    public TtsUsageView usage() {
        if (!elevenLabs.isConfigured()) {
            return TtsUsageView.unavailable(PROVIDER, "Chưa cấu hình API key ElevenLabs");
        }
        Instant now = clock.instant();
        Cached c = cached;
        if (c != null && now.isBefore(c.at().plus(TTL))) {
            return c.view();
        }
        TtsUsageView view;
        try {
            view = TtsUsageView.of(PROVIDER, elevenLabs.subscription());
        } catch (TtsException e) {
            // message của provider chỉ có mã HTTP / lỗi mạng, không chứa key
            log.warn("Không lấy được quota ElevenLabs: {}", e.getMessage());
            view = TtsUsageView.unavailable(PROVIDER, e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Không đọc được quota ElevenLabs: {}", e.toString());
            view = TtsUsageView.unavailable(PROVIDER, "Không đọc được phản hồi ElevenLabs");
        }
        cached = new Cached(view, now); // cache cả lỗi: tránh gọi dồn khi ElevenLabs đang hỏng
        return view;
    }
}
