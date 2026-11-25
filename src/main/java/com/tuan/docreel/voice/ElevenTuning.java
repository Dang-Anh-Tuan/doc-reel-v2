package com.tuan.docreel.voice;

import com.tuan.docreel.common.AppProperties;

/**
 * Tham số tinh chỉnh giọng ElevenLabs của một job (P8b), chụp vào {@code job.tts_settings.elevenlabs}. Giá trị
 * {@code null} hoặc chuỗi rỗng = không gửi field đó lên API.
 *
 * @param model {@code model_id}; null = dùng {@code docreel.tts.elevenlabs.model} hiện hành
 * @param languageCode {@code language_code} (ISO 639-1)
 * @param stability {@code voice_settings.stability}
 * @param similarityBoost {@code voice_settings.similarity_boost}
 * @param style {@code voice_settings.style}
 * @param speakerBoost {@code voice_settings.use_speaker_boost}
 * @param textNormalization {@code apply_text_normalization} ({@code on|off|auto})
 * @param sendContext gửi {@code previous_text}/{@code next_text}
 * @param applyLexicon áp lexicon (phiên âm) trước khi gửi
 */
public record ElevenTuning(
        String model,
        String languageCode,
        Double stability,
        Double similarityBoost,
        Double style,
        Boolean speakerBoost,
        String textNormalization,
        boolean sendContext,
        boolean applyLexicon) {

    /** Mặc định trong code (giống {@link AppProperties.ElevenLabs}{@code .DEFAULT_*}); model null = theo cấu hình. */
    public static final ElevenTuning DEFAULTS = new ElevenTuning(
            null,
            AppProperties.ElevenLabs.DEFAULT_LANGUAGE_CODE,
            Double.valueOf(AppProperties.ElevenLabs.DEFAULT_STABILITY),
            Double.valueOf(AppProperties.ElevenLabs.DEFAULT_SIMILARITY_BOOST),
            Double.valueOf(AppProperties.ElevenLabs.DEFAULT_STYLE),
            Boolean.valueOf(AppProperties.ElevenLabs.DEFAULT_SPEAKER_BOOST),
            AppProperties.ElevenLabs.DEFAULT_TEXT_NORMALIZATION,
            Boolean.parseBoolean(AppProperties.ElevenLabs.DEFAULT_SEND_CONTEXT),
            Boolean.parseBoolean(AppProperties.ElevenLabs.DEFAULT_APPLY_LEXICON));

    /** Lấy từ cấu hình hiện hành. */
    public static ElevenTuning of(AppProperties.ElevenLabs c) {
        return new ElevenTuning(
                c.model(),
                c.languageCode(),
                c.stability(),
                c.similarityBoost(),
                c.style(),
                c.speakerBoost(),
                c.textNormalization(),
                c.sendContext(),
                c.applyLexicon());
    }
}
