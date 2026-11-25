package com.tuan.docreel.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tuan.docreel.common.AppProperties;
import com.tuan.docreel.common.AppProperties.TtsProviderName;
import com.tuan.docreel.settings.YamlTtsSettingsProvider;
import com.tuan.docreel.support.TestAppProperties;
import java.net.URI;
import java.nio.file.Path;
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
                        TtsProviderName.ELEVENLABS,
                        TtsProviderName.EDGE,
                        "v1",
                        1.1,
                        "vi-VN-NamMinhNeural",
                        "+10%",
                        new ElevenTuning("m", "vi", 0.4, 0.8, 0.2, true, "on", true, false)));
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

    @Test
    void oldSnapshotWithoutTuningKeysUsesDefaults() {
        VoiceSettings s = VoiceSettings.fromJson(JSON, MAPPER);
        assertThat(s.eleven()).isEqualTo(new ElevenTuning("m", "vi", 0.4, 0.8, 0.2, true, "on", true, false));
        assertThat(s.eleven().languageCode()).isEqualTo(ElevenTuning.DEFAULTS.languageCode());
    }

    @Test
    void readsTuningKeysWrittenBySnapshot() {
        String json = JSON.replace(
                "\"speed\":1.1",
                "\"speed\":1.1,\"languageCode\":\"en\",\"stability\":0.6,\"similarityBoost\":0.5,"
                        + "\"style\":0.0,\"speakerBoost\":false,\"textNormalization\":\"auto\","
                        + "\"sendContext\":false,\"applyLexicon\":true");
        assertThat(VoiceSettings.fromJson(json, MAPPER).eleven())
                .isEqualTo(new ElevenTuning("m", "en", 0.6, 0.5, 0.0, false, "auto", false, true));
    }

    @Test
    void explicitEmptyOrNullTuningMeansDoNotSend() {
        String json = JSON.replace(
                "\"speed\":1.1",
                "\"speed\":1.1,\"languageCode\":\"\",\"stability\":null,\"style\":null,"
                        + "\"speakerBoost\":null,\"textNormalization\":\"\"");
        ElevenTuning t = VoiceSettings.fromJson(json, MAPPER).eleven();
        assertThat(t.languageCode()).isEmpty();
        assertThat(t.stability()).isNull();
        assertThat(t.style()).isNull();
        assertThat(t.speakerBoost()).isNull();
        assertThat(t.textNormalization()).isEmpty();
        assertThat(t.similarityBoost()).isEqualTo(0.8); // vắng mặt → mặc định
    }

    @Test
    void rejectsWrongTuningTypes() {
        assertThatThrownBy(() -> VoiceSettings.fromJson(
                        JSON.replace("\"speed\":1.1", "\"speed\":1.1,\"stability\":\"x\""), MAPPER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VoiceSettings.fromJson(
                        JSON.replace("\"speed\":1.1", "\"speed\":1.1,\"applyLexicon\":\"yes\""), MAPPER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void snapshotRoundTripKeepsConfiguredTuningIncludingDoNotSend() {
        var base = TestAppProperties.withDirsUnder(Path.of("x"));
        var el = new AppProperties.ElevenLabs(
                "",
                URI.create("http://localhost:1"),
                "eleven_v3",
                "v9",
                0.9,
                "",
                null,
                0.7,
                0.0,
                false,
                "",
                false,
                true);
        var props = new AppProperties(
                base.dirs(),
                base.tools(),
                new AppProperties.Tts(
                        base.tts().primary(),
                        base.tts().fallback(),
                        el,
                        base.tts().edge()),
                base.selfCheck());
        String json = new YamlTtsSettingsProvider(props).current().json();

        VoiceSettings s = VoiceSettings.fromJson(json, MAPPER);
        assertThat(s.eleven()).isEqualTo(new ElevenTuning("eleven_v3", "", null, 0.7, 0.0, false, "", false, true));
        assertThat(s.elevenVoiceId()).isEqualTo("v9");
    }
}
