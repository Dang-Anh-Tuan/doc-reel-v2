package com.tuan.docreel.system;

/**
 * Một mục của {@link SystemSelfCheck} (PLAN 2.6). Mỗi feature tự khai báo bean của mình, vd {@code voice}
 * thêm mục edge-tts / ElevenLabs, {@code render} thêm Chromium, {@code video} thêm ffmpeg — không phải sửa
 * {@link SystemSelfCheck}. Thứ tự hiển thị theo {@link org.springframework.core.annotation.Order}.
 *
 * <p>Mục còn TODO (phase sau): ffmpeg/ffprobe (P9), edge-tts + ElevenLabs quota (P8), Chromium + template
 * mẫu (P6), ổ trống, YouTube refresh token (P10).
 */
public interface SelfCheckItem {

    /** Tên ngắn hiện trong bảng, vd {@code "DB"}, {@code "Thư mục"}. */
    String name();

    /** Kiểm và trả kết quả. Exception ném ra được coi là {@link CheckStatus#CRITICAL}. */
    CheckResult check();
}
