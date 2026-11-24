package com.tuan.docreel.render;

import com.tuan.docreel.pipeline.RetryAdvice;

/** Theme không có trong {@code static/css/themes/}. Chặn trước khi render. */
public class UnknownThemeException extends RuntimeException implements RetryAdvice {

    private static final long serialVersionUID = 1L;

    public UnknownThemeException(String theme) {
        super("Theme không hợp lệ: " + theme);
    }

    @Override
    public boolean retryable() {
        return false;
    }
}
