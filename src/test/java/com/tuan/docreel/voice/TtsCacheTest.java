package com.tuan.docreel.voice;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.common.AppProperties.TtsProviderName;
import com.tuan.docreel.support.TestAppProperties;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TtsCacheTest {

    @Test
    void keyDependsOnProviderVoiceSpeedAndSentence() {
        String base = TtsCache.key(TtsProviderName.ELEVENLABS, "m|voice1|1.0", "Xin chào.");
        assertThat(base).hasSize(64).isEqualTo(TtsCache.key(TtsProviderName.ELEVENLABS, "m|voice1|1.0", "Xin chào."));
        assertThat(TtsCache.key(TtsProviderName.EDGE, "m|voice1|1.0", "Xin chào."))
                .isNotEqualTo(base);
        assertThat(TtsCache.key(TtsProviderName.ELEVENLABS, "m|voice2|1.0", "Xin chào."))
                .isNotEqualTo(base);
        assertThat(TtsCache.key(TtsProviderName.ELEVENLABS, "m|voice1|1.1", "Xin chào."))
                .isNotEqualTo(base);
        assertThat(TtsCache.key(TtsProviderName.ELEVENLABS, "m|voice1|1.0", "Xin chào!"))
                .isNotEqualTo(base);
    }

    @Test
    void providerVoiceKeysIncludeVoiceAndSpeed(@TempDir Path dir) {
        VoiceSettings s = new VoiceSettings(
                TtsProviderName.ELEVENLABS, TtsProviderName.EDGE, "v1", 1.1, "vi-VN-HoaiMyNeural", "+10%");
        var props = TestAppProperties.withDirsUnder(dir);
        assertThat(new ElevenLabsProvider(props).voiceKey(s)).contains("v1").contains("1.1");
        assertThat(new EdgeTtsProvider(null, props).voiceKey(s))
                .contains("HoaiMy")
                .contains("+10%");
    }

    @Test
    void elevenLabsVoiceKeyChangesWithEveryAudioAffectingParameter(@TempDir Path dir) {
        ElevenLabsProvider p = new ElevenLabsProvider(TestAppProperties.withDirsUnder(dir));
        ElevenTuning d = new ElevenTuning("m1", "vi", 0.4, 0.8, 0.2, true, "on", true, false);
        VoiceSettings base = VoiceFixtures.settings(d);
        List<VoiceSettings> variants = List.of(
                VoiceFixtures.settings(new ElevenTuning("m2", "vi", 0.4, 0.8, 0.2, true, "on", true, false)),
                VoiceFixtures.settings(new ElevenTuning("m1", "en", 0.4, 0.8, 0.2, true, "on", true, false)),
                VoiceFixtures.settings(new ElevenTuning("m1", "", 0.4, 0.8, 0.2, true, "on", true, false)),
                VoiceFixtures.settings(new ElevenTuning("m1", "vi", 0.5, 0.8, 0.2, true, "on", true, false)),
                VoiceFixtures.settings(new ElevenTuning("m1", "vi", null, 0.8, 0.2, true, "on", true, false)),
                VoiceFixtures.settings(new ElevenTuning("m1", "vi", 0.4, 0.7, 0.2, true, "on", true, false)),
                VoiceFixtures.settings(new ElevenTuning("m1", "vi", 0.4, 0.8, 0.3, true, "on", true, false)),
                VoiceFixtures.settings(new ElevenTuning("m1", "vi", 0.4, 0.8, 0.2, false, "on", true, false)),
                VoiceFixtures.settings(new ElevenTuning("m1", "vi", 0.4, 0.8, 0.2, true, "auto", true, false)),
                VoiceFixtures.settings(new ElevenTuning("m1", "vi", 0.4, 0.8, 0.2, true, "on", false, false)),
                VoiceFixtures.settings(new ElevenTuning("m1", "vi", 0.4, 0.8, 0.2, true, "on", true, true)),
                new VoiceSettings(
                        TtsProviderName.ELEVENLABS,
                        TtsProviderName.EDGE,
                        "voice2",
                        1.0,
                        "vi-VN-HoaiMyNeural",
                        "+0%",
                        d),
                new VoiceSettings(
                        TtsProviderName.ELEVENLABS,
                        TtsProviderName.EDGE,
                        "voice1",
                        1.1,
                        "vi-VN-HoaiMyNeural",
                        "+0%",
                        d));

        String baseKey = p.voiceKey(base);
        assertThat(p.voiceKey(VoiceFixtures.settings(d))).isEqualTo(baseKey);
        List<String> keys = variants.stream().map(p::voiceKey).toList();
        assertThat(keys).doesNotContain(baseKey).doesNotHaveDuplicates();
        // khóa đổi → cache miss
        TtsCache cache = new TtsCache(TestAppProperties.withDirsUnder(dir));
        cache.store(TtsCache.key(TtsProviderName.ELEVENLABS, baseKey, "Câu."), new byte[] {1});
        assertThat(cache.find(TtsCache.key(TtsProviderName.ELEVENLABS, baseKey, "Câu.")))
                .isPresent();
        keys.forEach(k -> assertThat(cache.find(TtsCache.key(TtsProviderName.ELEVENLABS, k, "Câu.")))
                .isEmpty());
    }

    @Test
    void elevenLabsModelFallsBackToCurrentConfigForOldJobs(@TempDir Path dir) {
        ElevenLabsProvider p = new ElevenLabsProvider(TestAppProperties.withDirsUnder(dir));
        assertThat(p.voiceKey(VoiceFixtures.settings())).startsWith("eleven_flash_v2_5|");
        assertThat(p.appliesLexicon(VoiceFixtures.settings())).isFalse();
        assertThat(p.appliesLexicon(VoiceFixtures.settings(VoiceFixtures.withLexicon())))
                .isTrue();
        assertThat(new EdgeTtsProvider(null, TestAppProperties.withDirsUnder(dir))
                        .appliesLexicon(VoiceFixtures.settings()))
                .isTrue();
    }

    @Test
    void storeThenFind(@TempDir Path dir) {
        TtsCache cache = new TtsCache(TestAppProperties.withDirsUnder(dir));
        assertThat(cache.find("abc")).isEmpty();
        Path stored = cache.store("abc", new byte[] {1, 2, 3});
        assertThat(cache.find("abc")).contains(stored);
        assertThat(stored.getParent().getFileName().toString()).isEqualTo("tts-cache");
    }
}
