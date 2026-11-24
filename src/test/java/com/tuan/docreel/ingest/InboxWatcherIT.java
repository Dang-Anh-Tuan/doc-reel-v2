package com.tuan.docreel.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.common.AppProperties;
import com.tuan.docreel.job.JobRepository;
import com.tuan.docreel.script.ScriptParser;
import com.tuan.docreel.support.TestcontainersConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "docreel.inbox.poll-interval=PT1H")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class InboxWatcherIT {

    private static final Path VALID = Path.of("samples", "full", "sample-all-scenes.json");
    private static final Path MALFORMED = Path.of("samples", "invalid", "malformed-json.json");

    @Autowired
    InboxWatcher watcher;

    @Autowired
    InboxWriter writer;

    @Autowired
    AppProperties props;

    @Autowired
    JobRepository jobs;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() throws IOException {
        jdbc.update("delete from job_attempt");
        jdbc.update("delete from video_job");
        for (Path d : props.dirs().all()) {
            if (Files.exists(d)) {
                try (Stream<Path> s = Files.walk(d)) {
                    s.sorted(Comparator.reverseOrder())
                            .filter(p -> !p.equals(d))
                            .forEach(p -> p.toFile().delete());
                }
            }
        }
    }

    private long count(Path dir, String suffix) throws IOException {
        if (!Files.exists(dir)) {
            return 0;
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(suffix)).count();
        }
    }

    @Test
    void validFileCreatesJobAndMovesToWork() throws Exception {
        writer.write("claude", "r08", Files.readAllBytes(VALID));

        watcher.scan();

        var all = jobs.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getSourceChannel()).isEqualTo("inbox");
        assertThat(count(props.dirs().inbox(), ".json")).isZero();
        assertThat(props.dirs().work().resolve(all.get(0).getId().toString()).resolve("input.json"))
                .exists();
    }

    @Test
    void brokenJsonGoesToFailedWithErrorFile() throws Exception {
        Path inbox = props.dirs().inbox();
        Files.createDirectories(inbox);
        Files.write(inbox.resolve("claude-20261123T000000-bad.json"), Files.readAllBytes(MALFORMED));

        watcher.scan();

        Path failed = props.dirs().failed();
        assertThat(failed.resolve("claude-20261123T000000-bad.json")).exists();
        Path err = failed.resolve("claude-20261123T000000-bad.json.error.txt");
        assertThat(err).exists();
        var expected = new ScriptParser().parse(Files.readAllBytes(MALFORMED)).error();
        assertThat(expected).hasSize(1);
        assertThat(Files.readString(err).trim())
                .isEqualTo(expected.get(0).path() + ": " + expected.get(0).message());
        assertThat(expected.get(0).path()).isEqualTo("$");
        assertThat(jobs.count()).isZero();
    }

    @Test
    void partFileIsIgnored() throws Exception {
        Path inbox = props.dirs().inbox();
        Files.createDirectories(inbox);
        Path part = inbox.resolve("claude-20261123T000000-x.json.part");
        Files.write(part, Files.readAllBytes(VALID));

        watcher.scan();

        assertThat(part).exists();
        assertThat(jobs.count()).isZero();
        assertThat(count(props.dirs().failed(), "")).isZero();
    }

    @Test
    void resubmittedFileGoesToDuplicate() throws Exception {
        writer.write("claude", "first", Files.readAllBytes(VALID));
        watcher.scan();
        jdbc.update("update video_job set state = 'UPLOADED'");

        writer.write("claude", "again", Files.readAllBytes(VALID));
        watcher.scan();

        assertThat(jobs.count()).isEqualTo(1);
        assertThat(count(props.dirs().duplicate(), ".json")).isEqualTo(1);
        assertThat(count(props.dirs().inbox(), ".json")).isZero();
    }

    @Test
    void crashBetweenSubmitAndMoveIsCompletedNextScan() throws Exception {
        // Giả lập crash: job đã tạo (RECEIVED) nhưng file còn trong inbox, work/{jobId}/input.json chưa có.
        writer.write("claude", "first", Files.readAllBytes(VALID));
        watcher.scan();
        var job = jobs.findAll().get(0);
        Path input = props.dirs().work().resolve(job.getId().toString()).resolve("input.json");
        Files.delete(input);

        writer.write("claude", "retry", Files.readAllBytes(VALID));
        watcher.scan();

        assertThat(jobs.count()).isEqualTo(1);
        assertThat(input).exists();
        assertThat(count(props.dirs().duplicate(), ".json")).isZero();
        assertThat(count(props.dirs().inbox(), ".json")).isZero();
    }
}
