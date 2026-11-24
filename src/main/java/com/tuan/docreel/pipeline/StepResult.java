package com.tuan.docreel.pipeline;

import com.tuan.docreel.job.JobState;

/**
 * Kết quả một bước thành công. Lỗi được báo bằng exception (P5 quyết định retry hay FAILED ngay).
 *
 * @param nextState state job chuyển tới sau bước này
 * @param skipped true nếu output đã có sẵn nên không chạy lại (idempotent)
 * @param ttsProvider provider TTS đã đọc (chỉ bước giọng điền); worker lưu vào {@code job.tts_provider}, null = không đổi
 */
public record StepResult(JobState nextState, boolean skipped, String ttsProvider) {

    public StepResult(JobState nextState, boolean skipped) {
        this(nextState, skipped, null);
    }
}
