package com.tuan.docreel.youtube;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import tools.jackson.databind.json.JsonMapper;

/** Dựng uploader trỏ về WireMock; token giả, không secret thật. */
final class YouTubeTestSupport {

    private YouTubeTestSupport() {}

    static WireMockServer startServer() {
        WireMockServer server =
                new WireMockServer(WireMockConfiguration.options().dynamicPort());
        server.start();
        return server;
    }

    static YouTubeUploader uploader(WireMockServer server, Path dir) {
        String base = "http://localhost:" + server.port();
        YouTubeProperties props = new YouTubeProperties(
                base,
                base + "/token",
                base + "/auth",
                "test-client",
                "test-client-secret",
                "test-passphrase",
                dir.resolve("token.enc"),
                3,
                Duration.ZERO);
        OAuthTokenStore store = new OAuthTokenStore(props);
        store.save("fake-refresh-token");
        JsonMapper mapper = JsonMapper.builder().build();
        return new YouTubeUploader(props, new AccessTokenProvider(props, store, mapper), mapper);
    }

    static Path fakeMp4(Path dir) throws java.io.IOException {
        Path p = dir.resolve("video.mp4");
        Files.write(p, new byte[] {0, 1, 2, 3, 4, 5});
        return p;
    }
}
