package com.tuan.docreel.youtube;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YouTubePlaylistIT {

    @TempDir
    Path dir;

    WireMockServer server;

    @BeforeEach
    void start() {
        server = YouTubeTestSupport.startServer();
        server.stubFor(post(urlPathEqualTo("/token")).willReturn(aResponse().withBody("{\"access_token\":\"at-1\"}")));
        server.stubFor(post(urlPathEqualTo("/youtube/v3/playlistItems"))
                .willReturn(aResponse().withBody("{\"id\":\"item1\"}")));
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void createsPlaylistWhenMissing() {
        server.stubFor(get(urlPathEqualTo("/youtube/v3/playlists"))
                .willReturn(
                        aResponse().withBody("{\"items\":[{\"id\":\"PL_other\",\"snippet\":{\"title\":\"Java\"}}]}")));
        server.stubFor(post(urlPathEqualTo("/youtube/v3/playlists"))
                .willReturn(aResponse().withBody("{\"id\":\"PL_new\"}")));

        String id = YouTubeTestSupport.uploader(server, dir).addToPlaylist("React", "vid1");

        assertThat(id).isEqualTo("PL_new");
        server.verify(
                1, postRequestedFor(urlPathEqualTo("/youtube/v3/playlists")).withRequestBody(containing("React")));
        server.verify(postRequestedFor(urlPathEqualTo("/youtube/v3/playlistItems"))
                .withRequestBody(containing("PL_new"))
                .withRequestBody(containing("vid1")));
    }

    @Test
    void reusesExistingPlaylistFoundOnSecondPage() {
        server.stubFor(get(urlPathEqualTo("/youtube/v3/playlists"))
                .willReturn(aResponse().withBody("{\"items\":[],\"nextPageToken\":\"p2\"}")));
        server.stubFor(get(urlPathEqualTo("/youtube/v3/playlists"))
                .withQueryParam("pageToken", com.github.tomakehurst.wiremock.client.WireMock.equalTo("p2"))
                .willReturn(
                        aResponse().withBody("{\"items\":[{\"id\":\"PL_react\",\"snippet\":{\"title\":\"React\"}}]}")));

        String id = YouTubeTestSupport.uploader(server, dir).addToPlaylist("React", "vid1");

        assertThat(id).isEqualTo("PL_react");
        server.verify(0, postRequestedFor(urlPathEqualTo("/youtube/v3/playlists")));
        server.verify(1, postRequestedFor(urlPathEqualTo("/youtube/v3/playlistItems")));
    }

    @Test
    void conflict409OnPlaylistItemIsRetriedThenSucceeds() {
        server.stubFor(get(urlPathEqualTo("/youtube/v3/playlists"))
                .willReturn(
                        aResponse().withBody("{\"items\":[{\"id\":\"PL_java\",\"snippet\":{\"title\":\"Java\"}}]}")));
        server.stubFor(post(urlPathEqualTo("/youtube/v3/playlistItems"))
                .inScenario("409-roi-ok")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(409).withBody("{\"error\":{\"code\":409}}"))
                .willSetStateTo("ok"));
        server.stubFor(post(urlPathEqualTo("/youtube/v3/playlistItems"))
                .inScenario("409-roi-ok")
                .whenScenarioStateIs("ok")
                .willReturn(aResponse().withBody("{\"id\":\"item1\"}")));

        String id = YouTubeTestSupport.uploader(server, dir).addToPlaylist("Java", "vid1");

        assertThat(id).isEqualTo("PL_java");
        server.verify(2, postRequestedFor(urlPathEqualTo("/youtube/v3/playlistItems")));
    }
}
