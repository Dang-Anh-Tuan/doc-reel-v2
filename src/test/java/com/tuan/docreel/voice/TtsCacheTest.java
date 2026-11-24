package com.tuan.docreel.voice;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.common.AppProperties.TtsProviderName;
import com.tuan.docreel.support.TestAppProperties;
import java.nio.file.Path;
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
        TtsSettings s = new TtsSettings(
                TtsProviderName.ELEVENLABS, TtsProviderName.EDGE, "v1", 1.1, "vi-VN-HoaiMyNeural", "+10%");
        var props = TestAppProperties.withDirsUnder(dir);
        assertThat(new ElevenLabsProvider(props).voiceKey(s)).contains("v1").contains("1.1");
        assertThat(new EdgeTtsProvider(null, props).voiceKey(s))
                .contains("HoaiMy")
                .contains("+10%");
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
