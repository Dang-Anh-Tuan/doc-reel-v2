package com.tuan.docreel.voice;

import com.tuan.docreel.common.AppProperties;
import com.tuan.docreel.common.AppProperties.TtsProviderName;
import com.tuan.docreel.script.QuizScene;
import com.tuan.docreel.script.Scene;
import com.tuan.docreel.script.TitleScene;
import com.tuan.docreel.script.VideoScript;
import com.tuan.docreel.script.VideoType;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/** Dựng nhanh kịch bản, cấu hình, settings cho test giọng. */
final class VoiceFixtures {

    private VoiceFixtures() {}

    static AppProperties props(Path root, URI elevenBase, String apiKey, String edgeCommand, long pauseMs) {
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
                        TtsProviderName.ELEVENLABS,
                        TtsProviderName.EDGE,
                        new AppProperties.ElevenLabs(apiKey, elevenBase, "eleven_flash_v2_5", "voice1", 1.0),
                        new AppProperties.Edge(edgeCommand, "vi-VN-HoaiMyNeural", "+0%", pauseMs)),
                new AppProperties.SelfCheck(Duration.ofMinutes(15)));
    }

    static VoiceSettings settings() {
        return new VoiceSettings(
                TtsProviderName.ELEVENLABS, TtsProviderName.EDGE, "voice1", 1.0, "vi-VN-HoaiMyNeural", "+0%");
    }

    static VideoScript script(Scene... scenes) {
        return new VideoScript("t1", "java", "Tiêu đề", 1, VideoType.SLIDES, null, List.of(), List.of(scenes), null);
    }

    static Scene title(String id, String narration) {
        return new TitleScene(id, narration, null, "chip", "JPA", " là gì", "hook");
    }

    static Scene quiz(String id, String narration, String reveal) {
        return new QuizScene(id, narration, null, "q?", List.of("a", "b"), 0, "e", reveal);
    }
}
