package com.tuan.docreel.job;

/**
 * Trạng thái job (SPEC mục 5). Thứ tự khai báo = thứ tự pipeline. Luật chuyển trạng thái
 * ({@code canTransitionTo}) là việc của P2.
 */
public enum JobState {
    RECEIVED,
    RENDERED,
    VOICED,
    ASSEMBLED,
    UPLOADED,
    FAILED
}
