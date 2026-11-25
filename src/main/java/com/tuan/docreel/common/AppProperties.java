package com.tuan.docreel.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Cấu hình {@code docreel.*} (SPEC mục 6, 8). Sai / thiếu → app không khởi động. */
@Validated
@ConfigurationProperties("docreel")
public record AppProperties(
        @Valid @NotNull Dirs dirs,
        @Valid @NotNull Tools tools,
        @Valid @NotNull Tts tts,
        @Valid @DefaultValue SelfCheck selfCheck) {

    /** Thư mục làm việc: inbox → work → done / failed / duplicate; requests cho chủ đề tự do (bản 2). */
    public record Dirs(
            @NotNull Path inbox,
            @NotNull Path requests,
            @NotNull Path work,
            @NotNull Path done,
            @NotNull Path failed,
            @NotNull Path duplicate) {

        /** Thư mục mà app phải đọc/ghi được (mục "Thư mục" của self-check). */
        public List<Path> all() {
            return List.of(inbox, requests, work, done, failed, duplicate);
        }
    }

    /** Lệnh ngoài: tên trong PATH hoặc đường dẫn đầy đủ. */
    public record Tools(@NotBlank String ffmpeg, @NotBlank String ffprobe) {}

    public enum TtsProviderName {
        ELEVENLABS,
        EDGE
    }

    public record Tts(
            @NotNull TtsProviderName primary,
            @NotNull TtsProviderName fallback,
            @Valid @NotNull ElevenLabs elevenlabs,
            @Valid @NotNull Edge edge) {}

    /**
     * ElevenLabs. {@code apiKey} rỗng = chưa cấu hình → luôn dùng fallback.
     *
     * <p>P8b — tinh chỉnh giọng (mặc định trong code, xem {@code DEFAULT_*}); giá trị rỗng / null = không gửi field đó
     * (đổi sang model không hỗ trợ field mà không bị 400):
     *
     * @param languageCode ISO 639-1 gửi {@code language_code} (vd {@code vi}); rỗng = để model tự đoán
     * @param stability {@code voice_settings.stability} 0–1 (thấp = biểu cảm hơn)
     * @param similarityBoost {@code voice_settings.similarity_boost} 0–1
     * @param style {@code voice_settings.style} 0–1 (&gt; 0 tốn tài nguyên hơn)
     * @param speakerBoost {@code voice_settings.use_speaker_boost}
     * @param textNormalization {@code apply_text_normalization}: {@code on|off|auto}, rỗng = không gửi
     * @param sendContext true = gửi {@code previous_text}/{@code next_text}
     * @param applyLexicon true = áp {@code config/lexicon.yaml} trước khi gửi (mặc định false: ElevenLabs đọc được
     *     từ tiếng Anh xen trong câu Việt, phiên âm kiểu edge làm giọng ngọng)
     */
    public record ElevenLabs(
            String apiKey,
            @NotNull URI baseUrl,
            @NotBlank String model,
            @NotBlank String voiceId,
            @DecimalMin("0.7") @DecimalMax("1.2") double speed,
            @DefaultValue(DEFAULT_LANGUAGE_CODE) String languageCode,

            @DefaultValue(DEFAULT_STABILITY) @DecimalMin("0.0") @DecimalMax("1.0")
            Double stability,

            @DefaultValue(DEFAULT_SIMILARITY_BOOST) @DecimalMin("0.0") @DecimalMax("1.0")
            Double similarityBoost,

            @DefaultValue(DEFAULT_STYLE) @DecimalMin("0.0") @DecimalMax("1.0")
            Double style,

            @DefaultValue(DEFAULT_SPEAKER_BOOST) Boolean speakerBoost,

            @DefaultValue(DEFAULT_TEXT_NORMALIZATION) @Pattern(regexp = "^(on|off|auto)?$")
            String textNormalization,

            @DefaultValue(DEFAULT_SEND_CONTEXT) boolean sendContext,
            @DefaultValue(DEFAULT_APPLY_LEXICON) boolean applyLexicon) {

        /** YAML 1.1 hiểu {@code on}/{@code off} không ngoặc kép là boolean → Spring đưa vào chuỗi "true"/"false". */
        public ElevenLabs {
            if ("true".equalsIgnoreCase(textNormalization)) {
                textNormalization = "on";
            } else if ("false".equalsIgnoreCase(textNormalization)) {
                textNormalization = "off";
            }
        }

        public static final String DEFAULT_LANGUAGE_CODE = "vi";
        public static final String DEFAULT_STABILITY = "0.4";
        public static final String DEFAULT_SIMILARITY_BOOST = "0.8";
        public static final String DEFAULT_STYLE = "0.2";
        public static final String DEFAULT_SPEAKER_BOOST = "true";
        public static final String DEFAULT_TEXT_NORMALIZATION = "on";
        public static final String DEFAULT_SEND_CONTEXT = "true";
        public static final String DEFAULT_APPLY_LEXICON = "false";

        /** Bộ cấu hình với mọi tham số tinh chỉnh giọng = mặc định trong code (dùng khi dựng tay, vd test). */
        public static ElevenLabs withDefaults(String apiKey, URI baseUrl, String model, String voiceId, double speed) {
            return new ElevenLabs(
                    apiKey,
                    baseUrl,
                    model,
                    voiceId,
                    speed,
                    DEFAULT_LANGUAGE_CODE,
                    Double.valueOf(DEFAULT_STABILITY),
                    Double.valueOf(DEFAULT_SIMILARITY_BOOST),
                    Double.valueOf(DEFAULT_STYLE),
                    Boolean.valueOf(DEFAULT_SPEAKER_BOOST),
                    DEFAULT_TEXT_NORMALIZATION,
                    Boolean.parseBoolean(DEFAULT_SEND_CONTEXT),
                    Boolean.parseBoolean(DEFAULT_APPLY_LEXICON));
        }

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        /** Không bao giờ in key ra log. */
        @Override
        public String toString() {
            return "ElevenLabs[apiKey=" + (hasApiKey() ? "***" : "<empty>") + ", baseUrl=" + baseUrl + ", model="
                    + model + ", voiceId=" + voiceId + ", speed=" + speed + ", languageCode=" + languageCode
                    + ", stability=" + stability + ", similarityBoost=" + similarityBoost + ", style=" + style
                    + ", speakerBoost=" + speakerBoost + ", textNormalization=" + textNormalization
                    + ", sendContext=" + sendContext + ", applyLexicon=" + applyLexicon + "]";
        }
    }

    public record Edge(
            @NotBlank String command,
            @NotBlank String voice,
            @NotBlank @Pattern(regexp = "^[+-]\\d{1,2}%$") String rate,
            @PositiveOrZero long pauseBetweenCallsMs) {}

    public record SelfCheck(@DefaultValue("PT15M") @NotNull Duration interval) {}
}
