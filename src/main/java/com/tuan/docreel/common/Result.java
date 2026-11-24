package com.tuan.docreel.common;

/**
 * Kết quả thành công hoặc lỗi, không dùng exception cho lỗi dữ liệu đầu vào dự kiến trước.
 *
 * @param <V> kiểu giá trị khi thành công
 * @param <E> kiểu lỗi
 */
public final class Result<V, E> {

    private final V value;
    private final E error;
    private final boolean ok;

    private Result(V value, E error, boolean ok) {
        this.value = value;
        this.error = error;
        this.ok = ok;
    }

    public static <V, E> Result<V, E> ok(V value) {
        return new Result<>(value, null, true);
    }

    public static <V, E> Result<V, E> failure(E error) {
        return new Result<>(null, error, false);
    }

    public boolean isOk() {
        return ok;
    }

    /** Giá trị khi thành công; null nếu là lỗi. */
    public V value() {
        return value;
    }

    /** Lỗi khi thất bại; null nếu thành công. */
    public E error() {
        return error;
    }
}
