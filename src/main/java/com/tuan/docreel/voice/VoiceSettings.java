package com.tuan.docreel.voice;

import com.tuan.docreel.common.AppProperties.TtsProviderName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Giọng + tốc độ của job, đọc từ {@code job.tts_settings} (ảnh chụp lúc tạo job, SPEC mục 7.2) → retry không đổi giọng
 * dù {@code application.yml} đã sửa.
 *
 * @param eleven tinh chỉnh ElevenLabs (P8b); job tạo trước P8b thiếu khóa → {@link ElevenTuning#DEFAULTS}
 */
public record VoiceSettings(
        TtsProviderName primary,
        TtsProviderName fallback,
        String elevenVoiceId,
        double elevenSpeed,
        String edgeVoice,
        String edgeRate,
        ElevenTuning eleven) {

    public VoiceSettings {
        if (eleven == null) {
            eleven = ElevenTuning.DEFAULTS;
        }
    }

    /** Tinh chỉnh ElevenLabs = mặc định. */
    public VoiceSettings(
            TtsProviderName primary,
            TtsProviderName fallback,
            String elevenVoiceId,
            double elevenSpeed,
            String edgeVoice,
            String edgeRate) {
        this(primary, fallback, elevenVoiceId, elevenSpeed, edgeVoice, edgeRate, ElevenTuning.DEFAULTS);
    }

    /**
     * Khóa theo ảnh chụp của {@code settings.YamlTtsSettingsProvider}: primary, fallback, elevenlabs.*, edge.*. Khóa
     * tinh chỉnh (P8b) vắng mặt → mặc định; có mặt nhưng {@code null}/rỗng → giữ null/rỗng (= không gửi).
     */
    public static VoiceSettings fromJson(String json, ObjectMapper mapper) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("job.tts_settings không phải JSON hợp lệ", e);
        }
        JsonNode el = root.path("elevenlabs");
        return new VoiceSettings(
                provider(root, "primary"),
                provider(root, "fallback"),
                text(el, "voiceId"),
                number(el, "speed"),
                text(root.path("edge"), "voice"),
                text(root.path("edge"), "rate"),
                tuning(el));
    }

    private static ElevenTuning tuning(JsonNode el) {
        ElevenTuning d = ElevenTuning.DEFAULTS;
        return new ElevenTuning(
                optionalText(el, "model", d.model()),
                optionalText(el, "languageCode", d.languageCode()),
                optionalNumber(el, "stability", d.stability()),
                optionalNumber(el, "similarityBoost", d.similarityBoost()),
                optionalNumber(el, "style", d.style()),
                optionalBool(el, "speakerBoost", d.speakerBoost()),
                optionalText(el, "textNormalization", d.textNormalization()),
                flag(el, "sendContext", d.sendContext()),
                flag(el, "applyLexicon", d.applyLexicon()));
    }

    private static String optionalText(JsonNode node, String field, String fallback) {
        if (!node.has(field)) {
            return fallback;
        }
        JsonNode v = node.get(field);
        if (v.isNull()) {
            return null;
        }
        if (!v.isString()) {
            throw new IllegalArgumentException("tts_settings." + field + " phải là chuỗi");
        }
        return v.asString();
    }

    private static Double optionalNumber(JsonNode node, String field, Double fallback) {
        if (!node.has(field)) {
            return fallback;
        }
        JsonNode v = node.get(field);
        if (v.isNull()) {
            return null;
        }
        if (!v.isNumber()) {
            throw new IllegalArgumentException("tts_settings." + field + " phải là số");
        }
        return v.asDouble();
    }

    private static Boolean optionalBool(JsonNode node, String field, Boolean fallback) {
        if (!node.has(field)) {
            return fallback;
        }
        JsonNode v = node.get(field);
        if (v.isNull()) {
            return null;
        }
        if (!v.isBoolean()) {
            throw new IllegalArgumentException("tts_settings." + field + " phải là true/false");
        }
        return v.asBoolean();
    }

    private static boolean flag(JsonNode node, String field, boolean fallback) {
        Boolean v = optionalBool(node, field, Boolean.valueOf(fallback));
        return v == null ? fallback : v;
    }

    private static TtsProviderName provider(JsonNode node, String field) {
        try {
            return TtsProviderName.valueOf(text(node, field).toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("tts_settings." + field + " không hợp lệ", e);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (!v.isString() || v.asString().isBlank()) {
            throw new IllegalArgumentException("tts_settings thiếu khóa " + field);
        }
        return v.asString();
    }

    private static double number(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (!v.isNumber()) {
            throw new IllegalArgumentException("tts_settings thiếu khóa " + field);
        }
        return v.asDouble();
    }
}
