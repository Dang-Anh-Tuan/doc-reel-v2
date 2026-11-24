package com.tuan.docreel.voice;

import com.tuan.docreel.common.AppProperties.TtsProviderName;
import java.time.Clock;
import java.time.YearMonth;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JdbcTtsUsageTracker implements TtsUsageTracker {

    private static final String UPSERT = """
            INSERT INTO tts_usage (provider, month, chars) VALUES (?, ?, ?)
            ON CONFLICT (provider, month) DO UPDATE SET chars = tts_usage.chars + EXCLUDED.chars
            """;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public JdbcTtsUsageTracker(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemDefaultZone());
    }

    JdbcTtsUsageTracker(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void add(TtsProviderName provider, long chars) {
        if (chars <= 0) {
            return;
        }
        jdbc.update(
                UPSERT,
                provider.name().toLowerCase(Locale.ROOT),
                YearMonth.now(clock).toString(),
                chars);
    }
}
