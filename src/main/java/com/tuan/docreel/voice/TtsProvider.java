package com.tuan.docreel.voice;

import com.tuan.docreel.common.AppProperties.TtsProviderName;

/** Một nhà cung cấp giọng đọc. Thêm provider mới (vd Azure) không phải sửa {@link TtsService}. */
public interface TtsProvider {

    TtsProviderName name();

    /** false = chưa đủ cấu hình (vd thiếu API key) → không bao giờ được chọn làm provider chính. */
    boolean isConfigured();

    /** Số ký tự còn đọc được trong tháng; edge-tts không giới hạn. */
    long remainingChars();

    /** Phần của khóa cache đại diện cho giọng + tốc độ. */
    String voiceKey(VoiceSettings settings);

    /**
     * Đọc 1 câu → nội dung MP3.
     *
     * @param previous câu liền trước (để ngữ điệu liền mạch), có thể null
     * @param next câu liền sau, có thể null
     * @throws TtsQuotaExceededException hết quota
     * @throws TtsTransientException lỗi mạng / 5xx
     * @throws TtsUnavailableException công cụ dự phòng hỏng
     */
    byte[] synthesize(String sentence, String previous, String next, VoiceSettings settings);
}
