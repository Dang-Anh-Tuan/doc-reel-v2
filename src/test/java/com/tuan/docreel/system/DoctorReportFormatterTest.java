package com.tuan.docreel.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DoctorReportFormatterTest {

    @Test
    void printsAlignedTableWithFixHintsAndFailVerdict() {
        SelfCheckReport report = new SelfCheckReport(
                Instant.parse("2026-11-23T08:00:00Z"),
                List.of(
                        CheckResult.ok("DB", "schema v1"),
                        CheckResult.critical("Thư mục", "work không ghi được", "cấp quyền ghi"),
                        CheckResult.warn("TTS", "chưa có key", "điền .env")));

        String table = DoctorReportFormatter.format(report);
        List<String> lines = table.lines().toList();

        assertThat(table)
                .contains("| Mục     | Kết quả  | Chi tiết            |")
                .contains("| Thư mục | CRITICAL | work không ghi được |")
                .contains("→ Thư mục: cấp quyền ghi")
                .contains("→ TTS: điền .env")
                .doesNotContain("→ DB")
                .contains("KẾT LUẬN: KHÔNG ĐẠT");
        assertThat(lines.get(1)).isEqualTo("+---------+----------+---------------------+");
    }

    @Test
    void printsPassVerdictWhenNothingCritical() {
        SelfCheckReport report =
                new SelfCheckReport(Instant.EPOCH, List.of(CheckResult.warn("TTS", "chưa có key", null)));

        assertThat(DoctorReportFormatter.format(report))
                .contains("KẾT LUẬN: ĐẠT")
                .doesNotContain("→");
    }

    @Test
    void recognisesDoctorFlag() {
        assertThat(DoctorCommand.isRequested(new String[] {"--spring.profiles.active=dev", "--doctor"}))
                .isTrue();
        assertThat(DoctorCommand.isRequested(new String[] {"--doctorx"})).isFalse();
        assertThat(DoctorCommand.doctorProperties()).containsEntry("spring.flyway.enabled", "false");
    }
}
