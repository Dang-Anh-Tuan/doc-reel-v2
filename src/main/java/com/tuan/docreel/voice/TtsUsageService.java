package com.tuan.docreel.voice;

import com.tuan.docreel.common.AppProperties.TtsProviderName;
import com.tuan.docreel.job.TtsCostEstimator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Quota ElevenLabs tháng này, lấy thẳng từ {@code /v1/user/subscription} (nguồn sự thật, khác {@code tts_usage} chỉ
 * đếm phía app). Cache trong bộ nhớ {@value #TTL_SECONDS} s để trang Jobs tự làm mới không gọi API liên tục. Nhiều
 * request cùng lúc khi cache hết hạn → chỉ một lời gọi ra ngoài (khóa + kiểm tra lại cache sau khi lấy khóa); lời
 * gọi có timeout riêng ngắn ({@link ElevenLabsProvider#SUBSCRIPTION_TIMEOUT}).
 */
@Service
public class TtsUsageService {

    static final long TTL_SECONDS = 60;
    private static final Duration TTL = Duration.ofSeconds(TTL_SECONDS);
    private static final String PROVIDER = TtsProviderName.ELEVENLABS.name();
    private static final Logger log = LoggerFactory.getLogger(TtsUsageService.class);

    private final ElevenLabsProvider elevenLabs;
    private final double creditsPerChar;
    private final Clock clock;
    private final ReentrantLock refreshLock = new ReentrantLock();
    private volatile Cached cached;

    private record Cached(TtsUsageView view, Instant at) {}

    @Autowired
    public TtsUsageService(ElevenLabsProvider elevenLabs, TtsCostEstimator costs) {
        this(elevenLabs, costs, Clock.systemUTC());
    }

    TtsUsageService(ElevenLabsProvider elevenLabs, TtsCostEstimator costs, Clock clock) {
        this.elevenLabs = elevenLabs;
        this.creditsPerChar = costs.creditsPerChar();
        this.clock = clock;
    }

    public TtsUsageView usage() {
        if (!elevenLabs.isConfigured()) {
            return TtsUsageView.unavailable(PROVIDER, "Chưa cấu hình API key ElevenLabs", creditsPerChar);
        }
        TtsUsageView fresh = fresh();
        if (fresh != null) {
            return fresh;
        }
        refreshLock.lock();
        try {
            fresh = fresh(); // request khác vừa làm mới trong lúc chờ khóa
            if (fresh != null) {
                return fresh;
            }
            TtsUsageView view = fetch();
            cached = new Cached(view, clock.instant()); // cache cả lỗi: tránh gọi dồn khi ElevenLabs đang hỏng
            return view;
        } finally {
            refreshLock.unlock();
        }
    }

    private TtsUsageView fresh() {
        Cached c = cached;
        return c != null && clock.instant().isBefore(c.at().plus(TTL)) ? c.view() : null;
    }

    private TtsUsageView fetch() {
        try {
            return TtsUsageView.of(PROVIDER, elevenLabs.subscription(), creditsPerChar);
        } catch (TtsException e) {
            // message của provider chỉ có mã HTTP / lỗi mạng, không chứa key
            log.warn("Không lấy được quota ElevenLabs: {}", e.getMessage());
            return TtsUsageView.unavailable(PROVIDER, e.getMessage(), creditsPerChar);
        } catch (RuntimeException e) {
            log.warn("Không đọc được quota ElevenLabs: {}", e.toString());
            return TtsUsageView.unavailable(PROVIDER, "Không đọc được phản hồi ElevenLabs", creditsPerChar);
        }
    }
}
