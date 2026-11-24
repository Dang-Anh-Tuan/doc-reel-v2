package com.tuan.docreel.system;

import java.time.Instant;
import java.util.List;

/** Kết quả một lượt self-check. */
public record SelfCheckReport(Instant checkedAt, List<CheckResult> results) {

    public SelfCheckReport {
        results = List.copyOf(results);
    }

    public boolean hasCritical() {
        return results.stream().anyMatch(r -> r.status() == CheckStatus.CRITICAL);
    }

    public boolean hasWarning() {
        return results.stream().anyMatch(r -> r.status() == CheckStatus.WARN);
    }
}
