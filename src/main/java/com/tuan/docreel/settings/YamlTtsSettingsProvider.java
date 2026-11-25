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
        AppProperties.ElevenLabs el = tts.elevenlabs();
        // LinkedHashMap: giữ null (= "không gửi field") trong ảnh chụp, khác với "vắng mặt" (= mặc định, job cũ)
        Map<String, Object> eleven = new LinkedHashMap<>();
        eleven.put("model", el.model());
        eleven.put("voiceId", el.voiceId());
        eleven.put("speed", el.speed());
        eleven.put("languageCode", el.languageCode() == null ? "" : el.languageCode());
        eleven.put("stability", el.stability());
        eleven.put("similarityBoost", el.similarityBoost());
        eleven.put("style", el.style());
        eleven.put("speakerBoost", el.speakerBoost());
        eleven.put("textNormalization", el.textNormalization() == null ? "" : el.textNormalization());
        eleven.put("sendContext", el.sendContext());
        eleven.put("applyLexicon", el.applyLexicon());
        m.put("elevenlabs", eleven);
        m.put("edge", Map.of("voice", tts.edge().voice(), "rate", tts.edge().rate()));
        return new TtsSettings(tts.primary().name(), MAPPER.writeValueAsString(m));
    }
}
