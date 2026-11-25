package com.tuan.docreel.voice;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import com.tuan.docreel.common.AppProperties;
import com.tuan.docreel.common.AppProperties.TtsProviderName;
import com.tuan.docreel.common.ProcessRequest;
import com.tuan.docreel.common.ProcessResult;
import com.tuan.docreel.common.ProcessRunner;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ElevenLabs giả bằng WireMock: quota giữa job, retry dùng cache, 5xx không đổi provider. */
class TtsFallbackIT {

    private static final String TTS_PATH = "/v1/text-to-speech/voice1";
    private static final byte ELEVEN = 0x11;
    private static final byte EDGE = 0x22;

    @TempDir
    Path root;

    WireMockServer wm;
    List<String> usage = new ArrayList<>();
    List<String> edgeSentences = new ArrayList<>();

    @BeforeEach
    void start() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
        wm.stubFor(get(urlPathEqualTo("/v1/user/subscription"))
                .withHeader("xi-api-key", equalTo("test-key"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"character_limit\":10000,\"character_count\":100}")));
    }

    @AfterEach
    void stop() {
        wm.stop();
    }

    /** edge-tts giả chạy trong process JVM hiện tại: ghi audio đánh dấu 0x22. */
    private ProcessRunner inProcessEdge() {
        return new ProcessRunner() {
            @Override
            public ProcessResult run(ProcessRequest request) {
                List<String> c = request.command();
                try {
                    String text = Files.readString(Path.of(c.get(c.indexOf("--file") + 1)));
                    edgeSentences.add(text);
                    Files.write(Path.of(c.get(c.indexOf("--write-media") + 1)), FakeAudioTool.audioFor(EDGE, text));
                } catch (java.io.IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
                return new ProcessResult(0, "", "", Duration.ZERO);
            }
        };
    }

    private TtsService service() {
        AppProperties props = VoiceFixtures.props(root, URI.create(wm.baseUrl()), "test-key", "edge-tts", 0);
        return new TtsService(
                List.of(new ElevenLabsProvider(props), new EdgeTtsProvider(inProcessEdge(), props)),
                new Lexicon(root.resolve("none.yaml"), false),
                new TtsCache(props),
                new FakeAudioTool(),
                (provider, chars) -> usage.add(provider + ":" + chars));
    }

    private void stubTts(String sentence, int status, String body) {
        wm.stubFor(post(urlPathEqualTo(TTS_PATH))
                .withRequestBody(matchingJsonPath("$.text", equalTo(sentence)))
                .withHeader("xi-api-key", equalTo("test-key"))
                .atPriority(1)
                .willReturn(aResponse()
                        .withStatus(status)
                        .withBody(status == 200 ? FakeAudioTool.audioFor(ELEVEN, sentence) : body.getBytes())));
    }

    private static final String SCRIPT_TEXT = "Câu một. Câu hai. Câu ba.";

    private VoiceResult voice(TtsService svc) {
        return svc.voice(
                VoiceFixtures.script(VoiceFixtures.title("s01", SCRIPT_TEXT)),
                root.resolve("work/j1"),
                VoiceFixtures.settings(),
                null);
    }

    @Test
    void quotaExceededMidJobDiscardsAudioAndRereadsEverythingWithEdge() throws Exception {
        stubTts("Câu một.", 200, "");
        stubTts("Câu hai.", 401, "{\"detail\":{\"status\":\"quota_exceeded\",\"message\":\"x\"}}");
        stubTts("Câu ba.", 200, "");

        VoiceResult result = voice(service());

        assertThat(result.provider()).isEqualTo(TtsProviderName.EDGE);
        assertThat(edgeSentences).containsExactly("Câu một.", "Câu hai.", "Câu ba."); // đọc lại TOÀN BỘ
        byte[] audio = Files.readAllBytes(result.audios().getFirst().file());
        assertThat(audio).contains(EDGE).doesNotContain(ELEVEN); // không trộn giọng
        wm.verify(2, postRequestedFor(urlPathEqualTo(TTS_PATH))); // "Câu ba." chưa bao giờ được gửi
        assertThat(usage).containsExactly("ELEVENLABS:8", "EDGE:8", "EDGE:8", "EDGE:7");
    }

    @Test
    void http429AlsoMeansQuota() {
        stubTts("Câu một.", 429, "{}");
        VoiceResult result = voice(service());
        assertThat(result.provider()).isEqualTo(TtsProviderName.EDGE);
    }

    @Test
    void sendsModelVoiceSettingsAndNeighbouringText() {
        stubTts("Câu một.", 200, "");
        stubTts("Câu hai.", 200, "");
        stubTts("Câu ba.", 200, "");
        VoiceResult result = voice(service());

        assertThat(result.provider()).isEqualTo(TtsProviderName.ELEVENLABS);
        // equalToJson chặt: không thừa field nào
        wm.verify(postRequestedFor(urlPathEqualTo(TTS_PATH)).withRequestBody(equalToJson("""
                        {"text":"Câu hai.","model_id":"eleven_flash_v2_5","language_code":"vi",
                         "voice_settings":{"stability":0.4,"similarity_boost":0.8,"style":0.2,
                                           "use_speaker_boost":true,"speed":1.0},
                         "apply_text_normalization":"on",
                         "previous_text":"Câu một.","next_text":"Câu ba."}
                        """)));
        // câu đầu không có previous_text (không gửi null)
        wm.verify(postRequestedFor(urlPathEqualTo(TTS_PATH)).withRequestBody(equalToJson("""
                        {"text":"Câu một.","model_id":"eleven_flash_v2_5","language_code":"vi",
                         "voice_settings":{"stability":0.4,"similarity_boost":0.8,"style":0.2,
                                           "use_speaker_boost":true,"speed":1.0},
                         "apply_text_normalization":"on","next_text":"Câu hai."}
                        """)));
    }

    @Test
    void emptyOrNullTuningIsLeftOutOfRequestAndContextCanBeDisabled() {
        stubTts("Câu một.", 200, "");
        stubTts("Câu hai.", 200, "");
        stubTts("Câu ba.", 200, "");
        VoiceSettings bare =
                VoiceFixtures.settings(new ElevenTuning("eleven_v3", "", null, null, null, null, "", false, false));

        VoiceResult result = service()
                .voice(
                        VoiceFixtures.script(VoiceFixtures.title("s01", SCRIPT_TEXT)),
                        root.resolve("work/j1"),
                        bare,
                        null);

        assertThat(result.provider()).isEqualTo(TtsProviderName.ELEVENLABS);
        wm.verify(postRequestedFor(urlPathEqualTo(TTS_PATH)).withRequestBody(equalToJson("""
                        {"text":"Câu hai.","model_id":"eleven_v3","voice_settings":{"speed":1.0}}
                        """)));
        wm.verify(0, postRequestedFor(urlPathEqualTo(TTS_PATH)).withRequestBody(matchingJsonPath("$.previous_text")));
        wm.verify(0, postRequestedFor(urlPathEqualTo(TTS_PATH)).withRequestBody(matchingJsonPath("$.language_code")));
    }

    @Test
    void elevenLabsGetsEnglishTermsVerbatimWhileEdgeGetsLexicon() throws Exception {
        Path lex = root.resolve("lexicon.yaml");
        Files.writeString(lex, "React: \"Rì-ách\"\nuseEffect: \"iu-ì-phếch\"\n");
        AppProperties props = VoiceFixtures.props(root, URI.create(wm.baseUrl()), "test-key", "edge-tts", 0);
        TtsService svc = new TtsService(
                List.of(new ElevenLabsProvider(props), new EdgeTtsProvider(inProcessEdge(), props)),
                new Lexicon(lex, false),
                new TtsCache(props),
                new FakeAudioTool(),
                (provider, chars) -> usage.add(provider + ":" + chars));
        String sentence = "React gọi useEffect sau khi render.";
        stubTts(sentence, 200, "");
        var script = VoiceFixtures.script(VoiceFixtures.title("s01", sentence));

        svc.voice(script, root.resolve("work/j1"), VoiceFixtures.settings(), null);
        wm.verify(
                1,
                postRequestedFor(urlPathEqualTo(TTS_PATH))
                        .withRequestBody(matchingJsonPath("$.text", equalTo(sentence))));
        assertThat(usage).containsExactly("ELEVENLABS:" + sentence.length()); // đếm text thực gửi (gốc)

        VoiceSettings edgeOnly =
                new VoiceSettings(TtsProviderName.EDGE, TtsProviderName.EDGE, "v", 1.0, "vi-VN-HoaiMyNeural", "+0%");
        svc.voice(script, root.resolve("work/j2"), edgeOnly, null);
        assertThat(edgeSentences).containsExactly("Rì-ách gọi iu-ì-phếch sau khi render.");
    }

    @Test
    void retryAfterServerErrorDoesNotCallApiAgainForCachedSentences() {
        stubTts("Câu một.", 200, "");
        stubTts("Câu hai.", 200, "");
        wm.stubFor(post(urlPathEqualTo(TTS_PATH))
                .withRequestBody(matchingJsonPath("$.text", equalTo("Câu ba.")))
                .inScenario("flaky")
                .whenScenarioStateIs(Scenario.STARTED)
                .atPriority(1)
                .willSetStateTo("recovered")
                .willReturn(aResponse().withStatus(503)));
        wm.stubFor(post(urlPathEqualTo(TTS_PATH))
                .withRequestBody(matchingJsonPath("$.text", equalTo("Câu ba.")))
                .inScenario("flaky")
                .whenScenarioStateIs("recovered")
                .atPriority(1)
                .willReturn(aResponse().withStatus(200).withBody(FakeAudioTool.audioFor(ELEVEN, "Câu ba."))));

        TtsService svc = service();
        // 5xx: retry của pipeline, không chuyển provider
        assertThatThrownBy(() -> voice(svc)).isInstanceOf(TtsTransientException.class);
        VoiceResult result = voice(svc);

        assertThat(result.provider()).isEqualTo(TtsProviderName.ELEVENLABS);
        wm.verify(
                1,
                postRequestedFor(urlPathEqualTo(TTS_PATH))
                        .withRequestBody(matchingJsonPath("$.text", equalTo("Câu một."))));
        wm.verify(
                1,
                postRequestedFor(urlPathEqualTo(TTS_PATH))
                        .withRequestBody(matchingJsonPath("$.text", equalTo("Câu hai."))));
        wm.verify(
                2,
                postRequestedFor(urlPathEqualTo(TTS_PATH))
                        .withRequestBody(matchingJsonPath("$.text", equalTo("Câu ba."))));
        assertThat(usage).containsExactly("ELEVENLABS:8", "ELEVENLABS:8", "ELEVENLABS:7");
    }

    @Test
    void insufficientQuotaGoesStraightToEdgeWithoutCallingTts() {
        wm.stubFor(get(urlPathEqualTo("/v1/user/subscription"))
                .atPriority(1)
                .willReturn(aResponse().withStatus(200).withBody("{\"character_limit\":100,\"character_count\":90}")));
        VoiceResult result = voice(service());
        assertThat(result.provider()).isEqualTo(TtsProviderName.EDGE);
        wm.verify(0, postRequestedFor(urlPathEqualTo(TTS_PATH)));
    }

    @Test
    void badApiKeyIsNotRetriedAsTransient() {
        wm.stubFor(post(urlPathEqualTo(TTS_PATH))
                .willReturn(aResponse().withStatus(401).withBody("{\"detail\":\"invalid\"}")));
        assertThatThrownBy(() -> voice(service()))
                .isInstanceOf(TtsException.class)
                .isNotInstanceOf(TtsTransientException.class)
                .isNotInstanceOf(TtsQuotaExceededException.class);
    }

    @Test
    void subscriptionFailureIsTransient() {
        wm.stubFor(get(urlPathEqualTo("/v1/user/subscription"))
                .atPriority(1)
                .willReturn(aResponse().withStatus(500)));
        assertThatThrownBy(() -> voice(service())).isInstanceOf(TtsTransientException.class);
    }

    @Test
    void networkErrorIsTransient() {
        TtsService svc = service();
        wm.stop();
        assertThatThrownBy(() -> voice(svc)).isInstanceOf(TtsTransientException.class);
    }
}
