package com.tuan.docreel.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.support.DirectoryPermissions;
import com.tuan.docreel.support.TestcontainersConfiguration;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.FileSystemUtils;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SystemSelfCheckIT {

    private static final Path ROOT = createRoot();

    @DynamicPropertySource
    static void dirs(DynamicPropertyRegistry registry) {
        for (String dir : new String[] {"inbox", "requests", "work", "done", "failed", "duplicate"}) {
            registry.add("docreel.dirs." + dir, () -> ROOT.resolve(dir).toString());
        }
    }

    @AfterAll
    static void cleanup() throws IOException {
        FileSystemUtils.deleteRecursively(ROOT);
    }

    @LocalServerPort
    int port;

    @Autowired
    SystemSelfCheck selfCheck;

    @Test
    void readOnlyWorkDirMakesDirectoriesCriticalAndHealthDown() throws Exception {
        assertThat(selfCheck.runNow().hasCritical()).isFalse();
        assertThat(health().statusCode()).isEqualTo(200);

        DirectoryPermissions.Restore restore = DirectoryPermissions.denyWrite(ROOT.resolve("work"));
        try {
            SelfCheckReport report = selfCheck.runNow();

            assertThat(report.results())
                    .filteredOn(r -> r.name().equals(DirectoriesCheck.NAME))
                    .singleElement()
                    .satisfies(r -> {
                        assertThat(r.status()).isEqualTo(CheckStatus.CRITICAL);
                        assertThat(r.detail()).contains("work").contains("không ghi được");
                    });
            assertThat(selfCheck.hasCriticalFailure()).isTrue();
            HttpResponse<String> health = health();
            assertThat(health.statusCode()).isEqualTo(503);
            assertThat(health.body()).contains("\"status\":\"DOWN\"");
        } finally {
            restore.close();
        }

        assertThat(selfCheck.runNow().hasCritical()).isFalse();
        assertThat(health().statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> health() throws IOException, InterruptedException {
        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
    }

    private static Path createRoot() {
        try {
            return Files.createTempDirectory("docreel-selfcheck-");
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
