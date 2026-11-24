package com.tuan.docreel.voice;

import com.tuan.docreel.common.AppProperties.TtsProviderName;

/** Giọng + tốc độ của job (SPEC mục 7.2): chụp lúc tạo job → retry không đổi giọng. */
public record TtsSettings(
        TtsProviderName primary,
        TtsProviderName fallback,
        String elevenVoiceId,
        double elevenSpeed,
        String edgeVoice,
        String edgeRate) {}
