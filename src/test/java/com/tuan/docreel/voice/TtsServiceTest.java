package com.tuan.docreel.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tuan.docreel.common.AppProperties.TtsProviderName;
import com.tuan.docreel.support.TestAppProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TtsServiceTest {

    @TempDir
    Path root;

    /** Provider giả: audio có độ dài theo ký tự, ghi lại câu đã nhận. */
    static class StubProvider implements TtsProvider {
        final TtsProviderName name;
        final boolean configured;
        final long remaining;
        final List<String> received = new ArrayList<>();

        StubProvider(TtsProviderName name, boolean configured, long remaining) {
            this.name = name;
            this.configured = configured;
            this.remaining = remaining;
        }

        @Override
        public TtsProviderName name() {
            return name;
        }

        @Override
        public boolean isConfigured() {
            return configured;
        }

        @Override
        public long remainingChars() {
            return remaining;
        }

        @Override
        public String voiceKey(TtsSettings settings) {
            return "stub";
        }

        @Override
        public byte[] synthesize(String sentence, String previous, String next, TtsSettings settings) {
            received.add(sentence);
            return FakeAudioTool.audioFor(1, sentence);
        }
    }

    private static class RecordingUsage implements TtsUsageTracker {
        final List<String> calls = new ArrayList<>();

        @Override
        public void add(TtsProviderName provider, long chars) {
            calls.add(provider + ":" + chars);
        }
    }

    private TtsService service(Lexicon lexicon, TtsUsageTracker usage, TtsProvider... providers) {
        return new TtsService(
                List.of(providers),
                lexicon,
                new TtsCache(TestAppProperties.withDirsUnder(root)),
                new FakeAudioTool(),
                usage);
    }

    private Lexicon emptyLexicon() {
        return new Lexicon(root.resolve("none.yaml"), false);
    }

    @Test
    void choosesElevenLabsWhenQuotaCoversNeededTimesOnePointOne() {
        StubProvider eleven = new StubProvider(TtsProviderName.ELEVENLABS, true, 1100);
        StubProvider edge = new StubProvider(TtsProviderName.EDGE, true, Long.MAX_VALUE);
        TtsService svc = service(emptyLexicon(), new RecordingUsage(), eleven, edge);

        assertThat(svc.choose(1000, VoiceFixtures.settings(), null)).isSameAs(eleven); // đúng ngưỡng × 1.1
        assertThat(svc.choose(1001, VoiceFixtures.settings(), null)).isSameAs(edge); // thiếu 1 ký tự
        assertThat(svc.choose(10, VoiceFixtures.settings(), TtsProviderName.EDGE))
                .isSameAs(edge); // retry giữ nguyên
    }

    @Test
    void choosesEdgeWhenElevenLabsNotConfigured() {
        StubProvider eleven = new StubProvider(TtsProviderName.ELEVENLABS, false, 1_000_000);
        StubProvider edge = new StubProvider(TtsProviderName.EDGE, true, Long.MAX_VALUE);
        assertThat(service(emptyLexicon(), new RecordingUsage(), eleven, edge)
                        .choose(10, VoiceFixtures.settings(), null))
                .isSameAs(edge);
    }

    @Test
    void missingProviderFailsClearly() {
        TtsService svc = service(emptyLexicon(), new RecordingUsage());
        assertThatThrownBy(() -> svc.choose(1, VoiceFixtures.settings(), null)).isInstanceOf(TtsException.class);
    }

    @Test
    void voicesScenesAppliesLexiconOnlyToSpeechAndBuildsSrt() throws IOException {
        Path lexFile = root.resolve("lexicon.yaml");
        Files.writeString(lexFile, "JPA: \"Giê Pi Ây\"\n", StandardCharsets.UTF_8);
        StubProvider eleven = new StubProvider(TtsProviderName.ELEVENLABS, true, 1_000_000);
        StubProvider edge = new StubProvider(TtsProviderName.EDGE, true, Long.MAX_VALUE);
        RecordingUsage usage = new RecordingUsage();
        TtsService svc = service(new Lexicon(lexFile, false), usage, eleven, edge);

        var script = VoiceFixtures.script(
                VoiceFixtures.title("s01", "JPA là gì. Rất hay."),
                VoiceFixtures.quiz("s02", "Chọn đáp án.", "Đáp án là A."));
        VoiceResult result = svc.voice(script, root.resolve("work/j1"), VoiceFixtures.settings(), null);

        assertThat(result.provider()).isEqualTo(TtsProviderName.ELEVENLABS);
        assertThat(result.audios()).extracting(VoiceResult.UnitAudio::name).containsExactly("s01", "s02", "s02-reveal");
        assertThat(eleven.received).containsExactly("Giê Pi Ây là gì.", "Rất hay.", "Chọn đáp án.", "Đáp án là A.");
        assertThat(usage.calls).containsExactly("ELEVENLABS:16", "ELEVENLABS:8", "ELEVENLABS:12", "ELEVENLABS:12");
        // s01: câu 1 = 16 ký tự = 1.6s, câu 2 = 0.8s; đệm 0.2 + 0.4 → 3.0s
        assertThat(result.audios().getFirst().seconds()).isEqualTo(3.0);
        String srt = Files.readString(result.srt(), StandardCharsets.UTF_8);
        assertThat(srt).contains("JPA là gì.").doesNotContain("Giê Pi Ây");
        assertThat(srt)
                .startsWith(
                        "1\n00:00:00,200 --> 00:00:01,800\nJPA là gì.\n\n2\n00:00:01,800 --> 00:00:02,600\nRất hay.");
        // frame quiz thứ nhất bắt đầu sau 3.0s + 0.2s đệm
        assertThat(srt).contains("00:00:03,200 -->");
    }

    @Test
    void quotaFailureOnFallbackIsRethrown() {
        TtsProvider quota = new StubProvider(TtsProviderName.EDGE, true, Long.MAX_VALUE) {
            @Override
            public byte[] synthesize(String s, String p, String n, TtsSettings st) {
                throw new TtsQuotaExceededException("hết");
            }
        };
        TtsService svc = service(emptyLexicon(), new RecordingUsage(), quota);
        var script = VoiceFixtures.script(VoiceFixtures.title("s01", "Một câu."));
        TtsSettings edgeOnly =
                new TtsSettings(TtsProviderName.EDGE, TtsProviderName.EDGE, "v", 1.0, "vi-VN-HoaiMyNeural", "+0%");
        assertThatThrownBy(() -> svc.voice(script, root.resolve("work/j"), edgeOnly, null))
                .isInstanceOf(TtsQuotaExceededException.class);
    }

    @Test
    void blankNarrationFails() {
        StubProvider edge = new StubProvider(TtsProviderName.EDGE, true, Long.MAX_VALUE);
        TtsService svc = service(emptyLexicon(), new RecordingUsage(), edge);
        var script = VoiceFixtures.script(VoiceFixtures.title("s01", "   "));
        TtsSettings edgeOnly =
                new TtsSettings(TtsProviderName.EDGE, TtsProviderName.EDGE, "v", 1.0, "vi-VN-HoaiMyNeural", "+0%");
        assertThatThrownBy(() -> svc.voice(script, root.resolve("work/j"), edgeOnly, null))
                .isInstanceOf(TtsException.class);
    }
}
