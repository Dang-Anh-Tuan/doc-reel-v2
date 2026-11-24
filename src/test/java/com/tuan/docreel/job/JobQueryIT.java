package com.tuan.docreel.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.common.AppProperties;
import com.tuan.docreel.support.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

/** P13 qua HTTP thật + Postgres: phân trang + lọc state, chi tiết, slide PNG chỉ của đúng job (chặn {@code ../}). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class JobQueryIT {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 9, 9};

    @Autowired
    Environment env;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JobService jobs;

    @Autowired
    AppProperties props;

    final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void clean() {
        jdbc.update("delete from job_attempt");
        jdbc.update("delete from video_job");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + env.getProperty("local.server.port") + path))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> getBytes(String path) throws Exception {
        return http.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + env.getProperty("local.server.port") + path))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    /** Tạo {@code n} job (cũ → mới), trả id theo thứ tự tạo. */
    private List<UUID> createJobs(int n) {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            var script = JobFixtures.withTitle(JobFixtures.sampleScript(), "Bài " + i);
            UUID id = jobs.submit(script, null).jobId();
            // created_at tách biệt để thứ tự ổn định
            jdbc.update("update video_job set created_at = now() - make_interval(mins => ?) where id = ?", n - i, id);
            ids.add(id);
        }
        return ids;
    }

    private static List<String> ids(JsonNode arr) {
        List<String> out = new ArrayList<>();
        arr.forEach(n -> out.add(n.get("id").asString()));
        return out;
    }

    @Test
    void listPaginatesNewestFirstAndFiltersByState() throws Exception {
        List<UUID> created = createJobs(3);
        jdbc.update(
                "update video_job set state='FAILED', last_successful_step='RENDER', error_message='TTS lỗi' where id=?",
                created.get(0));

        HttpResponse<String> p0 = get("/api/jobs?page=0&size=2");
        assertThat(p0.statusCode()).isEqualTo(200);
        assertThat(p0.headers().firstValue("X-Total-Count")).contains("3");
        JsonNode rows = JSON.readTree(p0.body());
        assertThat(ids(rows))
                .containsExactly(created.get(2).toString(), created.get(1).toString());
        assertThat(rows.get(0).get("title").asString()).isEqualTo("Bài 2");
        assertThat(rows.get(0).get("currentStep").asString()).isEqualTo("RENDER");
        assertThat(rows.get(0).get("ttsProvider").isNull()).isFalse();

        HttpResponse<String> p1 = get("/api/jobs?page=1&size=2");
        assertThat(ids(JSON.readTree(p1.body()))).containsExactly(created.get(0).toString());
        assertThat(p1.headers().firstValue("X-Page")).contains("1");

        HttpResponse<String> failed = get("/api/jobs?state=FAILED");
        assertThat(failed.headers().firstValue("X-Total-Count")).contains("1");
        JsonNode f = JSON.readTree(failed.body()).get(0);
        assertThat(f.get("state").asString()).isEqualTo("FAILED");
        assertThat(f.get("currentStep").asString()).isEqualTo("VOICE");
        assertThat(f.get("errorMessage").asString()).isEqualTo("TTS lỗi");

        // Không tham số: vẫn là mảng như P3
        assertThat(JSON.readTree(get("/api/jobs").body()).size()).isEqualTo(3);
    }

    @Test
    void detailAndSlidesOfOwnJobOnly() throws Exception {
        List<UUID> created = createJobs(2);
        UUID owner = created.get(0);
        UUID other = created.get(1);
        Path work = props.dirs().work();
        Path dir = Files.createDirectories(work.resolve(owner.toString()));
        Files.write(dir.resolve("s01-title.png"), PNG);
        Files.createDirectories(work.resolve(other.toString()));
        Files.write(work.resolve("secret.png"), PNG);
        jdbc.update(
                "update video_job set state='UPLOADED', last_successful_step='UPLOAD', youtube_id='vid123'"
                        + " where id=?",
                owner);

        JsonNode d = JSON.readTree(get("/api/jobs/" + owner).body());
        assertThat(d.get("youtubeUrl").asString()).isEqualTo("https://youtu.be/vid123");
        assertThat(d.get("currentStep").isNull()).isTrue();
        assertThat(d.get("slides").size()).isEqualTo(1);
        String url = d.get("slides").get(0).get("url").asString();
        assertThat(url).isEqualTo("/api/jobs/" + owner + "/slides/1.png");

        HttpResponse<byte[]> png = getBytes(url);
        assertThat(png.statusCode()).isEqualTo(200);
        assertThat(png.headers().firstValue("Content-Type")).contains("image/png");
        assertThat(png.body()).isEqualTo(PNG);

        assertThat(get("/api/jobs/" + other + "/slides/1.png").statusCode()).isEqualTo(404);
        assertThat(get("/api/jobs/" + UUID.randomUUID() + "/slides/1.png").statusCode())
                .isEqualTo(404);
        assertThat(get("/api/jobs/" + owner + "/slides/0.png").statusCode()).isEqualTo(404);
        assertThat(get("/api/jobs/" + owner + "/slides/abc.png").statusCode()).isEqualTo(404);
        assertThat(get("/api/jobs/" + other + "/slides/%2E%2E.png").statusCode())
                .isEqualTo(404);
        // %2F: Tomcat chặn từ connector (encodedSolidusHandling=reject → 400) trước khi tới app; tầng app trả 404
        // cho cùng chuỗi (JobQueryControllerTest.pathTraversalIs404). Điều cần giữ: không bao giờ lộ file.
        for (String n : List.of("..%2F..%2Fsecret", "..%2F" + owner + "%2Fs01-title")) {
            HttpResponse<byte[]> res = getBytes("/api/jobs/" + other + "/slides/" + n + ".png");
            assertThat(res.statusCode()).as(n).isIn(400, 404);
            assertThat(res.body()).as(n).isNotEqualTo(PNG);
        }
    }
}
