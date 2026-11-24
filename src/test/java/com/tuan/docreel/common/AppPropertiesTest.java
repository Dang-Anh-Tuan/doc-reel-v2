package com.tuan.docreel.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AppPropertiesTest {

    /** Không gắn @Configuration để component scan của các IT không nhặt nhầm. */
    @EnableConfigurationProperties(AppProperties.class)
    static class Config {}

    private static Map<String, String> validProperties() {
        Map<String, String> p = new LinkedHashMap<>();
        for (String dir : new String[] {"inbox", "requests", "work", "done", "failed", "duplicate"}) {
            p.put("docreel.dirs." + dir, "data/" + dir);
        }
        p.put("docreel.tools.ffmpeg", "ffmpeg");
        p.put("docreel.tools.ffprobe", "ffprobe");
        p.put("docreel.tts.primary", "elevenlabs");
        p.put("docreel.tts.fallback", "edge");
        p.put("docreel.tts.elevenlabs.api-key", "test-key-not-real");
        p.put("docreel.tts.elevenlabs.base-url", "http://localhost:9999");
        p.put("docreel.tts.elevenlabs.model", "eleven_flash_v2_5");
        p.put("docreel.tts.elevenlabs.voice-id", "TODO_CHON_SAU");
        p.put("docreel.tts.elevenlabs.speed", "1.0");
        p.put("docreel.tts.edge.command", "edge-tts");
        p.put("docreel.tts.edge.voice", "vi-VN-HoaiMyNeural");
        p.put("docreel.tts.edge.rate", "+10%");
        p.put("docreel.tts.edge.pause-between-calls-ms", "300");
        return p;
    }

    private static ApplicationContextRunner runnerWith(Map<String, String> props) {
        return new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withPropertyValues(props.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .toArray(String[]::new));
    }

    @Test
    void bindsAllSectionsWhenConfigComplete() {
        runnerWith(validProperties()).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            AppProperties props = ctx.getBean(AppProperties.class);
            assertThat(props.dirs().inbox()).isEqualTo(Path.of("data/inbox"));
            assertThat(props.dirs().all()).hasSize(6);
            assertThat(props.tts().primary()).isEqualTo(AppProperties.TtsProviderName.ELEVENLABS);
            assertThat(props.tts().fallback()).isEqualTo(AppProperties.TtsProviderName.EDGE);
            assertThat(props.tts().elevenlabs().baseUrl()).isEqualTo(URI.create("http://localhost:9999"));
            assertThat(props.tts().elevenlabs().hasApiKey()).isTrue();
            assertThat(props.tts().edge().pauseBetweenCallsMs()).isEqualTo(300);
            assertThat(props.selfCheck().interval()).isEqualTo(Duration.ofMinutes(15));
        });
    }

    @Test
    void failsToStartWhenInboxDirMissing() {
        Map<String, String> props = validProperties();
        props.remove("docreel.dirs.inbox");

        runnerWith(props).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure()).rootCause().hasMessageContaining("dirs.inbox");
        });
    }

    @Test
    void failsToStartWhenSpeedOutOfRange() {
        Map<String, String> props = validProperties();
        props.put("docreel.tts.elevenlabs.speed", "1.5");

        runnerWith(props).run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void failsToStartWhenEdgeRateMalformed() {
        Map<String, String> props = validProperties();
        props.put("docreel.tts.edge.rate", "fast");

        runnerWith(props).run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void neverPrintsApiKeyInToString() {
        runnerWith(validProperties()).run(ctx -> {
            String text = ctx.getBean(AppProperties.class).toString();
            assertThat(text).doesNotContain("test-key-not-real").contains("apiKey=***");
        });
    }

    @Test
    void treatsBlankApiKeyAsNotConfigured() {
        Map<String, String> props = validProperties();
        props.put("docreel.tts.elevenlabs.api-key", "");

        runnerWith(props).run(ctx -> {
            AppProperties.ElevenLabs el = ctx.getBean(AppProperties.class).tts().elevenlabs();
            assertThat(el.hasApiKey()).isFalse();
            assertThat(el.toString()).contains("apiKey=<empty>");
        });
    }
}
