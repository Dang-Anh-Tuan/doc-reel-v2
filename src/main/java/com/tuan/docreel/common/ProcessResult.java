package com.tuan.docreel.common;

import java.time.Duration;

/** Kết quả một lần chạy lệnh ngoài. */
public record ProcessResult(int exitCode, String stdout, String stderr, Duration elapsed) {}
