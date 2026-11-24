package com.tuan.docreel.youtube;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.system.CheckStatus;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YouTubeTokenCheckTest {

    @TempDir
    Path dir;

    @Test
    void missingTokenIsWarnWithAuthHint() {
        var r = new YouTubeTokenCheck(new OAuthTokenStore(dir.resolve("t.enc"), "k")).check();
        assertThat(r.status()).isEqualTo(CheckStatus.WARN);
        assertThat(r.fix()).contains("--youtube-auth");
    }

    @Test
    void tokenPresentAndDecryptableIsOk() {
        OAuthTokenStore store = new OAuthTokenStore(dir.resolve("t.enc"), "k");
        store.save("refresh");
        assertThat(new YouTubeTokenCheck(store).check().status()).isEqualTo(CheckStatus.OK);
    }

    @Test
    void wrongKeyIsWarn() {
        new OAuthTokenStore(dir.resolve("t.enc"), "k").save("refresh");
        var r = new YouTubeTokenCheck(new OAuthTokenStore(dir.resolve("t.enc"), "other")).check();
        assertThat(r.status()).isEqualTo(CheckStatus.WARN);
        assertThat(r.detail()).contains("YOUTUBE_ENCRYPTION_KEY");
    }
}
