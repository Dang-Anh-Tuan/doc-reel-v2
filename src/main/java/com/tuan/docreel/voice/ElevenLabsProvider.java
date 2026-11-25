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
    /** Đọc quota là lời gọi nhỏ, có thể chạy trên thread HTTP ({@code /api/tts/usage}) → không chờ lâu như TTS. */
    static final Duration SUBSCRIPTION_TIMEOUT = Duration.ofSeconds(5);

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
                send(HttpRequest.newBuilder(url("/v1/user/subscription")).GET(), SUBSCRIPTION_TIMEOUT);
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
        ElevenTuning t = settings.eleven();
        return String.join(
                "|",
                model(settings),
                settings.elevenVoiceId(),
                String.valueOf(settings.elevenSpeed()),
                "lang=" + blankToEmpty(t.languageCode()),
                "stab=" + t.stability(),
                "sim=" + t.similarityBoost(),
                "style=" + t.style(),
                "boost=" + t.speakerBoost(),
                "norm=" + blankToEmpty(t.textNormalization()),
                "ctx=" + t.sendContext(),
                "lex=" + t.applyLexicon());
    }

    @Override
    public boolean appliesLexicon(VoiceSettings settings) {
        return settings.eleven().applyLexicon();
    }

    /** Body JSON của {@code POST /v1/text-to-speech/{voice}}: field null/rỗng bị bỏ (model không hỗ trợ → không 400). */
    Map<String, Object> requestBody(String sentence, String previous, String next, VoiceSettings settings) {
        ElevenTuning t = settings.eleven();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", sentence);
        body.put("model_id", model(settings));
        putIfPresent(body, "language_code", t.languageCode());
        Map<String, Object> voice = new LinkedHashMap<>();
        putIfPresent(voice, "stability", t.stability());
        putIfPresent(voice, "similarity_boost", t.similarityBoost());
        putIfPresent(voice, "style", t.style());
        putIfPresent(voice, "use_speaker_boost", t.speakerBoost());
        voice.put("speed", settings.elevenSpeed());
        body.put("voice_settings", voice);
        putIfPresent(body, "apply_text_normalization", t.textNormalization());
        if (t.sendContext()) {
            putIfPresent(body, "previous_text", previous);
            putIfPresent(body, "next_text", next);
        }
        return body;
    }

    @Override
    public byte[] synthesize(String sentence, String previous, String next, VoiceSettings settings) {
        Map<String, Object> body = requestBody(sentence, previous, next, settings);
        String voice = URLEncoder.encode(settings.elevenVoiceId(), StandardCharsets.UTF_8);
        HttpRequest.Builder request = HttpRequest.newBuilder(
                        url("/v1/text-to-speech/" + voice + "?output_format=mp3_44100_128"))
                .header("Content-Type", "application/json")
                .header("Accept", "audio/mpeg")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8));
        HttpResponse<byte[]> response = send(request, REQUEST_TIMEOUT);
        if (response.statusCode() != 200) {
            throw failure(response);
        }
        if (response.body().length == 0) {
            throw new TtsTransientException("ElevenLabs trả audio rỗng");
        }
        return response.body();
    }

    /** Model đã chụp trong job; job cũ không có → cấu hình hiện hành. */
    private String model(VoiceSettings settings) {
        String m = settings.eleven().model();
        return m == null || m.isBlank() ? config.model() : m;
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            return;
        }
        map.put(key, value);
    }

    private static String blankToEmpty(String s) {
        return s == null ? "" : s.strip();
    }

    private URI url(String path) {
        String base = config.baseUrl().toString();
        return URI.create(base.endsWith("/") ? base.substring(0, base.length() - 1) + path : base + path);
    }

    private HttpResponse<byte[]> send(HttpRequest.Builder builder, Duration timeout) {
        HttpRequest request =
                builder.header("xi-api-key", config.apiKey()).timeout(timeout).build();
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
