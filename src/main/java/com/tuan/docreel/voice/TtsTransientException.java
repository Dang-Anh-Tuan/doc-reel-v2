package com.tuan.docreel.voice;

import java.io.Serial;

/** Lỗi mạng / 5xx của nhà cung cấp: retry theo pipeline, không đổi provider (SPEC mục 8, bước 5). */
public class TtsTransientException extends TtsException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TtsTransientException(String message) {
        super(message);
    }

    public TtsTransientException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public boolean retryable() {
        return true;
    }
}
