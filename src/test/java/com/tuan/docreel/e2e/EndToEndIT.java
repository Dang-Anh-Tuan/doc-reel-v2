package com.tuan.docreel.e2e;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.tuan.docreel.common.AppProperties;
import com.tuan.docreel.common.ProcessRequest;
import com.tuan.docreel.common.ProcessResult;
import com.tuan.docreel.common.ProcessRunner;
import com.tuan.docreel.ingest.InboxWatcher;
import com.tuan.docreel.job.JobRepository;
import com.tuan.docreel.job.JobState;
import com.tuan.docreel.job.VideoJob;
import com.tuan.docreel.pipeline.PipelineWorker;
import com.tuan.docreel.support.TestcontainersConfiguration;
import com.tuan.docreel.youtube.OAuthTokenStore;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

/**
 * UC1 đầu-cuối (PLAN 2.5, SPEC mục 13) với ffmpeg + Playwright/Chromium + Postgres thật; chỉ có edge-tts giả
 * (audio im lặng dài {@code số ký tự x 60ms}, không tốn quota) và YouTube giả (WireMock). Chạy:
 * {@code ./mvnw verify -Pe2e -Dit.test=EndToEndIT}.
 *
 * <p>Thả 3 file vào inbox: hợp lệ, sai (tiêu đề quá dài), bản sao của file hợp lệ. Mong đợi: 1 job UPLOADED với
 * youtube_id, input chuyển sang done/, file sai sang failed/ kèm lỗi, bản sao sang duplicate/; thả lại → không
 * job mới, không upload thêm.
 */
@Tag("e2e")
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, EndToEndIT.FakeTts.class})
class EndToEndIT {

