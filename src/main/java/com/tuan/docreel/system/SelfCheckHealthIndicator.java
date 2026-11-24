package com.tuan.docreel.system;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/** Gộp self-check vào {@code /actuator/health}: có mục CRITICAL → DOWN (HTTP 503). */
/* Tên bean → khóa "systemSelfCheck" trong /actuator/health. */
@Component("systemSelfCheckHealthIndicator")
public class SelfCheckHealthIndicator implements HealthIndicator {

    private final SystemSelfCheck selfCheck;

    public SelfCheckHealthIndicator(SystemSelfCheck selfCheck) {
        this.selfCheck = selfCheck;
    }

    @Override
    public Health health() {
        SelfCheckReport report = selfCheck.lastReport();
        Health.Builder builder = report.hasCritical() ? Health.down() : Health.up();
        builder.withDetail("checkedAt", report.checkedAt().toString());
        for (CheckResult r : report.results()) {
            builder.withDetail(r.name(), r.status() + (r.detail().isEmpty() ? "" : " - " + r.detail()));
        }
        return builder.build();
    }
}
