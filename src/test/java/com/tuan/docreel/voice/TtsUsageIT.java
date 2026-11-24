package com.tuan.docreel.voice;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.common.AppProperties.TtsProviderName;
import com.tuan.docreel.support.TestAppProperties;
import com.tuan.docreel.support.TestcontainersConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.FileSystemUtils;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TtsUsageIT {

    private static final Path ROOT = createRoot();

    @DynamicPropertySource
    static void dirs(DynamicPropertyRegistry registry) {
        for (String dir : new String[] {"inbox", "requests", "work", "done", "failed", "duplicate"}) {
            registry.add("docreel.dirs." + dir, () -> ROOT.resolve(dir).toString());
        }
    }

    @AfterAll
    static void cleanup() throws IOException {
        FileSystemUtils.deleteRecursively(ROOT);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TtsUsageTracker tracker;

    @Autowired
    TtsService service;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM tts_usage");
    }

    private Map<String, Long> usage() {
        return jdbc.query("SELECT provider, month, chars FROM tts_usage ORDER BY provider, month", rs -> {
            Map<String, Long> m = new java.util.LinkedHashMap<>();
            while (rs.next()) {
                m.put(rs.getString(1) + "@" + rs.getString(2), rs.getLong(3));
            }
            return m;
        });
    }

    @Test
    void contextWiresVoiceBeans() {
        assertThat(service).isNotNull();
        assertThat(tracker).isInstanceOf(JdbcTtsUsageTracker.class);
    }

    @Test
    void addsCharsPerProviderAndMonth() {
        Clock march = Clock.fixed(Instant.parse("2026-03-15T10:00:00Z"), ZoneOffset.UTC);
        Clock april = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneOffset.UTC);
        JdbcTtsUsageTracker inMarch = new JdbcTtsUsageTracker(jdbc, march);
        inMarch.add(TtsProviderName.ELEVENLABS, 100);
        inMarch.add(TtsProviderName.ELEVENLABS, 50);
        inMarch.add(TtsProviderName.EDGE, 30);
        inMarch.add(TtsProviderName.EDGE, 0); // không ghi
        new JdbcTtsUsageTracker(jdbc, april).add(TtsProviderName.ELEVENLABS, 7);

        assertThat(usage())
                .containsExactly(
                        Map.entry("edge@2026-03", 30L),
                        Map.entry("elevenlabs@2026-03", 150L),
                        Map.entry("elevenlabs@2026-04", 7L));
    }

    @Test
    void serviceRecordsCharsOfSentencesActuallyRead() {
        TtsProvider edge = new TtsServiceTest.StubProvider(TtsProviderName.EDGE, true, Long.MAX_VALUE);
        TtsService svc = new TtsService(
                List.of(edge),
                new Lexicon(ROOT.resolve("none.yaml"), false),
                new TtsCache(TestAppProperties.withDirsUnder(ROOT)),
                new FakeAudioTool(),
                tracker);
        TtsSettings edgeOnly =
                new TtsSettings(TtsProviderName.EDGE, TtsProviderName.EDGE, "v", 1.0, "vi-VN-HoaiMyNeural", "+0%");
        var script = VoiceFixtures.script(VoiceFixtures.title("s01", "Câu một. Câu hai."));

        svc.voice(script, ROOT.resolve("work/j1"), edgeOnly, null);
        svc.voice(script, ROOT.resolve("work/j1"), edgeOnly, null); // lần 2: cache, không cộng thêm

        assertThat(usage().values()).containsExactly(16L); // "Câu một." + "Câu hai." = 8 + 8
    }

    private static Path createRoot() {
        try {
            return Files.createTempDirectory("docreel-tts-");
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
