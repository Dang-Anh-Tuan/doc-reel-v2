package com.tuan.docreel.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.support.TestAppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DirectoriesCheckTest {

    @Test
    void createsMissingDirectoriesAndReportsOk(@TempDir Path root) {
        DirectoriesCheck check = new DirectoriesCheck(TestAppProperties.withDirsUnder(root));

        CheckResult result = check.check();

        assertThat(result.status()).isEqualTo(CheckStatus.OK);
        assertThat(result.name()).isEqualTo(check.name());
        assertThat(root.resolve("work")).isDirectory();
        assertThat(root.resolve("inbox")).isEmptyDirectory();
    }

    @Test
    void reportsCriticalWhenDirectoryPathIsAFile(@TempDir Path root) throws IOException {
        Files.writeString(root.resolve("work"), "không phải thư mục");
        DirectoriesCheck check = new DirectoriesCheck(TestAppProperties.withDirsUnder(root));

        CheckResult result = check.check();

        assertThat(result.status()).isEqualTo(CheckStatus.CRITICAL);
        assertThat(result.detail()).contains("work").contains("không tạo được");
        assertThat(result.fix()).isNotBlank();
    }
}
