package com.tuan.docreel.job;

import java.util.List;

/** Một trang danh sách job; {@code page}, {@code size} là giá trị đã chuẩn hóa thật sự được dùng. */
public record JobPage(List<JobSummary> items, long total, int page, int size) {}
