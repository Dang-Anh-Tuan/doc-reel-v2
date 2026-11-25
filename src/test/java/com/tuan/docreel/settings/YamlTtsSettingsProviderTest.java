package com.tuan.docreel.settings;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.support.TestAppProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class YamlTtsSettingsProviderTest {

    @Test
    void snapshotHasProviderAndSettingsButNoApiKey() {
        TtsSettings s = new YamlTtsSettingsProvider(TestAppProperties.withDirsUnder(Path.of("x"))).current();

        assertThat(s.provider()).isEqualTo("ELEVENLABS");
        JsonNode json = JsonMapper.builder().build().readTree(s.json());
        assertThat(json.get("primary").asString()).isEqualTo("ELEVENLABS");
        assertThat(json.get("fallback").asString()).isEqualTo("EDGE");
        assertThat(json.get("elevenlabs").get("model").asString()).isEqualTo("eleven_flash_v2_5");
        assertThat(json.get("edge").get("voice").asString()).isEqualTo("vi-VN-HoaiMyNeural");
        assertThat(s.json()).doesNotContainIgnoringCase("apikey");
    }

    @Test
    void snapshotHasElevenLabsTuning() {
        TtsSettings s = new YamlTtsSettingsProvider(TestAppProperties.withDirsUnder(Path.of("x"))).current();

        JsonNode el = JsonMapper.builder().build().readTree(s.json()).get("elevenlabs");
        assertThat(el.get("languageCode").asString()).isEqualTo("vi");
        assertThat(el.get("stability").asDouble()).isEqualTo(0.4);
        assertThat(el.get("similarityBoost").asDouble()).isEqualTo(0.8);
        assertThat(el.get("style").asDouble()).isEqualTo(0.2);
        assertThat(el.get("speakerBoost").asBoolean()).isTrue();
        assertThat(el.get("textNormalization").asString()).isEqualTo("on");
        assertThat(el.get("sendContext").asBoolean()).isTrue();
        assertThat(el.get("applyLexicon").asBoolean()).isFalse();
    }
}
