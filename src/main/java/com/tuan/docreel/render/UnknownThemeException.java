package com.tuan.docreel.render;

/** Theme không có trong {@code static/css/themes/}. Chặn trước khi render. */
public class UnknownThemeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UnknownThemeException(String theme) {
        super("Theme không hợp lệ: " + theme);
    }
}
