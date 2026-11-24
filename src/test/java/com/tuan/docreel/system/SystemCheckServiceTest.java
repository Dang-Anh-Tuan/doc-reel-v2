package com.tuan.docreel.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SystemCheckServiceTest {

    private static final Instant AT = Instant.parse("2026-11-24T08:00:00Z");

    private final SystemSelfCheck selfCheck = mock(SystemSelfCheck.class);
    private final SystemCheckService service = new SystemCheckService(selfCheck);

    private static SelfCheckReport report(CheckResult... results) {
        return new SelfCheckReport(AT, List.of(results));
    }

    @Test
    void overallStatusIsOkWhenEveryItemOk() {
        when(selfCheck.lastReport()).thenReturn(report(CheckResult.ok("DB", "v1"), CheckResult.ok("Thư mục", "")));

        SystemCheckView view = service.check(false);

        assertThat(view.status()).isEqualTo(CheckStatus.OK);
        assertThat(view.checkedAt()).isEqualTo(AT);
        assertThat(view.items()).extracting(CheckResult::name).containsExactly("DB", "Thư mục");
        verify(selfCheck, never()).runNow();
    }

    @Test
    void overallStatusIsWarnWhenAnyItemWarns() {
        when(selfCheck.lastReport())
                .thenReturn(report(CheckResult.ok("DB", ""), CheckResult.warn("TTS", "hết quota", "nạp thêm")));

        assertThat(service.check(false).status()).isEqualTo(CheckStatus.WARN);
    }

    @Test
    void overallStatusIsCriticalWhenAnyItemCritical() {
        when(selfCheck.lastReport())
                .thenReturn(report(
                        CheckResult.warn("TTS", "", null), CheckResult.critical("ffmpeg", "không thấy", "cài ffmpeg")));

        SystemCheckView view = service.check(false);

        assertThat(view.status()).isEqualTo(CheckStatus.CRITICAL);
        assertThat(view.items().get(1).fix()).isEqualTo("cài ffmpeg");
    }

    @Test
    void refreshRunsChecksNow() {
        when(selfCheck.runNow()).thenReturn(report(CheckResult.ok("DB", "")));

        assertThat(service.check(true).status()).isEqualTo(CheckStatus.OK);
        verify(selfCheck).runNow();
        verify(selfCheck, never()).lastReport();
    }
}
