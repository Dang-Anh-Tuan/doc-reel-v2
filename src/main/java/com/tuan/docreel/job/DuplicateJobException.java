package com.tuan.docreel.job;

import java.util.UUID;

/** Kịch bản trùng khóa idempotency với job đã có (trả 409). */
public class DuplicateJobException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient UUID jobId;
    private final JobState state;

    public DuplicateJobException(UUID jobId, JobState state) {
        super("Kịch bản này đã được gửi (job " + jobId + ", trạng thái " + state + ")"
                + (state == JobState.FAILED ? "; job đã FAILED, dùng POST /api/jobs/" + jobId + "/retry" : ""));
        this.jobId = jobId;
        this.state = state;
    }

    public UUID jobId() {
        return jobId;
    }

    public JobState state() {
        return state;
    }
}
