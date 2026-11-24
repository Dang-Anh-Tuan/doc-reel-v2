package com.tuan.docreel.voice;

import com.tuan.docreel.common.AppProperties;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** MVP: mọi job dùng hằng số trong {@code docreel.tts.*}. */
@Component
public class YmlTtsSettingsProvider implements TtsSettingsProvider {

    private final AppProperties props;

    public YmlTtsSettingsProvider(AppProperties props) {
        this.props = props;
    }

    @Override
    public TtsSettings forJob(UUID jobId) {
        AppProperties.Tts tts = props.tts();
        return new TtsSettings(
                tts.primary(),
                tts.fallback(),
                tts.elevenlabs().voiceId(),
                tts.elevenlabs().speed(),
                tts.edge().voice(),
                tts.edge().rate());
    }
}
