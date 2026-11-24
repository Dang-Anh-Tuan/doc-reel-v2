package com.tuan.docreel.voice;

import java.io.Serial;

/** Lỗi giọng đọc nói chung (cấu hình sai, API từ chối). Không nên retry nếu là loại này. */
public class TtsException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TtsException(String message) {
        super(message);
    }

    public TtsException(String message, Throwable cause) {
        super(message, cause);
    }
}
