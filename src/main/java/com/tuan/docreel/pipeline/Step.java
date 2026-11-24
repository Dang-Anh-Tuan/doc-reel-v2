package com.tuan.docreel.pipeline;

import com.tuan.docreel.job.JobState;

/**
 * Một bước của pipeline (render, giọng, ghép, upload). {@code PipelineWorker} (P5) chọn bước theo state hiện
 * tại của job → thêm bước / loại video mới không phải sửa worker.
 *
 * <p>Mỗi bước phải idempotent: output đã có và hợp lệ trong {@code work/{jobId}/} thì bỏ qua.
 */
public interface Step {

    /** Bước này xử lý job đang ở {@code state} (vd bước render nhận {@code RECEIVED}). */
    boolean supports(JobState state);

    StepResult execute(StepContext context);
}
