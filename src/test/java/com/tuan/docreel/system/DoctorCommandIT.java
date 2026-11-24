package com.tuan.docreel.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.DocreelApplication;
import com.tuan.docreel.support.TestcontainersConfiguration;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** {@code --doctor}: in bảng, thoát 1 khi có mục CRITICAL, 0 khi đạt; không tự migrate. */
@Testcontainers
class DoctorCommandIT {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestcontainersConfiguration.POSTGRES_IMAGE);

    @TempDir
    static Path dirs;

    @Test
    void reportsPendingMigrationThenPassesAfterMigrate() {
        Run beforeMigrate = doctor(POSTGRES.getJdbcUrl());

        assertThat(beforeMigrate.exitCode()).isEqualTo(1);
        assertThat(beforeMigrate.output())
                .contains("| DB ")
                .contains("CRITICAL")
                .contains("migration chưa chạy")
                .contains("KẾT LUẬN: KHÔNG ĐẠT");

        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        Run afterMigrate = doctor(POSTGRES.getJdbcUrl());

        assertThat(afterMigrate.exitCode()).as(afterMigrate.output()).isZero();
        assertThat(afterMigrate.output())
                .contains("schema v1")
                .contains("| Thư mục ")
                .contains("KẾT LUẬN: ĐẠT");
    }

    @Test
    void reportsUnreachableDatabaseAsCriticalInsteadOfCrashing() {
        Run run = doctor("jdbc:postgresql://localhost:1/docreel");

        assertThat(run.exitCode()).isEqualTo(1);
        assertThat(run.output()).contains("không kết nối được").contains("docker compose up -d");
    }

    private Run doctor(String jdbcUrl) {
        List<String> args = new ArrayList<>(List.of(
                "--doctor",
                "--spring.datasource.url=" + jdbcUrl,
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword()));
        for (String dir : new String[] {"inbox", "requests", "work", "done", "failed", "duplicate"}) {
            args.add("--docreel.dirs." + dir + "=" + dirs.resolve(dir));
        }
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int code = DoctorCommand.run(
                DocreelApplication.class,
                args.toArray(String[]::new),
                new PrintStream(buffer, true, StandardCharsets.UTF_8));
        return new Run(code, buffer.toString(StandardCharsets.UTF_8));
    }

    private record Run(int exitCode, String output) {}
}
