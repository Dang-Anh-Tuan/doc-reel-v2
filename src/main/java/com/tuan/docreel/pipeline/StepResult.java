package com.tuan.docreel.pipeline;

import com.tuan.docreel.job.JobState;

/**
 * Kết quả một bước thành công. Lỗi được báo bằng exception (P5 quyết định retry hay FAILED ngay).
 *
 * @param nextState state job chuyển tới sau bước này
 * @param skipped true nếu output đã có sẵn nên không chạy lại (idempotent)
 * @param ttsProvider provider TTS đã đọc (chỉ bước giọng điền); worker lưu vào {@code job.tts_provider}, null = không đổi
 * @param audioMs tổng độ dài audio (chỉ bước giọng, 0 = không có); worker ghi vào {@code job.audio_ms}. Ký tự TTS
 *     đã gửi KHÔNG trả ở đây mà ghi dần vào {@link StepContext#usage()} để không mất khi bước lỗi giữa chừng.
 */
public record StepResult(JobState nextState, boolean skipped, String ttsProvider, long audioMs) {

    public StepResult(JobState nextState, boolean skipped) {
        this(nextState, skipped, null);
    }

    public StepResult(JobState nextState, boolean skipped, String ttsProvider) {
        this(nextState, skipped, ttsProvider, 0);
    }
}
