package com.tuan.docreel.youtube;

import com.tuan.docreel.pipeline.RetryAdvice;

/** Lỗi gọi YouTube / Google không thể thử lại (hoặc đã hết lượt thử). */
public class YouTubeApiException extends RuntimeException implements RetryAdvice {

    private static final long serialVersionUID = 1L;

    private final int status;

    public YouTubeApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public YouTubeApiException(String message, Throwable cause) {
        super(message, cause);
        this.status = -1;
    }

    /** Lỗi mạng (-1), 5xx, 408, 429 → thử lại được; 4xx khác (sai quyền, hết quota ngày, request sai) thì không. */
    @Override
    public boolean retryable() {
        return status == -1 || status >= 500 || status == 408 || status == 429;
    }

    /** Mã HTTP, -1 nếu lỗi mạng. */
    public int status() {
        return status;
    }
}
