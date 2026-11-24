package com.tuan.docreel.pipeline;

/**
 * Exception tự khai báo có nên thử lại không. Đặt ở {@code pipeline} để voice / render / youtube implement mà
 * {@code pipeline} không phải biết các lớp lỗi cụ thể (tránh vòng phụ thuộc giữa các package).
 */
public interface RetryAdvice {

    /** true = lỗi tạm (mạng, 5xx, công cụ ngoài): retry có backoff; false = lỗi nội dung / cấu hình: FAILED ngay. */
    boolean retryable();
}
