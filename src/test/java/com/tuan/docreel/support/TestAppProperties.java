package com.tuan.docreel.support;

import com.tuan.docreel.common.AppProperties;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

/** AppProperties dựng tay cho unit test. */
public final class TestAppProperties {

    private TestAppProperties() {}

    public static AppProperties withDirsUnder(Path root) {
        return new AppProperties(
                new AppProperties.Dirs(
                        root.resolve("inbox"),
                        root.resolve("requests"),
                        root.resolve("work"),
                        root.resolve("done"),
                        root.resolve("failed"),
                        root.resolve("duplicate")),
                new AppProperties.Tools("ffmpeg", "ffprobe"),
                new AppProperties.Tts(
                        AppProperties.TtsProviderName.ELEVENLABS,
                        AppProperties.TtsProviderName.EDGE,
                        AppProperties.ElevenLabs.withDefaults(
                                "", URI.create("http://localhost:1"), "eleven_flash_v2_5", "TODO_CHON_SAU", 1.0),
                        new AppProperties.Edge("edge-tts", "vi-VN-HoaiMyNeural", "+0%", 300)),
                new AppProperties.SelfCheck(Duration.ofMinutes(15)));
    }
}
