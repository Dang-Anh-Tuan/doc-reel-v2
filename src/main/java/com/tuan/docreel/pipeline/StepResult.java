package com.tuan.docreel.pipeline;

import com.tuan.docreel.job.JobState;

/**
 * Kết quả một bước thành công. Lỗi được báo bằng exception (P5 quyết định retry hay FAILED ngay).
 *
 * @param nextState state job chuyển tới sau bước này
 * @param skipped true nếu output đã có sẵn nên không chạy lại (idempotent)
 * @param ttsProvider provider TTS đã đọc (chỉ bước giọng điền); worker lưu vào {@code job.tts_provider}, null = không đổi
 * @param ttsChars số ký tự thực gửi provider TTS ở lần này (chỉ bước giọng); worker cộng vào {@code job.tts_chars}
 * @param audioMs tổng độ dài audio (chỉ bước giọng, 0 = không có); worker ghi vào {@code job.audio_ms}
 */
public record StepResult(JobState nextState, boolean skipped, String ttsProvider, long ttsChars, long audioMs) {

    public StepResult(JobState nextState, boolean skipped) {
        this(nextState, skipped, null);
    }

    public StepResult(JobState nextState, boolean skipped, String ttsProvider) {
        this(nextState, skipped, ttsProvider, 0, 0);
    }
}
