package com.tuan.docreel.e2e;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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
import tools.jackson.databind.node.ObjectNode;

/**
 * UC1 đầu-cuối (PLAN 2.5, SPEC mục 13) với ffmpeg + Playwright/Chromium + Postgres thật; chỉ có edge-tts giả
 * (audio im lặng dài {@code số ký tự x 60ms}, không tốn quota) và YouTube giả (WireMock). Chạy:
 * {@code ./mvnw verify -Pe2e -Dit.test=EndToEndIT}.
 *
 * <p>Thả 3 file vào inbox: 2 hợp lệ (khác topicId) + 1 sai (tiêu đề quá dài). Mong đợi: 2 job UPLOADED với
 * youtube_id, input sang done/, file sai sang failed/ kèm lỗi và không ảnh hưởng 2 job kia. ElevenLabs (WireMock)
 * báo còn quota nhưng từ chối {@code quota_exceeded} khi đọc → fallback edge-tts, 0 ký tự ElevenLabs. Mỗi job có
 * 4 attempt SUCCESS trong DB và log mang MDC {@code jobId}. Thả lại file đã có → duplicate/, không job, không upload.
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
    private static final String SECOND_TOPIC = "R-98";
    private static final String BROKEN_TOPIC = "R-97";
    private static final WireMockServer YOUTUBE =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());
    private static final WireMockServer ELEVEN =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        YOUTUBE.start();
        ELEVEN.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // ElevenLabs giả: key giả (đè biến môi trường nếu có, không bao giờ gọi API thật), hết quota khi đọc
        registry.add("docreel.tts.primary", () -> "elevenlabs");
        registry.add("docreel.tts.elevenlabs.api-key", () -> "e2e-fake-key");
        registry.add("docreel.tts.elevenlabs.base-url", () -> "http://localhost:" + ELEVEN.port());
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

    /** Chụp MDC ngay lúc ghi log (LoggingEvent đọc MDC lười, đọc sau khi worker xóa MDC sẽ rỗng). */
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>() {
        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            super.append(event);
        }
    };

    @AfterAll
    static void stopServer() {
        YOUTUBE.stop();
        ELEVEN.stop();
    }

    @AfterEach
    void detachLogs() {
        rootLogger().detachAppender(logs);
        logs.stop();
    }

    private static ch.qos.logback.classic.Logger rootLogger() {
        return (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    @BeforeEach
    void reset() throws IOException {
        logs.start();
        rootLogger().addAppender(logs);
        jdbc.update("delete from tts_usage");
        ELEVEN.resetAll();
        ELEVEN.stubFor(get(urlPathEqualTo("/v1/user/subscription"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"character_limit\":1000000,\"character_count\":0}")));
        ELEVEN.stubFor(post(urlPathMatching("/v1/text-to-speech/.*"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withBody("{\"detail\":{\"status\":\"quota_exceeded\",\"message\":\"quota\"}}")));
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
    void threeFilesOneInvalidThenDuplicateWithElevenLabsQuotaFallback() throws Exception {
        Path inboxDir = props.dirs().inbox();
        Files.createDirectories(inboxDir);
        Files.copy(SAMPLE, inboxDir.resolve("a-valid.json"));
        Files.copy(INVALID, inboxDir.resolve("b-invalid.json"));
        // File hợp lệ thứ hai: cùng nội dung, khác topicId → khóa idempotency khác → job riêng
        String second = Files.readString(SAMPLE, StandardCharsets.UTF_8)
                .replaceFirst("\"topicId\"\\s*:\\s*\"R-08\"", "\"topicId\": \"" + SECOND_TOPIC + "\"");
        assertThat(second).contains(SECOND_TOPIC);
        Files.writeString(inboxDir.resolve("c-valid.json"), second, StandardCharsets.UTF_8);
        // File qua validate nhưng hỏng lúc chạy: chữ W liền (rộng nhất, không ngắt) đúng giới hạn ký tự → slide tràn
        Files.writeString(inboxDir.resolve("e-overflow.json"), overflowingScript(), StandardCharsets.UTF_8);

        assertThat(inbox.scan()).isEqualTo(4);

        List<VideoJob> created = jobs.findAll();
        assertThat(created)
                .as("3 file qua validate thành 3 job, file sai ở ingest không tạo job")
                .hasSize(3);
        assertThat(names(props.dirs().failed())).anyMatch(n -> n.startsWith("b-invalid.json"));
        assertThat(names(props.dirs().failed())).anyMatch(n -> n.endsWith(".error.txt"));
        assertThat(inboxDir).isEmptyDirectory();
        VideoJob broken = created.stream()
                .filter(j -> j.getTopicId().equals(BROKEN_TOPIC))
                .findFirst()
                .orElseThrow();
        List<VideoJob> good = created.stream().filter(j -> j != broken).toList();

        long start = System.nanoTime();
        int steps = 0;
        while (worker.runOnce()) {
            steps++;
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMinutes(10));
        }
        assertThat(steps)
                .as("2 job × 4 bước (RECEIVED→…→UPLOADED) + 1 bước RENDER lỗi của job tràn = 9")
                .isEqualTo(9);

        // Job hỏng lúc chạy: FAILED ở RENDER, có lỗi, không làm hỏng 2 job kia
        VideoJob failed = jobs.findById(broken.getId()).orElseThrow();
        assertThat(failed.getState()).isEqualTo(JobState.FAILED);
        assertThat(failed.getErrorMessage()).contains("tràn");
        assertThat(failed.getYoutubeId()).isNull();
        assertThat(jdbc.queryForList(
                        "select step || ':' || attempt_no || ':' || outcome from job_attempt where job_id = ?"
                                + " and error is not null",
                        String.class,
                        failed.getId()))
                .containsExactly("RENDER:1:FAILED");

        for (VideoJob each : good) {
            VideoJob job = jobs.findById(each.getId()).orElseThrow();
            assertThat(job.getState()).as("lỗi: %s", job.getErrorMessage()).isEqualTo(JobState.UPLOADED);
            assertThat(job.getYoutubeId()).isEqualTo(YOUTUBE_ID);
            assertThat(job.getTtsProvider())
                    .as("ElevenLabs hết quota → edge-tts")
                    .isEqualToIgnoringCase("edge");
            assertThat(props.dirs().done().resolve(job.getId() + "-input.json")).isRegularFile();
            assertAttempts(job);
            assertJobLogs(job);
            assertVideo(props.dirs().work().resolve(job.getId().toString()));
        }

        YOUTUBE.verify(2, postRequestedFor(urlPathEqualTo("/upload/youtube/v3/videos")));
        YOUTUBE.verify(
                2,
                postRequestedFor(urlPathEqualTo("/upload/youtube/v3/videos"))
                        .withRequestBody(matchingJsonPath("$.status.privacyStatus", equalTo("private"))));
        YOUTUBE.verify(2, postRequestedFor(urlPathEqualTo("/youtube/v3/playlistItems")));
        // ElevenLabs được thử (còn quota theo /subscription) nhưng từ chối quota_exceeded → không tính ký tự nào
        ELEVEN.verify(2, getRequestedFor(urlPathEqualTo("/v1/user/subscription")));
        assertThat(ELEVEN.findAll(postRequestedFor(urlPathMatching("/v1/text-to-speech/.*"))))
                .hasSizeGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForObject(
                        "select coalesce(sum(chars), 0) from tts_usage where provider = 'elevenlabs'", Long.class))
                .as("không phát sinh ký tự ElevenLabs")
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select coalesce(sum(chars), 0) from tts_usage where provider = 'edge'", Long.class))
                .isPositive();
        assertThat(logs.list)
                .as("cảnh báo fallback có jobId")
                .anyMatch(e -> e.getFormattedMessage().contains("hết quota")
                        && e.getMDCPropertyMap().containsKey("jobId"));

        // Thả lại cùng file → duplicate, không job mới, không upload thêm
        Files.copy(SAMPLE, inboxDir.resolve("d-again.json"));
        assertThat(inbox.scan()).isEqualTo(1);
        assertThat(jobs.count()).isEqualTo(3);
        assertThat(names(props.dirs().duplicate())).anyMatch(n -> n.startsWith("d-again"));
        assertThat(worker.runOnce()).isFalse();
        YOUTUBE.verify(2, postRequestedFor(urlPathEqualTo("/upload/youtube/v3/videos")));
    }

    /**
     * sample-all-scenes với topicId khác, accent tiêu đề và thuật ngữ định nghĩa toàn chữ "W" đúng giới hạn ký tự
     * của validator: hợp lệ về dữ liệu nhưng không vừa khung khi render.
     */
    private static String overflowingScript() throws IOException {
        ObjectNode root = (ObjectNode) new JsonMapper().readTree(Files.readString(SAMPLE, StandardCharsets.UTF_8));
        root.put("topicId", BROKEN_TOPIC);
        for (var scene : root.path("scenes")) {
            ObjectNode s = (ObjectNode) scene;
            switch (s.path("type").asString()) {
                case "title" -> {
                    s.put("accent", "W".repeat(24));
                    s.put("titleRest", "WWWW");
                }
                case "definition" -> s.put("term", "W".repeat(20));
                default -> {}
            }
        }
        return root.toString();
    }

    /** Lịch sử attempt trong DB: đúng 4 bước, mỗi bước 1 lần SUCCESS, có thời lượng. */
    private void assertAttempts(VideoJob job) {
        List<String> rows = jdbc.queryForList(
                "select step || ':' || attempt_no || ':' || outcome from job_attempt where job_id = ?"
                        + " and duration_ms is not null order by started_at, id",
                String.class,
                job.getId());
        assertThat(rows)
                .containsExactly("RENDER:1:SUCCESS", "VOICE:1:SUCCESS", "ASSEMBLE:1:SUCCESS", "UPLOAD:1:SUCCESS");
    }

    /** Mọi log của pipeline cho job này mang MDC {@code jobId}; mỗi bước có dòng "Chạy bước". */
    private void assertJobLogs(VideoJob job) {
        String id = job.getId().toString();
        List<ILoggingEvent> mine = logs.list.stream()
                .filter(e -> id.equals(e.getMDCPropertyMap().get("jobId")))
                .toList();
        assertThat(mine.stream()
                        .filter(e -> e.getLoggerName().endsWith("PipelineWorker"))
                        .map(ILoggingEvent::getFormattedMessage)
                        .filter(m -> m.startsWith("Chạy bước")))
                .hasSize(4);
        assertThat(mine.stream().map(ILoggingEvent::getLoggerName))
                .as("log của các bước voice/video/youtube có jobId")
                .anyMatch(n -> n.contains(".voice."))
                .anyMatch(n -> n.contains(".video."))
                .anyMatch(n -> n.contains(".youtube."));
        assertThat(logs.list.stream()
                        .filter(e -> e.getLoggerName()
                                .matches("com\\.tuan\\.docreel\\.(pipeline|render|voice|video|youtube)\\..*"))
                        .filter(e -> !e.getMDCPropertyMap().containsKey("jobId")))
                .as("log pipeline không có jobId thì phải nêu id trong nội dung")
                .allMatch(e -> mentionsJobId(e.getFormattedMessage()));
    }

    private boolean mentionsJobId(String message) {
        return jobs.findAll().stream().anyMatch(j -> message.contains(j.getId().toString()));
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
                .isCloseTo(audioSeconds, within(FRAME));
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
     * Mỗi slide bắt đầu đúng lúc audio của nó: tại mốc cộng dồn audio +2 frame, khung hình giống PNG của slide đó hơn
     * PNG của slide trước; tại -2 frame thì ngược lại (SSIM). Lấy ±2 frame chứ không ±1 vì chuyển cảnh xfade trộn
     * hai slide ngay quanh ranh giới, khung đúng mốc là ảnh lai. Scene-detect ({@code select=gt(scene,0.3)}) không
     * dùng được vì các slide cùng nền tối khác nhau rất ít, điểm scene dưới 0,3.
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
                assertThat(ssim(mp4, start + 2 * FRAME, ownPng))
                        .as("%.3fs: đã sang slide %s", start + 2 * FRAME, own)
                        .isGreaterThan(ssim(mp4, start + 2 * FRAME, prevPng));
                assertThat(ssim(mp4, start - 2 * FRAME, prevPng))
                        .as("%.3fs: còn ở slide %s", start - 2 * FRAME, prev)
                        .isGreaterThan(ssim(mp4, start - 2 * FRAME, ownPng));
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
