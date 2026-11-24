package com.tuan.docreel.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tuan.docreel.common.ProcessFailedException;
import com.tuan.docreel.common.ProcessRequest;
import com.tuan.docreel.common.ProcessResult;
import com.tuan.docreel.common.ProcessRunner;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EdgeTtsProviderTest {

    @TempDir
    Path root;

    private EdgeTtsProvider provider(ProcessRunner runner, long pauseMs) {
        return provider(runner, pauseMs, 1);
    }

    private EdgeTtsProvider provider(ProcessRunner runner, long pauseMs, int attempts) {
        return new EdgeTtsProvider(
                runner,
                VoiceFixtures.props(root, URI.create("http://localhost:1"), "", "edge-tts", pauseMs),
                Duration.ofSeconds(5),
                attempts,
                Duration.ZERO);
    }

    private static ProcessFailedException noAudio() {
        return new ProcessFailedException(
                List.of("edge-tts"),
                new ProcessResult(
                        1,
                        "",
                        "Traceback (most recent call last):\n  ...\nedge_tts.exceptions.NoAudioReceived: No audio was"
                                + " received.",
                        Duration.ZERO));
    }

    private static Object writeAudio(org.mockito.invocation.InvocationOnMock inv) throws Exception {
        ProcessRequest req = inv.getArgument(0);
        Files.write(Path.of(after(req.command(), "--write-media")), new byte[] {7});
        return new ProcessResult(0, "", "", Duration.ZERO);
    }

    @Test
    void retriesNoAudioReceivedUntilServiceAnswers() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any())).thenThrow(noAudio()).thenThrow(noAudio()).thenAnswer(EdgeTtsProviderTest::writeAudio);

        byte[] audio = provider(runner, 0, 4).synthesize("tôi dùng React", null, null, VoiceFixtures.settings());

        assertThat(audio).containsExactly(7);
        verify(runner, times(3)).run(any());
    }

    @Test
    void finalErrorIsOneShortLineNotPythonTraceback() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any())).thenThrow(noAudio());

        assertThatThrownBy(() -> provider(runner, 0, 2).synthesize("x", null, null, VoiceFixtures.settings()))
                .isInstanceOf(TtsUnavailableException.class)
                .hasMessage("TTS_UNAVAILABLE: edge-tts: NoAudioReceived (Microsoft không trả audio) — đã thử 2 lần")
                .hasRootCauseInstanceOf(ProcessFailedException.class); // traceback đầy đủ vẫn còn ở cause
    }

    @Test
    void summarizeKeepsLastLineWithExitCode() {
        assertThat(EdgeTtsProvider.summarize(1, "Traceback...\n  File x\nValueError: Invalid voice 'abc'."))
                .isEqualTo("thoát mã 1: ValueError: Invalid voice 'abc'.");
        assertThat(EdgeTtsProvider.summarize(3, "")).isEqualTo("thoát mã 3");
        assertThat(EdgeTtsProvider.summarize(1, "x".repeat(500))).hasSize("thoát mã 1: ".length() + 201);
    }

    @Test
    void retriesEmptyOutputFile() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any()))
                .thenAnswer(inv -> {
                    ProcessRequest req = inv.getArgument(0);
                    Files.write(Path.of(after(req.command(), "--write-media")), new byte[0]);
                    return new ProcessResult(0, "", "", Duration.ZERO);
                })
                .thenAnswer(EdgeTtsProviderTest::writeAudio);

        assertThat(provider(runner, 0, 2).synthesize("x", null, null, VoiceFixtures.settings()))
                .containsExactly(7);
        verify(runner, times(2)).run(any());
    }

    @Test
    void givesUpAfterMaxAttemptsWithTtsUnavailable() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any())).thenThrow(noAudio());

        assertThatThrownBy(() -> provider(runner, 0, 3).synthesize("x", null, null, VoiceFixtures.settings()))
                .isInstanceOf(TtsUnavailableException.class)
                .hasMessageContaining("NoAudioReceived");
        verify(runner, times(3)).run(any());
    }

    @Test
    void timeoutAndMissingCommandAreNotRetried() {
        ProcessRunner hang = mock(ProcessRunner.class);
        when(hang.run(any()))
                .thenThrow(new com.tuan.docreel.common.ProcessTimeoutException(
                        List.of("edge-tts"), Duration.ofSeconds(5), 1L, "", ""));
        assertThatThrownBy(() -> provider(hang, 0, 4).synthesize("x", null, null, VoiceFixtures.settings()))
                .isInstanceOf(TtsUnavailableException.class);
        verify(hang, times(1)).run(any());

        ProcessRunner missing = mock(ProcessRunner.class);
        when(missing.run(any())).thenThrow(new java.io.UncheckedIOException(new java.io.IOException("không có")));
        assertThatThrownBy(() -> provider(missing, 0, 4).synthesize("x", null, null, VoiceFixtures.settings()))
                .isInstanceOf(TtsUnavailableException.class);
        verify(missing, times(1)).run(any());
    }

    private static String after(List<String> cmd, String flag) {
        return cmd.get(cmd.indexOf(flag) + 1);
    }

    @Test
    void writesSentenceToUtf8FileAndBuildsCommandWithoutTextArgument() {
        String sentence = "Tiếng Việt có dấu, \"trích dẫn\" & ký tự đặc biệt.";
        List<List<String>> commands = new ArrayList<>();
        List<String> fileContents = new ArrayList<>();
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any())).thenAnswer(inv -> {
            ProcessRequest req = inv.getArgument(0);
            commands.add(req.command());
            fileContents.add(Files.readString(Path.of(after(req.command(), "--file")), StandardCharsets.UTF_8));
            Files.write(Path.of(after(req.command(), "--write-media")), new byte[] {9, 9, 9});
            return new ProcessResult(0, "", "", Duration.ZERO);
        });

        byte[] audio = provider(runner, 0).synthesize(sentence, null, null, VoiceFixtures.settings());

        assertThat(audio).containsExactly(9, 9, 9);
        List<String> cmd = commands.getFirst();
        assertThat(cmd.getFirst()).isEqualTo("edge-tts");
        assertThat(after(cmd, "--voice")).isEqualTo("vi-VN-HoaiMyNeural");
        assertThat(cmd).contains("--rate=+0%", "--file", "--write-media");
        assertThat(cmd).noneMatch(a -> a.contains("Tiếng") || a.contains("&") || a.contains("\""));
        assertThat(fileContents.getFirst()).isEqualTo(sentence);
        assertThat(Path.of(after(cmd, "--file"))).doesNotExist(); // thư mục tạm đã dọn
    }

    @Test
    void nonZeroExitBecomesTtsUnavailable() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any()))
                .thenThrow(new ProcessFailedException(
                        List.of("edge-tts"), new ProcessResult(2, "", "boom", Duration.ZERO)));
        assertThatThrownBy(() -> provider(runner, 0).synthesize("x", null, null, VoiceFixtures.settings()))
                .isInstanceOf(TtsUnavailableException.class)
                .hasMessageStartingWith("TTS_UNAVAILABLE");
    }

    @Test
    void emptyOutputFileBecomesTtsUnavailable() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any())).thenAnswer(inv -> {
            ProcessRequest req = inv.getArgument(0);
            Files.write(Path.of(after(req.command(), "--write-media")), new byte[0]);
            return new ProcessResult(0, "", "", Duration.ZERO);
        });
        assertThatThrownBy(() -> provider(runner, 0).synthesize("x", null, null, VoiceFixtures.settings()))
                .isInstanceOf(TtsUnavailableException.class);
    }

    @Test
    void missingCommandBecomesTtsUnavailable() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any())).thenThrow(new java.io.UncheckedIOException(new java.io.IOException("không có")));
        assertThatThrownBy(() -> provider(runner, 0).synthesize("x", null, null, VoiceFixtures.settings()))
                .isInstanceOf(TtsUnavailableException.class);
    }

    @Test
    void pausesBetweenCalls() {
        ProcessRunner runner = mock(ProcessRunner.class);
        when(runner.run(any())).thenAnswer(inv -> {
            ProcessRequest req = inv.getArgument(0);
            Files.write(Path.of(after(req.command(), "--write-media")), new byte[] {1});
            return new ProcessResult(0, "", "", Duration.ZERO);
        });
        EdgeTtsProvider p = provider(runner, 300);
        p.synthesize("a", null, null, VoiceFixtures.settings());
        long start = System.nanoTime();
        p.synthesize("b", null, null, VoiceFixtures.settings());
        assertThat(Duration.ofNanos(System.nanoTime() - start).toMillis()).isGreaterThanOrEqualTo(250);
    }

    @Test
    void alwaysConfiguredAndUnlimited() {
        EdgeTtsProvider p = provider(mock(ProcessRunner.class), 0);
        assertThat(p.isConfigured()).isTrue();
        assertThat(p.remainingChars()).isEqualTo(Long.MAX_VALUE);
        assertThat(p.name()).isEqualTo(com.tuan.docreel.common.AppProperties.TtsProviderName.EDGE);
    }
}
