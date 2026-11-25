package com.tuan.docreel.youtube;

import com.tuan.docreel.script.VideoScript;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Upload video lên YouTube (resumable, luôn private) và thêm vào playlist theo {@code track}.
 *
 * <p>Mọi request đi qua {@link #send}: 401 → làm mới access token đúng 1 lần rồi thử lại; 5xx / lỗi mạng → thử
 * lại tối đa {@code maxAttempts}; 4xx khác → lỗi ngay.
 */
@Component
public class YouTubeUploader {

    private static final Logger log = LoggerFactory.getLogger(YouTubeUploader.class);
    private static final String CATEGORY_EDUCATION = "27";

    private final YouTubeProperties props;
    private final AccessTokenProvider tokens;
    private final JsonMapper mapper;
    private final HttpClient http = HttpClient.newHttpClient();
    private final Map<String, String> playlistCache = new ConcurrentHashMap<>();

    public YouTubeUploader(YouTubeProperties props, AccessTokenProvider tokens, JsonMapper mapper) {
        this.props = props;
        this.tokens = tokens;
        this.mapper = mapper;
    }

    /** @return youtube video id */
    public String upload(Path mp4, VideoScript script) {
        YouTubeMetadata meta = YouTubeMetadata.from(script);
        long size;
        try {
            size = Files.size(mp4);
        } catch (IOException e) {
            throw new YouTubeApiException("Không đọc được file video " + mp4, e);
        }
        String metaJson = mapper.writeValueAsString(Map.of(
                "snippet",
                Map.of(
                        "title", meta.title(),
                        "description", meta.description(),
                        "tags", meta.tags(),
                        "categoryId", CATEGORY_EDUCATION),
                "status",
                Map.of("privacyStatus", meta.privacyStatus(), "selfDeclaredMadeForKids", false)));

        HttpResponse<String> init = send(token -> HttpRequest.newBuilder(
                        uri("/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("X-Upload-Content-Type", "video/mp4")
                .header("X-Upload-Content-Length", Long.toString(size))
                .POST(HttpRequest.BodyPublishers.ofString(metaJson))
                .build());
        String session = init.headers()
                .firstValue("Location")
                .orElseThrow(() -> new YouTubeApiException(502, "YouTube không trả Location của phiên upload"));

        HttpResponse<String> done = send(token -> {
            try {
                return HttpRequest.newBuilder(URI.create(session))
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "video/mp4")
                        .PUT(HttpRequest.BodyPublishers.ofFile(mp4))
                        .build();
            } catch (IOException e) {
                throw new YouTubeApiException("Không mở được file video " + mp4, e);
            }
        });
        String id = mapper.readTree(done.body()).path("id").asString("");
        if (id.isBlank()) {
            throw new YouTubeApiException(502, "YouTube không trả id video");
        }
        log.info("Đã upload video lên YouTube (private): {}", id);
        return id;
    }

    /** Thêm video vào playlist tên = {@code track}, tạo playlist (private) nếu chưa có. @return playlist id */
    public String addToPlaylist(String track, String videoId) {
        String playlistId = playlistCache.computeIfAbsent(track, this::findOrCreatePlaylist);
        String body = mapper.writeValueAsString(Map.of(
                "snippet",
                Map.of("playlistId", playlistId, "resourceId", Map.of("kind", "youtube#video", "videoId", videoId))));
        send(token -> jsonPost("/youtube/v3/playlistItems?part=snippet", token, body));
        log.info("Đã thêm video {} vào playlist {}", videoId, playlistId);
        return playlistId;
    }

    private String findOrCreatePlaylist(String title) {
        String pageToken = null;
        do {
            String path = "/youtube/v3/playlists?part=snippet&mine=true&maxResults=50"
                    + (pageToken == null ? "" : "&pageToken=" + URLEncoder.encode(pageToken, StandardCharsets.UTF_8));
            JsonNode page = mapper.readTree(send(token -> HttpRequest.newBuilder(uri(path))
                            .header("Authorization", "Bearer " + token)
                            .GET()
                            .build())
                    .body());
            for (JsonNode item : page.path("items")) {
                if (title.equals(item.path("snippet").path("title").asString(""))) {
                    return item.path("id").asString();
                }
            }
            pageToken = page.path("nextPageToken").asString(null);
        } while (pageToken != null);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("snippet", Map.of("title", title));
        body.put("status", Map.of("privacyStatus", YouTubeMetadata.PRIVATE));
        String json = mapper.writeValueAsString(body);
        String id = mapper.readTree(send(token -> jsonPost("/youtube/v3/playlists?part=snippet,status", token, json))
                        .body())
                .path("id")
                .asString("");
        if (id.isBlank()) {
            throw new YouTubeApiException(502, "YouTube không trả id playlist");
        }
        log.info("Đã tạo playlist '{}'", title);
        return id;
    }

    private HttpRequest jsonPost(String path, String token, String json) {
        return HttpRequest.newBuilder(uri(path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
    }

    private URI uri(String path) {
        return URI.create(props.apiBaseUrl() + path);
    }

    private HttpResponse<String> send(Function<String, HttpRequest> requestForToken) {
        boolean refreshed = false;
        YouTubeApiException last = null;
        for (int attempt = 1; attempt <= Math.max(1, props.maxAttempts()); attempt++) {
            try {
                HttpResponse<String> res =
                        http.send(requestForToken.apply(tokens.accessToken()), HttpResponse.BodyHandlers.ofString());
                int status = res.statusCode();
                if (status / 100 == 2) {
                    return res;
                }
                if (status == 401 && !refreshed) {
                    refreshed = true;
                    log.warn("YouTube trả 401, làm mới token rồi thử lại");
                    tokens.refresh();
                    continue;
                }
                last = new YouTubeApiException(status, "YouTube trả HTTP " + status);
                // 409 (ABORTED/conflict) hay gặp thoáng qua ở playlistItems.insert ngay sau upload → thử lại như 5xx
                if (status / 100 != 5 && status != 409) {
                    throw last;
                }
                log.warn("YouTube lỗi {} (lần {}/{})", status, attempt, props.maxAttempts());
            } catch (IOException e) {
                last = new YouTubeApiException("Lỗi mạng khi gọi YouTube", e);
                log.warn("Lỗi mạng khi gọi YouTube (lần {}/{})", attempt, props.maxAttempts());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new YouTubeApiException("Bị ngắt khi gọi YouTube", e);
            }
            backoff(attempt);
        }
        throw last != null ? last : new YouTubeApiException(401, "YouTube từ chối token sau khi làm mới");
    }

    private void backoff(int attempt) {
        Duration d = props.retryBackoff().multipliedBy(attempt);
        if (d.isZero()) {
            return;
        }
        try {
            Thread.sleep(d);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
