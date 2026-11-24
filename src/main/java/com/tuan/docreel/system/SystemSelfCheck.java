package com.tuan.docreel.system;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Chạy mọi {@link SelfCheckItem} lúc khởi động + định kỳ ({@code docreel.self-check.interval}), giữ kết quả
 * gần nhất cho health indicator, {@code --doctor} và (P5) cờ tạm dừng của {@code PipelineWorker}.
 */
@Component
public class SystemSelfCheck {

    private static final Logger log = LoggerFactory.getLogger(SystemSelfCheck.class);

    private final ObjectProvider<SelfCheckItem> items;
    private final Clock clock;
    private final AtomicReference<SelfCheckReport> last = new AtomicReference<>();

    public SystemSelfCheck(ObjectProvider<SelfCheckItem> items, ObjectProvider<Clock> clock) {
        this.items = items;
        this.clock = clock.getIfAvailable(Clock::systemUTC);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        runNow();
    }

    @Scheduled(
            initialDelayString = "${docreel.self-check.interval:PT15M}",
            fixedDelayString = "${docreel.self-check.interval:PT15M}")
    public void scheduled() {
        runNow();
    }

    /** Chạy toàn bộ mục ngay, lưu và trả kết quả. */
    public SelfCheckReport runNow() {
        List<CheckResult> results = new ArrayList<>();
        items.orderedStream().forEach(item -> results.add(safeCheck(item)));
        SelfCheckReport report = new SelfCheckReport(clock.instant(), results);
        last.set(report);
        logReport(report);
        return report;
    }

    /** Kết quả gần nhất; chưa chạy lần nào thì chạy ngay. */
    public SelfCheckReport lastReport() {
        SelfCheckReport report = last.get();
        return report != null ? report : runNow();
    }

    /** true khi có mục CRITICAL → worker không claim job mới (P5). */
    public boolean hasCriticalFailure() {
        return lastReport().hasCritical();
    }

    private static CheckResult safeCheck(SelfCheckItem item) {
        try {
            CheckResult result = item.check();
            return result != null ? result : CheckResult.critical(item.name(), "Mục kiểm tra không trả kết quả", null);
        } catch (RuntimeException e) {
            return CheckResult.critical(item.name(), "Lỗi khi kiểm: " + e.getMessage(), null);
        }
    }

    private static String fixOf(CheckResult r) {
        return r.fix() == null ? "-" : r.fix();
    }

    private static void logReport(SelfCheckReport report) {
        for (CheckResult r : report.results()) {
            switch (r.status()) {
                case OK -> log.debug("Self-check {}: OK {}", r.name(), r.detail());
                case WARN -> log.warn("Self-check {}: WARN {} → cách sửa: {}", r.name(), r.detail(), fixOf(r));
                case CRITICAL -> log.error("Self-check {}: CRITICAL {} → cách sửa: {}", r.name(), r.detail(), fixOf(r));
            }
        }
        if (report.hasCritical()) {
            log.error("Self-check có mục CRITICAL → hệ thống tạm dừng nhận job cho tới khi sửa xong");
        }
    }
}
