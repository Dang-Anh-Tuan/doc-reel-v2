package com.tuan.docreel.system;

import com.tuan.docreel.common.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Mục "Thư mục" (CRITICAL): tạo thư mục nếu thiếu rồi thử ghi + xóa 1 file tạm trong từng thư mục. */
@Component
@Order(20)
public class DirectoriesCheck implements SelfCheckItem {

    static final String NAME = "Thư mục";

    private final AppProperties properties;

    public DirectoriesCheck(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public CheckResult check() {
        List<String> problems = new ArrayList<>();
        for (Path dir : properties.dirs().all()) {
            String problem = probe(dir);
            if (problem != null) {
                problems.add(problem);
            }
        }
        if (problems.isEmpty()) {
            return CheckResult.ok(NAME, properties.dirs().all().size() + " thư mục ghi được");
        }
        return CheckResult.critical(
                NAME,
                String.join("; ", problems),
                "Tạo thư mục và cấp quyền ghi cho user chạy app, hoặc sửa docreel.dirs.* trong application.yml");
    }

    private static String probe(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            return dir.toAbsolutePath() + " không tạo được (" + e.getMessage() + ")";
        }
        try {
            Path probe = Files.createTempFile(dir, ".selfcheck-", ".tmp");
            Files.delete(probe);
            return null;
        } catch (IOException e) {
            return dir.toAbsolutePath() + " không ghi được (" + e.getClass().getSimpleName() + ")";
        }
    }
}
