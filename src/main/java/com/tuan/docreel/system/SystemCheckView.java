package com.tuan.docreel.system;

import java.time.Instant;
import java.util.List;

/**
 * Body của {@code GET /api/system/check}: mức chung (xấu nhất trong các mục) + chi tiết từng mục. Web dùng
 * {@code status} để tô thẻ Hệ thống xanh / vàng / đỏ.
 */
public record SystemCheckView(CheckStatus status, Instant checkedAt, List<CheckResult> items) {

    public SystemCheckView {
        items = List.copyOf(items);
    }

    static SystemCheckView of(SelfCheckReport report) {
        CheckStatus status =
                report.hasCritical() ? CheckStatus.CRITICAL : report.hasWarning() ? CheckStatus.WARN : CheckStatus.OK;
        return new SystemCheckView(status, report.checkedAt(), report.results());
    }
}
