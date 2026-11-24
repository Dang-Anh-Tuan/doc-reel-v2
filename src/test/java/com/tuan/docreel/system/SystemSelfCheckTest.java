package com.tuan.docreel.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.health.contributor.Status;

class SystemSelfCheckTest {

    private static final Instant NOW = Instant.parse("2026-11-23T08:00:00Z");

    private static SystemSelfCheck selfCheckWith(SelfCheckItem... items) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        for (int i = 0; i < items.length; i++) {
            factory.addBean("item" + i, items[i]);
        }
        factory.addBean("clock", Clock.fixed(NOW, ZoneOffset.UTC));
        return new SystemSelfCheck(factory.getBeanProvider(SelfCheckItem.class), factory.getBeanProvider(Clock.class));
    }

    private static SelfCheckItem item(String name, java.util.function.Supplier<CheckResult> check) {
        return new SelfCheckItem() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public CheckResult check() {
                return check.get();
            }
        };
    }

    @Test
    void reportsHealthyWhenAllItemsOk() {
        SystemSelfCheck selfCheck = selfCheckWith(item("DB", () -> CheckResult.ok("DB", "v1")));

        SelfCheckReport report = selfCheck.runNow();

        assertThat(report.checkedAt()).isEqualTo(NOW);
        assertThat(report.hasCritical()).isFalse();
        assertThat(report.hasWarning()).isFalse();
        assertThat(selfCheck.hasCriticalFailure()).isFalse();
    }

    @Test
    void warningDoesNotMakeSystemCritical() {
        SystemSelfCheck selfCheck =
                selfCheckWith(item("TTS", () -> CheckResult.warn("TTS", "hết quota", "đợi tháng sau")));

        SelfCheckReport report = selfCheck.runNow();

        assertThat(report.hasWarning()).isTrue();
        assertThat(report.hasCritical()).isFalse();
        assertThat(new SelfCheckHealthIndicator(selfCheck).health().getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void itemThrowingOrReturningNullCountsAsCritical() {
        SystemSelfCheck selfCheck = selfCheckWith(
                item("Lỗi", () -> {
                    throw new IllegalStateException("nổ");
                }),
                item("Rỗng", () -> null));

        SelfCheckReport report = selfCheck.runNow();

        assertThat(report.results())
                .extracting(CheckResult::status)
                .containsExactly(CheckStatus.CRITICAL, CheckStatus.CRITICAL);
        assertThat(report.results().getFirst().detail()).contains("nổ");
        assertThat(selfCheck.hasCriticalFailure()).isTrue();
    }

    @Test
    void healthIsDownWithDetailsWhenAnyItemCritical() {
        SystemSelfCheck selfCheck = selfCheckWith(
                item("DB", () -> CheckResult.ok("DB", "")),
                item("Thư mục", () -> CheckResult.critical("Thư mục", "work không ghi được", "cấp quyền")));

        var health = new SelfCheckHealthIndicator(selfCheck).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails())
                .containsEntry("DB", "OK")
                .containsEntry("Thư mục", "CRITICAL - work không ghi được");
    }

    @Test
    void lastReportRunsOnceThenReusesCachedResultUntilRunNow() {
        AtomicInteger calls = new AtomicInteger();
        SystemSelfCheck selfCheck = selfCheckWith(item("DB", () -> {
            calls.incrementAndGet();
            return CheckResult.ok("DB", "");
        }));

        selfCheck.lastReport();
        selfCheck.lastReport();
        assertThat(calls).hasValue(1);

        selfCheck.onStartup();
        selfCheck.scheduled();
        assertThat(calls).hasValue(3);
    }

    @Test
    void checkResultDefaultsDetailToEmpty() {
        assertThat(new CheckResult("x", CheckStatus.OK, null, null).detail()).isEmpty();
    }
}
