package com.tuan.docreel.voice;

import com.tuan.docreel.common.AppProperties;
import com.tuan.docreel.common.AppProperties.TtsProviderName;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** ElevenLabs REST API. API key chỉ nằm trong header, không bao giờ log. */
@Component
public class ElevenLabsProvider implements TtsProvider {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    private final AppProperties.ElevenLabs config;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public ElevenLabsProvider(AppProperties props) {
        this.config = props.tts().elevenlabs();
    }

    @Override
    public TtsProviderName name() {
        return TtsProviderName.ELEVENLABS;
    }

    @Override
    public boolean isConfigured() {
        return config.hasApiKey();
    }

    @Override
    public long remainingChars() {
        if (!isConfigured()) {
            return 0;
        }
        Subscription sub = subscription();
        return sub.limit() - sub.used();
    }

    /**
     * Quota tháng hiện tại từ {@code GET /v1/user/subscription}.
     *
     * @param used {@code character_count} (credit đã dùng trong chu kỳ)
     * @param limit {@code character_limit}
     * @param resetAt {@code next_character_count_reset_unix}; null nếu API không trả
     */
    public record Subscription(long used, long limit, Instant resetAt) {}

    /** Gọi ElevenLabs; lỗi HTTP/mạng ném {@link TtsException} (message không chứa key). */
    public Subscription subscription() {
        HttpResponse<byte[]> response =
                send(HttpRequest.newBuilder(url("/v1/user/subscription")).GET());
        if (response.statusCode() != 200) {
            throw failure(response);
        }
        JsonNode json = MAPPER.readTree(response.body());
        JsonNode reset = json.path("next_character_count_reset_unix");
        return new Subscription(
                json.path("character_count").asLong(),
                json.path("character_limit").asLong(),
                reset.isNumber() && reset.asLong() > 0 ? Instant.ofEpochSecond(reset.asLong()) : null);
    }

    @Override
    public String voiceKey(VoiceSettings settings) {
        return config.model() + "|" + settings.elevenVoiceId() + "|" + settings.elevenSpeed();
    }

    @Override
    public byte[] synthesize(String sentence, String previous, String next, VoiceSettings settings) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", sentence);
        body.put("model_id", config.model());
        body.put("voice_settings", Map.of("speed", settings.elevenSpeed()));
        if (previous != null) {
            body.put("previous_text", previous);
        }
        if (next != null) {
            body.put("next_text", next);
        }
        String voice = URLEncoder.encode(settings.elevenVoiceId(), StandardCharsets.UTF_8);
        HttpRequest.Builder request = HttpRequest.newBuilder(
                        url("/v1/text-to-speech/" + voice + "?output_format=mp3_44100_128"))
                .header("Content-Type", "application/json")
                .header("Accept", "audio/mpeg")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8));
        HttpResponse<byte[]> response = send(request);
        if (response.statusCode() != 200) {
            throw failure(response);
        }
        if (response.body().length == 0) {
            throw new TtsTransientException("ElevenLabs trả audio rỗng");
        }
        return response.body();
    }

    private URI url(String path) {
        String base = config.baseUrl().toString();
        return URI.create(base.endsWith("/") ? base.substring(0, base.length() - 1) + path : base + path);
    }

    private HttpResponse<byte[]> send(HttpRequest.Builder builder) {
        HttpRequest request = builder.header("xi-api-key", config.apiKey())
                .timeout(REQUEST_TIMEOUT)
                .build();
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new TtsTransientException("Lỗi mạng khi gọi ElevenLabs: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TtsTransientException("Bị ngắt khi gọi ElevenLabs", e);
        }
    }

    private static TtsException failure(HttpResponse<byte[]> response) {
        int status = response.statusCode();
        String body = new String(response.body(), StandardCharsets.UTF_8);
        if (status == 429 || (status == 401 && body.contains("quota_exceeded"))) {
            return new TtsQuotaExceededException("ElevenLabs hết quota (HTTP " + status + ")");
        }
        if (status >= 500) {
            return new TtsTransientException("ElevenLabs lỗi HTTP " + status);
        }
        return new TtsException("ElevenLabs từ chối: HTTP " + status);
    }
}
