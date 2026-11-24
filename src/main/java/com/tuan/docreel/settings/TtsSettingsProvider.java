package com.tuan.docreel.settings;

/** Nguồn cấu hình TTS hiện hành. MVP: yml; bản 2: bảng {@code app_setting}. */
public interface TtsSettingsProvider {

    TtsSettings current();
}
