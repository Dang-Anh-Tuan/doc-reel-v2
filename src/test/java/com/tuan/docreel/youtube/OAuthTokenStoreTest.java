package com.tuan.docreel.youtube;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OAuthTokenStoreTest {

    @TempDir
    Path dir;

    @Test
    void roundTripAndNotPlaintextOnDisk() throws IOException {
        Path file = dir.resolve("t.enc");
        new OAuthTokenStore(file, "pass-a").save("refresh-secret-value");
        assertThat(Files.readString(file)).doesNotContain("refresh-secret-value");
        assertThat(new OAuthTokenStore(file, "pass-a").load()).contains("refresh-secret-value");
    }

    @Test
    void wrongKeyOrMissingFileGivesEmpty() {
        Path file = dir.resolve("t.enc");
        assertThat(new OAuthTokenStore(file, "pass-a").load()).isEmpty();
        new OAuthTokenStore(file, "pass-a").save("x");
        assertThat(new OAuthTokenStore(file, "pass-b").load()).isEmpty();
    }
}
