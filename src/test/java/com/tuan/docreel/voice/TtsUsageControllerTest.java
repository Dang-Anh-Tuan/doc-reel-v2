package com.tuan.docreel.voice;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** P13b: {@code GET /api/tts/usage} lấy từ ElevenLabs {@code /v1/user/subscription} (WireMock), cache 60 s. */
class TtsUsageControllerTest {

    private static final String KEY = "test-key-SECRET";

    @TempDir
    Path root;

    WireMockServer wm;
    final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-25T10:00:00Z"));

    @BeforeEach
    void start() {
        wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wm.start();
    }

    @AfterEach
    void stop() {
        if (wm.isRunning()) {
            wm.stop();
        }
    }

    private MockMvc mvc(String apiKey) {
        var props = VoiceFixtures.props(root, URI.create(wm.baseUrl()), apiKey, "edge-tts", 0);
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        var service = new TtsUsageService(new ElevenLabsProvider(props), clock);
        return MockMvcBuilders.standaloneSetup(new TtsUsageController(service)).build();
    }

    private void stubSubscription(int status, String body) {
        wm.stubFor(get(urlPathEqualTo("/v1/user/subscription"))
                .withHeader("xi-api-key", equalTo(KEY))
                .willReturn(aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }

    @Test
    void returnsUsedLimitRemainingAndResetFromSubscription() throws Exception {
        stubSubscription(200, """
                {"tier":"starter","character_count":4200,"character_limit":30000,
                 "next_character_count_reset_unix":1767225600}""");
        MockMvc mvc = mvc(KEY);

        mvc.perform(MockMvcRequestBuilders.get("/api/tts/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.provider").value("ELEVENLABS"))
                .andExpect(jsonPath("$.used").value(4200))
                .andExpect(jsonPath("$.limit").value(30000))
                .andExpect(jsonPath("$.remaining").value(25800))
                .andExpect(jsonPath("$.resetAt").value("2026-01-01T00:00:00Z"))
                .andExpect(content().string(not(containsString(KEY))));

        // trong 60 s: dùng cache, không gọi lại ElevenLabs
        now.set(now.get().plusSeconds(59));
        mvc.perform(MockMvcRequestBuilders.get("/api/tts/usage"))
                .andExpect(jsonPath("$.used").value(4200));
        wm.verify(1, getRequestedFor(urlPathEqualTo("/v1/user/subscription")));

        // quá 60 s → gọi lại
        now.set(now.get().plusSeconds(2));
        mvc.perform(MockMvcRequestBuilders.get("/api/tts/usage")).andExpect(status().isOk());
        wm.verify(2, getRequestedFor(urlPathEqualTo("/v1/user/subscription")));
    }

    @Test
    void withoutApiKeyIsUnavailableNot500() throws Exception {
        mvc("").perform(MockMvcRequestBuilders.get("/api/tts/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.provider").value("ELEVENLABS"))
                .andExpect(jsonPath("$.reason").value(containsString("API key")))
                .andExpect(jsonPath("$.used").doesNotExist());
        wm.verify(0, getRequestedFor(urlPathEqualTo("/v1/user/subscription")));
    }

    @Test
    void providerErrorIsUnavailableWithShortReasonAndNoKey() throws Exception {
        stubSubscription(401, "{\"detail\":{\"status\":\"invalid_api_key\",\"message\":\"Invalid API key\"}}");

        mvc(KEY).perform(MockMvcRequestBuilders.get("/api/tts/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value(containsString("401")))
                .andExpect(content().string(not(containsString(KEY))));
    }

    @Test
    void networkErrorIsUnavailable() throws Exception {
        MockMvc mvc = mvc(KEY);
        wm.stop(); // cổng đóng → lỗi mạng

        mvc.perform(MockMvcRequestBuilders.get("/api/tts/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(content().string(not(containsString(KEY))));
    }
}
