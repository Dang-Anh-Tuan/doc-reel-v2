package com.tuan.docreel.system;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Mục "DB" (CRITICAL): kết nối được + Flyway không còn migration chờ / lỗi. Tự dựng Flyway từ DataSource (chỉ
 * đọc {@code flyway_schema_history}) → dùng được cả ở {@code --doctor}, nơi Flyway tự động bị tắt.
 */
@Component
@Order(10)
public class DatabaseCheck implements SelfCheckItem {

    static final String NAME = "DB";
    private static final int VALID_TIMEOUT_SECONDS = 3;

    private final DataSource dataSource;

    public DatabaseCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public CheckResult check() {
        try (Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(VALID_TIMEOUT_SECONDS)) {
                return unreachable("kết nối không hợp lệ");
            }
        } catch (SQLException e) {
            return unreachable(rootMessage(e));
        }

        MigrationInfoService info = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .info();
        long failed =
                Arrays.stream(info.all()).filter(m -> m.getState().isFailed()).count();
        int pending = info.pending().length;
        if (failed > 0 || pending > 0) {
            return CheckResult.critical(
                    NAME,
                    "Flyway: " + pending + " migration chưa chạy, " + failed + " migration lỗi",
                    "Khởi động app bình thường để Flyway migrate; migration lỗi thì sửa SQL rồi flyway repair");
        }
        MigrationInfo current = info.current();
        return CheckResult.ok(NAME, "kết nối OK, schema v" + (current == null ? "?" : current.getVersion()));
    }

    /** Hikari bọc lỗi thật (vd "Connection refused") trong "Connection is not available" → lấy lỗi gốc. */
    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage();
    }

    private static CheckResult unreachable(String reason) {
        return CheckResult.critical(
                NAME,
                "không kết nối được: " + reason,
                "Chạy `docker compose up -d` và kiểm DB_HOST/DB_PORT/DB_USER/DB_PASSWORD");
    }
}
