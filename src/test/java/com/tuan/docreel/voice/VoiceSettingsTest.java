package com.tuan.docreel.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tuan.docreel.common.AppProperties.TtsProviderName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class VoiceSettingsTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final String JSON = """
            {"primary":"ELEVENLABS","fallback":"EDGE",
             "elevenlabs":{"model":"m","voiceId":"v1","speed":1.1},
             "edge":{"voice":"vi-VN-NamMinhNeural","rate":"+10%"}}
            """;

    @Test
    void readsKeysWrittenByYamlTtsSettingsProvider() {
        assertThat(VoiceSettings.fromJson(JSON, MAPPER))
                .isEqualTo(new VoiceSettings(
                        TtsProviderName.ELEVENLABS, TtsProviderName.EDGE, "v1", 1.1, "vi-VN-NamMinhNeural", "+10%"));
    }

    @Test
    void rejectsMissingOrInvalidKeys() {
        assertThatThrownBy(() -> VoiceSettings.fromJson("{}", MAPPER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VoiceSettings.fromJson("not json", MAPPER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VoiceSettings.fromJson(JSON.replace("\"speed\":1.1", "\"speed\":\"x\""), MAPPER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VoiceSettings.fromJson(JSON.replace("\"EDGE\"", "\"AZURE\""), MAPPER))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
