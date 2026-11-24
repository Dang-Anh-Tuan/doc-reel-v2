package com.tuan.docreel.render;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.system.CheckStatus;
import org.junit.jupiter.api.Test;

class ChromiumCheckTest {

    @Test
    void okWhenBrowserOpens() {
        var r = new ChromiumCheck(() -> "140.0").check();
        assertThat(r.status()).isEqualTo(CheckStatus.OK);
        assertThat(r.detail()).contains("140.0");
    }

    @Test
    void criticalWithInstallHintWhenBrowserMissing() {
        var r = new ChromiumCheck(() -> {
                    throw new IllegalStateException("Executable doesn't exist\r\n\tat C:\\ms-playwright\n  more lines");
                })
                .check();
        assertThat(r.status()).isEqualTo(CheckStatus.CRITICAL);
        assertThat(r.detail()).contains("Executable doesn't exist at C:\\ms-playwright more lines");
        assertThat(r.detail()).doesNotContain("\n").doesNotContain("\r").doesNotContain("\t");
        assertThat(r.fix()).contains("playwright install chromium");
    }
}
