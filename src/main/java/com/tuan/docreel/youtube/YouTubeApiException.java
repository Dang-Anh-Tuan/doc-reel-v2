package com.tuan.docreel.youtube;

/** Lỗi gọi YouTube / Google không thể thử lại (hoặc đã hết lượt thử). */
public class YouTubeApiException extends RuntimeException {

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

    /** Mã HTTP, -1 nếu lỗi mạng. */
    public int status() {
        return status;
    }
}