    private static final double FRAME = 1.0 / 30;
    private static final String YOUTUBE_ID = "e2eVideo01";
    private static final Path SAMPLE = Path.of("samples", "full", "sample-all-scenes.json");
    private static final Path INVALID = Path.of("samples", "invalid", "title-too-long.json");
    private static final Path TOKEN_FILE = Path.of("target", "test-data", "youtube-token.enc");
    private static final WireMockServer YOUTUBE =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        YOUTUBE.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String base = "http://localhost:" + YOUTUBE.port();
        registry.add("docreel.youtube.api-base-url", () -> base);
        registry.add("docreel.youtube.token-url", () -> base + "/token");
        registry.add("docreel.youtube.client-id", () -> "e2e-client");
        registry.add("docreel.youtube.client-secret", () -> "e2e-secret");
        registry.add("docreel.youtube.encryption-key", () -> "e2e-passphrase");
        registry.add("docreel.youtube.token-file", TOKEN_FILE::toString);
        registry.add("docreel.youtube.retry-backoff", () -> "PT0S");
        registry.add("docreel.tts.edge.pause-between-calls-ms", () -> "0");
        registry.add("docreel.inbox.poll-interval", () -> "PT1H");
        registry.add("docreel.self-check.interval", () -> "PT1H");
    }

    /** edge-tts giả: thay cho lệnh ngoài, ghi MP3 im lặng dài {@code số ký tự x 60ms} bằng ffmpeg thật. */
    @TestConfiguration(proxyBeanMethods = false)
    static class FakeTts {
        @Bean
        @Primary
        ProcessRunner fakeEdgeTtsRunner(AppProperties props) {
            String edge = props.tts().edge().command();
            return new ProcessRunner() {
                @Override
                public ProcessResult run(ProcessRequest request) {
                    if (!request.program().equals(edge)) {
                        return super.run(request);
                    }
                    List<String> cmd = request.command();
                    try {
                        String text =
                                Files.readString(Path.of(cmd.get(cmd.indexOf("--file") + 1)), StandardCharsets.UTF_8);
                        String media = cmd.get(cmd.indexOf("--write-media") + 1);
                        double seconds = text.length() * 0.06;
                        return super.run(ProcessRequest.of(
                                request.timeout(),
                                props.tools().ffmpeg(),
                                "-y",
                                "-loglevel",
                                "error",
                                "-f",
                                "lavfi",
                                "-i",
                                "anullsrc=r=24000:cl=mono",
                                "-t",
                                String.format(Locale.ROOT, "%.3f", seconds),
                                "-c:a",
                                "libmp3lame",
                                "-b:a",
                                "32k",
                                media));
                    } catch (IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                }
            };
        }
    }

    @Autowired
    InboxWatcher inbox;

    @Autowired
    PipelineWorker worker;

    @Autowired
    JobRepository jobs;

    @Autowired
    AppProperties props;

    @Autowired
    OAuthTokenStore tokenStore;

    @Autowired
    JdbcTemplate jdbc;

    @AfterAll
    static void stopServer() {
        YOUTUBE.stop();
    }

    @BeforeEach
    void reset() throws IOException {
        jdbc.update("delete from job_attempt");
        jdbc.update("delete from video_job");
        for (Path dir : props.dirs().all()) {
            deleteTree(dir);
        }
        Files.deleteIfExists(TOKEN_FILE);
        tokenStore.save("e2e-refresh-token");
        YOUTUBE.resetAll();
        YOUTUBE.stubFor(post(urlPathEqualTo("/token"))
                .willReturn(aResponse().withBody("{\"access_token\":\"e2e-access\",\"expires_in\":3600}")));
        YOUTUBE.stubFor(post(urlPathEqualTo("/upload/youtube/v3/videos"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Location", "http://localhost:" + YOUTUBE.port() + "/session/e2e")));
        YOUTUBE.stubFor(put(urlPathEqualTo("/session/e2e"))
                .willReturn(aResponse().withStatus(200).withBody("{\"id\":\"" + YOUTUBE_ID + "\"}")));
        YOUTUBE.stubFor(get(urlPathEqualTo("/youtube/v3/playlists"))
                .willReturn(aResponse().withBody("{\"items\":[]}")));
        YOUTUBE.stubFor(post(urlPathEqualTo("/youtube/v3/playlists"))
                .willReturn(aResponse().withBody("{\"id\":\"PL-e2e\"}")));
        YOUTUBE.stubFor(post(urlPathEqualTo("/youtube/v3/playlistItems"))
                .willReturn(aResponse().withBody("{}")));
    }

    @Test
    void validInvalidAndDuplicateFiles() throws Exception {
        Files.createDirectories(props.dirs().inbox());
        Files.copy(SAMPLE, props.dirs().inbox().resolve("a-valid.json"));
        Files.copy(INVALID, props.dirs().inbox().resolve("b-invalid.json"));
        Files.copy(SAMPLE, props.dirs().inbox().resolve("c-duplicate.json"));

        assertThat(inbox.scan()).isEqualTo(3);

        List<VideoJob> all = jobs.findAll();
        assertThat(all).as("chỉ file hợp lệ đầu tiên thành job").hasSize(1);
        assertThat(names(props.dirs().failed())).anyMatch(n -> n.startsWith("b-invalid.json"));
        assertThat(names(props.dirs().failed())).anyMatch(n -> n.endsWith(".error.txt"));
        assertThat(names(props.dirs().duplicate())).anyMatch(n -> n.startsWith("c-duplicate"));
        assertThat(props.dirs().inbox()).isEmptyDirectory();

        long start = System.nanoTime();
        int steps = 0;
        while (worker.runOnce()) {
            steps++;
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMinutes(5));
        }
        assertThat(steps)
                .as("RECEIVED→RENDERED→VOICED→ASSEMBLED→UPLOADED = 4 bước")
                .isEqualTo(4);

        VideoJob job = jobs.findById(all.getFirst().getId()).orElseThrow();
        assertThat(job.getState()).as("lỗi: %s", job.getErrorMessage()).isEqualTo(JobState.UPLOADED);
        assertThat(job.getYoutubeId()).isEqualTo(YOUTUBE_ID);
        assertThat(job.getTtsProvider()).isEqualToIgnoringCase("edge");
        assertThat(props.dirs().done().resolve(job.getId() + "-input.json")).isRegularFile();

        YOUTUBE.verify(1, postRequestedFor(urlPathEqualTo("/upload/youtube/v3/videos")));
        YOUTUBE.verify(1, postRequestedFor(urlPathEqualTo("/youtube/v3/playlistItems")));

        assertVideo(props.dirs().work().resolve(job.getId().toString()));

        // Thả lại cùng file → duplicate, không job mới, không upload thêm
        Files.copy(SAMPLE, props.dirs().inbox().resolve("d-again.json"));
        assertThat(inbox.scan()).isEqualTo(1);
        assertThat(jobs.count()).isEqualTo(1);
        assertThat(names(props.dirs().duplicate())).anyMatch(n -> n.startsWith("d-again"));
        assertThat(worker.runOnce()).isFalse();
        YOUTUBE.verify(1, postRequestedFor(urlPathEqualTo("/upload/youtube/v3/videos")));
    }

    private void assertVideo(Path workDir) throws IOException {
        Path mp4 = workDir.resolve("video.mp4");
        assertThat(mp4).isRegularFile();
        String[] size = probe(mp4, "-select_streams", "v:0", "-show_entries", "stream=width,height", "-of", "csv=p=0")
                .trim()
                .split(",");
        assertThat(size).containsExactly("1080", "1920");
        assertThat(probe(mp4, "-select_streams", "a:0", "-show_entries", "stream=codec_type", "-of", "csv=p=0"))
                .contains("audio");

        double videoSeconds = Double.parseDouble(
                probe(mp4, "-show_entries", "format=duration", "-of", "csv=p=0").trim());
        List<Path> audios;
        try (Stream<Path> s = Files.list(workDir)) {
            audios = s.filter(p -> p.getFileName().toString().endsWith(".mp3")).toList();
        }
        double audioSeconds = 0;
        for (Path a : audios) {
            audioSeconds += Double.parseDouble(probe(a, "-show_entries", "format=duration", "-of", "csv=p=0")
                    .trim());
        }
        assertThat(videoSeconds)
                .as("thời lượng video = tổng audio các scene (%d file)", audios.size())
                .isCloseTo(audioSeconds, within(FRAME * 2));
        assertSlideBoundaries(workDir, mp4);

        Path srt = workDir.resolve("subtitles.srt");
        assertThat(srt).isRegularFile();
        Pattern time =
                Pattern.compile("(\\d\\d):(\\d\\d):(\\d\\d),(\\d\\d\\d) --> (\\d\\d):(\\d\\d):(\\d\\d),(\\d\\d\\d)");
        int cues = 0;
        for (String line : Files.readAllLines(srt, StandardCharsets.UTF_8)) {
            Matcher m = time.matcher(line);
            if (m.matches()) {
                cues++;
                double end = Integer.parseInt(m.group(5)) * 3600
                        + Integer.parseInt(m.group(6)) * 60
                        + Integer.parseInt(m.group(7))
                        + Integer.parseInt(m.group(8)) / 1000.0;
                assertThat(end).as("cue %d vượt cuối video", cues).isLessThanOrEqualTo(videoSeconds + FRAME);
            }
        }
        assertThat(cues).as("số cue phụ đề").isGreaterThanOrEqualTo(audios.size());
    }

    /**
     * Mỗi slide bắt đầu đúng lúc audio của nó: cách mốc cộng dồn audio +0,5s khung hình giống PNG của slide đó hơn
     * PNG của slide trước, và -0,15s thì ngược lại (SSIM; scene-detect không dùng được vì các slide cùng nền tối
     * khác nhau rất ít, điểm scene dưới 0,3).
     */
    private void assertSlideBoundaries(Path workDir, Path mp4) throws IOException {
        List<String> units = new ArrayList<>();
        for (var scene : new JsonMapper().readTree(Files.readString(SAMPLE)).path("scenes")) {
            String id = scene.path("id").asString();
            units.add(id);
            if (scene.path("type").asString().equals("quiz")) {
                units.add(id + "-reveal");
            }
        }
        double start = 0;
        for (int i = 0; i < units.size(); i++) {
            if (i > 0) {
                String own = units.get(i);
                String prev = units.get(i - 1);
                Path ownPng = workDir.resolve(own + ".png");
                Path prevPng = workDir.resolve(prev + ".png");
                assertThat(ssim(mp4, start + 0.5, ownPng))
                        .as("%.2fs: đã sang slide %s", start + 0.5, own)
                        .isGreaterThan(ssim(mp4, start + 0.5, prevPng));
                assertThat(ssim(mp4, start - 0.15, prevPng))
                        .as("%.2fs: còn ở slide %s", start - 0.15, prev)
                        .isGreaterThan(ssim(mp4, start - 0.15, ownPng));
            }
            start += Double.parseDouble(
                    probe(workDir.resolve(units.get(i) + ".mp3"), "-show_entries", "format=duration", "-of", "csv=p=0")
                            .trim());
        }
    }

    private double ssim(Path mp4, double at, Path png) {
        String log = new ProcessRunner()
                .run(ProcessRequest.of(
                        Duration.ofMinutes(1),
                        props.tools().ffmpeg(),
                        "-ss",
                        String.format(Locale.ROOT, "%.3f", at),
                        "-i",
                        mp4.toString(),
                        "-i",
                        png.toString(),
                        "-frames:v",
                        "1",
                        "-lavfi",
                        "[0:v]scale=270:480[a];[1:v]scale=270:480[b];[a][b]ssim",
                        "-f",
                        "null",
                        "-"))
                .stderr();
        Matcher m = Pattern.compile("All:([0-9.]+)").matcher(log);
        assertThat(m.find()).as("ssim output: %s", log).isTrue();
        return Double.parseDouble(m.group(1));
    }

    private String probe(Path file, String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(props.tools().ffprobe());
        cmd.add("-v");
        cmd.add("error");
        cmd.addAll(List.of(args));
        cmd.add(file.toString());
        return new ProcessRunner()
                .run(ProcessRequest.of(Duration.ofSeconds(30), cmd))
                .stdout();
    }

    private static List<String> names(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.list(dir)) {
            return s.map(p -> p.getFileName().toString()).toList();
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }
}
