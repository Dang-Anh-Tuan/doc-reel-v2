package com.tuan.docreel.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tuan.docreel.common.AppProperties;
import com.tuan.docreel.common.AppProperties.TtsProviderName;
import com.tuan.docreel.common.ProcessRunner;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** edge-tts giả (process thật) thoát ≠ 0 / quá giờ / file rỗng → TTS_UNAVAILABLE; chạy ổn → ra audio. */
class TtsEdgeFailureIT {

    @TempDir
    Path root;

    private TtsService service(ProcessRunner runner, Duration timeout) {
        AppProperties props = VoiceFixtures.props(root, URI.create("http://localhost:1"), "", "edge-tts", 0);
        TtsProvider eleven = new ElevenLabsProvider(props); // không key → luôn rơi xuống edge
        TtsProvider edge = new EdgeTtsProvider(runner, props, timeout);
        return new TtsService(
                List.of(eleven, edge),
                new Lexicon(root.resolve("none.yaml"), false),
                new TtsCache(props),
                new FakeAudioTool(),
                (provider, chars) -> {});
    }

    private VoiceResult run(TtsService svc) {
        var script = VoiceFixtures.script(VoiceFixtures.title("s01", "Một câu thôi."));
        return svc.voice(script, root.resolve("work/j1"), VoiceFixtures.settings(), null);
    }

    @Test
    void exitCodeNotZeroIsTtsUnavailable() {
        TtsService svc = service(FakeEdgeTts.runner("fail"), Duration.ofSeconds(20));
        assertThatThrownBy(() -> run(svc))
                .isInstanceOf(TtsUnavailableException.class)
                .hasMessageContaining("TTS_UNAVAILABLE")
                .hasMessageContaining("3");
    }

    @Test
    void timeoutIsTtsUnavailable() {
        TtsService svc = service(FakeEdgeTts.runner("hang"), Duration.ofSeconds(3));
        assertThatThrownBy(() -> run(svc)).isInstanceOf(TtsUnavailableException.class);
    }

    @Test
    void emptyFileIsTtsUnavailable() {
        TtsService svc = service(FakeEdgeTts.runner("empty"), Duration.ofSeconds(20));
        assertThatThrownBy(() -> run(svc))
                .isInstanceOf(TtsUnavailableException.class)
                .hasMessageContaining("rỗng");
    }

    @Test
    void healthyCliProducesAudioAndSrt() throws Exception {
        TtsService svc = service(FakeEdgeTts.runner("ok"), Duration.ofSeconds(20));
        VoiceResult result = run(svc);
        assertThat(result.provider()).isEqualTo(TtsProviderName.EDGE);
        assertThat(Files.size(result.audios().getFirst().file())).isEqualTo(13 * 100 + 600);
        assertThat(Files.readString(result.srt())).contains("Một câu thôi.");
    }
}
