package com.tuan.docreel.voice;

import java.util.UUID;

/**
 * Nguồn cấu hình giọng. MVP đọc {@code application.yml}; bản 2 thêm bản đọc {@code job.tts_settings} / DB mà không
 * sửa {@link TtsService}.
 */
public interface TtsSettingsProvider {

    TtsSettings forJob(UUID jobId);
}
