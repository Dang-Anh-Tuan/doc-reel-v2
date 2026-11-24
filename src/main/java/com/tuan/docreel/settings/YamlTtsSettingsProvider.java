package com.tuan.docreel.settings;

import com.tuan.docreel.common.AppProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Đọc cấu hình TTS từ {@code docreel.tts.*}. Không bao giờ đưa API key vào ảnh chụp. */
@Component
public class YamlTtsSettingsProvider implements TtsSettingsProvider {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final AppProperties.Tts tts;

    public YamlTtsSettingsProvider(AppProperties properties) {
        this.tts = properties.tts();
    }

    @Override
    public TtsSettings current() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("primary", tts.primary().name());
        m.put("fallback", tts.fallback().name());
        m.put(
                "elevenlabs",
                Map.of(
                        "model", tts.elevenlabs().model(),
                        "voiceId", tts.elevenlabs().voiceId(),
                        "speed", tts.elevenlabs().speed()));
        m.put("edge", Map.of("voice", tts.edge().voice(), "rate", tts.edge().rate()));
        return new TtsSettings(tts.primary().name(), MAPPER.writeValueAsString(m));
    }
}
