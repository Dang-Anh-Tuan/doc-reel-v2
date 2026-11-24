package com.tuan.docreel.settings;

/**
 * Ảnh chụp cấu hình TTS lúc tạo job.
 *
 * @param provider tên provider chính (vd {@code ELEVENLABS})
 * @param json cấu hình dạng JSON, không chứa bí mật
 */
public record TtsSettings(String provider, String json) {}
