package com.tuan.docreel.voice;

import com.tuan.docreel.common.AppProperties.TtsProviderName;

/** Cộng dồn số ký tự đã gửi cho từng provider theo tháng ({@code tts_usage}). */
public interface TtsUsageTracker {

    void add(TtsProviderName provider, long chars);
}
