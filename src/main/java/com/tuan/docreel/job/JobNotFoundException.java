package com.tuan.docreel.job;

import java.util.UUID;

public class JobNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public JobNotFoundException(UUID id) {
        super("Không tìm thấy job " + id);
    }
}
