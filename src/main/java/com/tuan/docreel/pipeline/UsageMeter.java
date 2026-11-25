package com.tuan.docreel.pipeline;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Bộ đếm tài nguyên trả phí mà một bước đã tiêu, ghi NGAY khi tiêu (không đợi bước xong). {@code PipelineWorker} đọc
 * cả khi bước thành công lẫn khi lỗi giữa chừng → ký tự TTS đã gửi (đã bị tính tiền) không bị mất khỏi
 * {@code job.tts_chars}.
 */
public final class UsageMeter {

    private final AtomicLong ttsChars = new AtomicLong();

    /** Cộng số ký tự vừa gửi provider TTS (bỏ qua giá trị ≤ 0). */
    public void addTtsChars(long chars) {
        if (chars > 0) {
            ttsChars.addAndGet(chars);
        }
    }

    public long ttsChars() {
        return ttsChars.get();
    }
}
