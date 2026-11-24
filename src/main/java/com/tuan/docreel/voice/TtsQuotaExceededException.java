package com.tuan.docreel.voice;

import java.io.Serial;

/** Hết quota ElevenLabs (401 {@code quota_exceeded} hoặc 429) → bỏ audio của job, đọc lại bằng provider dự phòng. */
public class TtsQuotaExceededException extends TtsException {

    @Serial
    private static final long serialVersionUID = 1L;

    public TtsQuotaExceededException(String message) {
        super(message);
    }
}
