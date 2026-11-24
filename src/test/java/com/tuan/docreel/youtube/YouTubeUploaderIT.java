package com.tuan.docreel.youtube;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YouTubeUploaderIT {

    private static final String INIT = "/upload/youtube/v3/videos";

    @TempDir
    Path dir;

    WireMockServer server;

    @BeforeEach
    void start() {
        server = YouTubeTestSupport.startServer();
        server.stubFor(post(urlPathEqualTo("/token"))
                .willReturn(aResponse().withBody("{\"access_token\":\"at-1\",\"expires_in\":3600}")));
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    private void stubUploadOk() {
        String session = "http://localhost:" + server.port() + "/session/abc";
        server.stubFor(post(urlPathEqualTo(INIT))
                .withQueryParam("uploadType", equalTo("resumable"))
                .withHeader("Authorization", equalTo("Bearer at-1"))
                .withRequestBody(matchingJsonPath("$.status[?(@.privacyStatus == 'private')]"))
                .willReturn(aResponse().withStatus(200).withHeader("Location", session)));
        server.stubFor(put(urlPathEqualTo("/session/abc"))
                .willReturn(aResponse().withStatus(200).withBody("{\"id\":\"vid123\"}")));
    }

    @Test
    void resumableUploadSucceedsAndReturnsId() throws IOException {
        stubUploadOk();
        String id = YouTubeTestSupport.uploader(server, dir)
                .upload(YouTubeTestSupport.fakeMp4(dir), YouTubeMetadataTest.script("Rules of Hooks", List.of()));
        assertThat(id).isEqualTo("vid123");
        server.verify(1, postRequestedFor(urlPathEqualTo(INIT)).withRequestBody(containing("Rules of Hooks")));
    }

    @Test
    void serverErrorIsRetried() throws IOException {
        String session = "http://localhost:" + server.port() + "/session/abc";
        server.stubFor(post(urlPathEqualTo(INIT))
                .inScenario("5xx")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("second"));
        server.stubFor(post(urlPathEqualTo(INIT))
                .inScenario("5xx")
                .whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(200).withHeader("Location", session)));
        server.stubFor(put(urlPathEqualTo("/session/abc"))
                .willReturn(aResponse().withStatus(200).withBody("{\"id\":\"vid5xx\"}")));

        String id = YouTubeTestSupport.uploader(server, dir)
                .upload(YouTubeTestSupport.fakeMp4(dir), YouTubeMetadataTest.script("T", List.of()));
        assertThat(id).isEqualTo("vid5xx");
        server.verify(2, postRequestedFor(urlPathEqualTo(INIT)));
    }

    @Test
    void persistentServerErrorGivesUpAfterMaxAttempts() throws IOException {
        server.stubFor(post(urlPathEqualTo(INIT)).willReturn(aResponse().withStatus(500)));
        YouTubeUploader uploader = YouTubeTestSupport.uploader(server, dir);
        Path mp4 = YouTubeTestSupport.fakeMp4(dir);
        assertThatThrownBy(() -> uploader.upload(mp4, YouTubeMetadataTest.script("T", List.of())))
                .isInstanceOf(YouTubeApiException.class);
        server.verify(3, postRequestedFor(urlPathEqualTo(INIT)));
    }

    @Test
    void unauthorizedRefreshesTokenOnceThenRetries() throws IOException {
        String session = "http://localhost:" + server.port() + "/session/abc";
        server.stubFor(post(urlPathEqualTo("/token")).willReturn(aResponse().withBody("{\"access_token\":\"at-1\"}")));
        server.stubFor(post(urlPathEqualTo(INIT))
                .withHeader("Authorization", equalTo("Bearer at-1"))
                .inScenario("401")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(401))
                .willSetStateTo("refreshed"));
        server.stubFor(post(urlPathEqualTo(INIT))
                .inScenario("401")
                .whenScenarioStateIs("refreshed")
                .willReturn(aResponse().withStatus(200).withHeader("Location", session)));
        server.stubFor(put(urlPathEqualTo("/session/abc"))
                .willReturn(aResponse().withStatus(200).withBody("{\"id\":\"vid401\"}")));

        String id = YouTubeTestSupport.uploader(server, dir)
                .upload(YouTubeTestSupport.fakeMp4(dir), YouTubeMetadataTest.script("T", List.of()));
        assertThat(id).isEqualTo("vid401");
        // 1 lần lấy token đầu + đúng 1 lần làm mới sau 401
        server.verify(2, postRequestedFor(urlPathEqualTo("/token")));
    }

    @Test
    void unauthorizedTwiceFailsWithoutLooping() throws IOException {
        server.stubFor(post(urlPathEqualTo(INIT)).willReturn(aResponse().withStatus(401)));
        YouTubeUploader uploader = YouTubeTestSupport.uploader(server, dir);
        Path mp4 = YouTubeTestSupport.fakeMp4(dir);
        assertThatThrownBy(() -> uploader.upload(mp4, YouTubeMetadataTest.script("T", List.of())))
                .isInstanceOf(YouTubeApiException.class)
                .extracting(e -> ((YouTubeApiException) e).status())
                .isEqualTo(401);
        server.verify(2, postRequestedFor(urlPathEqualTo("/token")));
        server.verify(2, postRequestedFor(urlPathEqualTo(INIT)));
    }

    @Test
    void authCodeExchangeStoresRefreshToken() {
        server.stubFor(post(urlPathEqualTo("/token"))
                .withRequestBody(containing("grant_type=authorization_code"))
                .willReturn(aResponse().withBody("{\"access_token\":\"a\",\"refresh_token\":\"r-new\"}")));
        String base = "http://localhost:" + server.port();
        YouTubeProperties props = new YouTubeProperties(
                base,
                base + "/token",
                base + "/auth",
                "c",
                "s",
                "pp",
                dir.resolve("x.enc"),
                3,
                java.time.Duration.ZERO);
        OAuthTokenStore store = new OAuthTokenStore(props);
        new AccessTokenProvider(
                        props,
                        store,
                        tools.jackson.databind.json.JsonMapper.builder().build())
                .exchangeCode("code-1", "http://localhost:1/cb");
        assertThat(store.load()).contains("r-new");
    }
}
