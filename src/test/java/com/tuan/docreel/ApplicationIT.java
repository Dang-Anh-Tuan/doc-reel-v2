package com.tuan.docreel;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.support.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ApplicationIT {

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void flywayCreatesMvpTablesOnly() {
        assertThat(jdbc.queryForObject(
                        "select count(*) from flyway_schema_history where success and version = '1'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForList(
                        "select table_name from information_schema.tables where table_schema = 'public'"
                                + " and table_name <> 'flyway_schema_history' order by table_name",
                        String.class))
                .containsExactly("job_attempt", "tts_usage", "video_job");
        assertThat(jdbc.queryForList(
                        "select column_name from information_schema.columns where table_name = 'video_job'",
                        String.class))
                .contains(
                        "tts_settings",
                        "source_channel",
                        "source_ref",
                        "idempotency_key",
                        "version",
                        "script",
                        "last_successful_step");
    }

    @Test
    void healthEndpointReportsUp() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }
}
