package com.tuan.docreel.youtube;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Đổi refresh token (đĩa) lấy access token, cache trong bộ nhớ. */
@Component
public class AccessTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(AccessTokenProvider.class);

    private final YouTubeProperties props;
    private final OAuthTokenStore store;
    private final JsonMapper mapper;
    private final HttpClient http = HttpClient.newHttpClient();
    private String cached;

    public AccessTokenProvider(YouTubeProperties props, OAuthTokenStore store, JsonMapper mapper) {
        this.props = props;
        this.store = store;
        this.mapper = mapper;
    }

    public synchronized String accessToken() {
        return cached != null ? cached : refresh();
    }

    /** Luôn gọi Google lấy access token mới. */
    public synchronized String refresh() {
        String refreshToken = store.load()
                .orElseThrow(() -> new YouTubeApiException(401, "Chưa cấp quyền YouTube: chạy --youtube-auth"));
        JsonNode json = postForm(Map.of(
                "client_id",
                nullToEmpty(props.clientId()),
                "client_secret",
                nullToEmpty(props.clientSecret()),
                "refresh_token",
                refreshToken,
                "grant_type",
                "refresh_token"));
        String token = json.path("access_token").asString("");
        if (token.isBlank()) {
            throw new YouTubeApiException(502, "Google không trả access_token");
        }
        cached = token;
        log.info("Đã làm mới access token YouTube");
        return token;
    }

    /** Đổi authorization code (lần cấp quyền đầu) lấy refresh token và lưu lại. */
    public synchronized void exchangeCode(String code, String redirectUri) {
        JsonNode json = postForm(Map.of(
                "client_id",
                nullToEmpty(props.clientId()),
                "client_secret",
                nullToEmpty(props.clientSecret()),
                "code",
                code,
                "redirect_uri",
                redirectUri,
                "grant_type",
                "authorization_code"));
        String refresh = json.path("refresh_token").asString("");
        if (refresh.isBlank()) {
            throw new YouTubeApiException(502, "Google không trả refresh_token (thử lại với prompt=consent)");
        }
        store.save(refresh);
        cached = json.path("access_token").asString(null);
    }

    private JsonNode postForm(Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        HttpRequest req = HttpRequest.newBuilder(URI.create(props.tokenUrl()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                // Không log body: có thể chứa chi tiết nhạy cảm.
                throw new YouTubeApiException(
                        res.statusCode(), "Google từ chối token request: HTTP " + res.statusCode());
            }
            return mapper.readTree(res.body());
        } catch (IOException e) {
            throw new YouTubeApiException("Không gọi được Google OAuth", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new YouTubeApiException("Bị ngắt khi gọi Google OAuth", e);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
