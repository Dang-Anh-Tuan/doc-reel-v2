package com.tuan.docreel.job;

import java.util.UUID;

/** Kết quả {@link JobService#submit}: job mới hay đã có (trùng khóa idempotency), kèm id job tương ứng. */
public record SubmitResult(Status status, UUID jobId) {

    public enum Status {
        CREATED,
        DUPLICATE
    }

    public boolean created() {
        return status == Status.CREATED;
    }
}
