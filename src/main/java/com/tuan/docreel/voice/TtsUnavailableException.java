package com.tuan.docreel.voice;

import java.io.Serial;

/** edge-tts lỗi (thoát ≠ 0, quá giờ, file rỗng). Hết lượt retry thì job FAILED với mã {@value #CODE}. */
public class TtsUnavailableException extends TtsException {

    public static final String CODE = "TTS_UNAVAILABLE";

    @Serial
    private static final long serialVersionUID = 1L;

    public TtsUnavailableException(String message, Throwable cause) {
        super(CODE + ": " + message, cause);
    }

    public TtsUnavailableException(String message) {
        super(CODE + ": " + message);
    }

    @Override
    public boolean retryable() {
        return true;
    }
}
