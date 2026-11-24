package com.tuan.docreel.system;

import java.util.Objects;

/**
 * Kết quả kiểm một mục.
 *
 * @param fix hướng dẫn sửa khi không OK (in ra log / doctor), null khi OK
 */
public record CheckResult(String name, CheckStatus status, String detail, String fix) {

    public CheckResult {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(status, "status");
        detail = detail == null ? "" : detail;
    }

    public static CheckResult ok(String name, String detail) {
        return new CheckResult(name, CheckStatus.OK, detail, null);
    }

    public static CheckResult warn(String name, String detail, String fix) {
        return new CheckResult(name, CheckStatus.WARN, detail, fix);
    }

    public static CheckResult critical(String name, String detail, String fix) {
        return new CheckResult(name, CheckStatus.CRITICAL, detail, fix);
    }
}
