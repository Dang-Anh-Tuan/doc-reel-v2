package com.tuan.docreel.job;

import java.util.UUID;

/** Chỉ job FAILED mới retry được (trả 409). */
public class JobNotRetryableException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public JobNotRetryableException(UUID id, JobState state) {
        super("Job " + id + " đang ở trạng thái " + state + ", chỉ job FAILED mới retry được");
    }
}
