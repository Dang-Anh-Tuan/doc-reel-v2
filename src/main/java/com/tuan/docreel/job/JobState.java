package com.tuan.docreel.job;

import java.util.EnumSet;
import java.util.Set;

/**
 * Trạng thái job và luật chuyển (SPEC mục 5). Thứ tự khai báo = thứ tự pipeline.
 *
 * <p>Hợp lệ: đi tiến từng bước, mọi trạng thái đang chạy → {@code FAILED}, {@code FAILED → RECEIVED} (retry).
 * {@code UPLOADED} là trạng thái cuối.
 */
public enum JobState {
    RECEIVED,
    RENDERED,
    VOICED,
    ASSEMBLED,
    UPLOADED,
    FAILED;

    public Set<JobState> allowedNext() {
        return switch (this) {
            case RECEIVED -> EnumSet.of(RENDERED, FAILED);
            case RENDERED -> EnumSet.of(VOICED, FAILED);
            case VOICED -> EnumSet.of(ASSEMBLED, FAILED);
            case ASSEMBLED -> EnumSet.of(UPLOADED, FAILED);
            case UPLOADED -> EnumSet.noneOf(JobState.class);
            case FAILED -> EnumSet.of(RECEIVED);
        };
    }

    public boolean canTransitionTo(JobState next) {
        return allowedNext().contains(next);
    }
}
