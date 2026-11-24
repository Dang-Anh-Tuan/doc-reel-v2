package com.tuan.docreel.system;

/** Kết quả một mục self-check. {@code CRITICAL} → worker tạm dừng, health DOWN, doctor thoát mã 1. */
public enum CheckStatus {
    OK,
    WARN,
    CRITICAL
}
