package com.tuan.docreel.voice;

import com.tuan.docreel.common.AppProperties.TtsProviderName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Giọng + tốc độ của job, đọc từ {@code job.tts_settings} (ảnh chụp lúc tạo job, SPEC mục 7.2) → retry không đổi giọng
 * dù {@code application.yml} đã sửa.
 */
public record VoiceSettings(
        TtsProviderName primary,
        TtsProviderName fallback,
        String elevenVoiceId,
        double elevenSpeed,
        String edgeVoice,
        String edgeRate) {

    /** Khóa theo ảnh chụp của {@code settings.YamlTtsSettingsProvider}: primary, fallback, elevenlabs.*, edge.*. */
    public static VoiceSettings fromJson(String json, ObjectMapper mapper) {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("job.tts_settings không phải JSON hợp lệ", e);
        }
        return new VoiceSettings(
                provider(root, "primary"),
                provider(root, "fallback"),
                text(root.path("elevenlabs"), "voiceId"),
                number(root.path("elevenlabs"), "speed"),
                text(root.path("edge"), "voice"),
                text(root.path("edge"), "rate"));
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
