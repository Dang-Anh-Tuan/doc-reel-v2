package com.tuan.docreel.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.support.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Gọi HTTP thật tới app + Postgres (Testcontainers): tạo, trùng, xem, retry. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class JobControllerIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    Environment env;

    @Autowired
    JdbcTemplate jdbc;

    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void clean() {
        jdbc.update("delete from job_attempt");
        jdbc.update("delete from video_job");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + env.getProperty("local.server.port") + path);
    }

    private HttpResponse<String> send(HttpRequest.Builder b) throws Exception {
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String body) throws Exception {
        return send(HttpRequest.newBuilder(uri("/api/jobs"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private static String sample() throws Exception {
        return Files.readString(Path.of("samples", "full", "sample-all-scenes.json"));
    }

    @Test
    void createGetRetryFlow() throws Exception {
        HttpResponse<String> created = postJson(sample());
        assertThat(created.statusCode()).isEqualTo(201);
        String id = JSON.readTree(created.body()).get("jobId").asString();

        HttpResponse<String> dup = postJson(sample());
        assertThat(dup.statusCode()).isEqualTo(409);
        assertThat(JSON.readTree(dup.body()).get("jobId").asString()).isEqualTo(id);

        HttpResponse<String> detail = send(HttpRequest.newBuilder(uri("/api/jobs/" + id)));
        assertThat(detail.statusCode()).isEqualTo(200);
        JsonNode d = JSON.readTree(detail.body());
        assertThat(d.get("state").asString()).isEqualTo("RECEIVED");
        assertThat(d.get("attempts").isArray()).isTrue();

        // RECEIVED thì chưa retry được
        HttpResponse<String> early = send(
                HttpRequest.newBuilder(uri("/api/jobs/" + id + "/retry")).POST(HttpRequest.BodyPublishers.noBody()));
        assertThat(early.statusCode()).isEqualTo(409);

        jdbc.update(
                "update video_job set state='FAILED', error_message='lỗi TTS', last_successful_step='RENDER' where id=?",
                UUID.fromString(id));

        HttpResponse<String> dupFailed = postJson(sample());
        assertThat(dupFailed.statusCode()).isEqualTo(409);
        assertThat(dupFailed.body()).contains("retry");

        HttpResponse<String> failedList = send(HttpRequest.newBuilder(uri("/api/jobs?state=FAILED")));
        assertThat(failedList.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(failedList.body()).size()).isEqualTo(1);
        assertThat(JSON.readTree(send(HttpRequest.newBuilder(uri("/api/jobs?state=UPLOADED")))
                                .body())
                        .size())
                .isZero();

        HttpResponse<String> retry = send(
                HttpRequest.newBuilder(uri("/api/jobs/" + id + "/retry")).POST(HttpRequest.BodyPublishers.noBody()));
        assertThat(retry.statusCode()).isEqualTo(202);
        assertThat(JSON.readTree(retry.body()).get("state").asString()).isEqualTo("RECEIVED");
        assertThat(JSON.readTree(retry.body()).get("lastSuccessfulStep").asString())
                .isEqualTo("RENDER");
    }

    @Test
    void invalidScriptReturns400WithFieldErrors() throws Exception {
        String bad = Files.readString(Path.of("samples", "invalid", "duplicate-scene-id.json"));
        HttpResponse<String> res = postJson(bad);
        assertThat(res.statusCode()).isEqualTo(400);
        assertThat(res.headers().firstValue("Content-Type").orElse("")).contains("problem+json");
        JsonNode errors = JSON.readTree(res.body()).get("errors");
        assertThat(errors.get(0).get("field").asString()).isEqualTo("scenes[1].id");
        assertThat(errors.get(0).get("message").asString()).isNotBlank();
        assertThat(jdbc.queryForObject("select count(*) from video_job", Integer.class))
                .isZero();
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        HttpResponse<String> res = postJson("{ không phải json");
        assertThat(res.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(res.body()).get("errors").get(0).get("field").asString())
                .isEqualTo("$");
    }

    @Test
    void unknownJobIs404() throws Exception {
        assertThat(send(HttpRequest.newBuilder(uri("/api/jobs/" + UUID.randomUUID())))
                        .statusCode())
                .isEqualTo(404);
    }
}
