package com.tuan.docreel.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.support.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Spring phục vụ web quản trị ở {@code /admin/**} (SPEC 7.1, W1). {@code index.html} lấy từ
 * {@code src/test/resources/static/admin/} (fixture) nên test chạy được cả khi chưa build web ({@code -Dskip.web}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AdminSpaIT {

    private static final String ROOT_DIV = "<div id=\"root\">";

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                        .header("Accept", "text/html,*/*")
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String contentType(HttpResponse<String> res) {
        return res.headers().firstValue("Content-Type").orElse("");
    }

    @Test
    void deepClientRouteReturnsIndexHtml() throws Exception {
        HttpResponse<String> res = get("/admin/jobs/123");

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(contentType(res)).startsWith("text/html");
        assertThat(res.body()).contains(ROOT_DIV);
    }

    @Test
    void adminRootReturnsIndexHtml() throws Exception {
        HttpResponse<String> res = get("/admin/");

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains(ROOT_DIV);
    }

    @Test
    void missingStaticAssetIs404NotHtml() throws Exception {
        HttpResponse<String> res = get("/admin/assets/x.js");

        assertThat(res.statusCode()).isEqualTo(404);
        assertThat(contentType(res)).doesNotStartWith("text/html");
        assertThat(res.body()).doesNotContain(ROOT_DIV);
    }

    @Test
    void systemCheckEndpointReturnsReport() throws Exception {
        HttpResponse<String> res = get("/api/system/check");

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(contentType(res)).contains("json");
        assertThat(res.body()).contains("\"status\"").contains("\"items\"").contains("\"checkedAt\"");
    }
}
