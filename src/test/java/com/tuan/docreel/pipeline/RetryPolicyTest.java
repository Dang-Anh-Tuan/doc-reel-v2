package com.tuan.docreel.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.render.SlideOverflowException;
import com.tuan.docreel.voice.TtsException;
import com.tuan.docreel.voice.TtsQuotaExceededException;
import com.tuan.docreel.voice.TtsTransientException;
import com.tuan.docreel.voice.TtsUnavailableException;
import com.tuan.docreel.youtube.YouTubeApiException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

    private final RetryPolicy policy = RetryPolicy.DEFAULT;

    @Test
    void transientErrorRetriesAfter30sThen2minThenFails() {
        var error = new TtsTransientException("503");

        assertThat(policy.decide(error, 1).delay()).isEqualTo(Duration.ofSeconds(30));
        assertThat(policy.decide(error, 2).delay()).isEqualTo(Duration.ofMinutes(2));
        assertThat(policy.decide(error, 3).retry()).isFalse();
    }

    @Test
    void slideOverflowFailsImmediately() {
        var error = new SlideOverflowException("s01", List.of("title"));

        assertThat(policy.decide(error, 1).retry()).isFalse();
    }

    @Test
    void ttsUnavailableRetriesUntilExhausted() {
        var error = new TtsUnavailableException("edge-tts thoát 1");

        assertThat(policy.decide(error, 1).retry()).isTrue();
        assertThat(policy.decide(error, 2).retry()).isTrue();
        assertThat(policy.decide(error, 3).retry()).isFalse();
    }

    @Test
    void plainTtsErrorsDoNotRetry() {
        assertThat(policy.decide(new TtsException("ElevenLabs 401"), 1).retry()).isFalse();
        assertThat(policy.decide(new TtsQuotaExceededException("hết quota"), 1).retry())
                .isFalse();
    }

    @Test
    void youTubeErrorsDependOnStatus() {
        assertThat(policy.decide(new YouTubeApiException(503, "x"), 1).retry()).isTrue();
        assertThat(policy.decide(new YouTubeApiException(429, "x"), 1).retry()).isTrue();
        assertThat(policy.decide(new YouTubeApiException("mạng", new RuntimeException()), 1)
                        .retry())
                .isTrue();
        assertThat(policy.decide(new YouTubeApiException(403, "quota"), 1).retry())
                .isFalse();
        assertThat(policy.decide(new YouTubeApiException(400, "x"), 1).retry()).isFalse();
    }

    @Test
    void unknownErrorIsTreatedAsTransient() {
        assertThat(policy.decide(new IllegalStateException("ffmpeg"), 1).retry())
                .isTrue();
        assertThat(policy.decide(new java.io.UncheckedIOException(new java.io.IOException("đĩa")), 1)
                        .retry())
                .isTrue();
    }

    @Test
    void adviceOfWrappedCauseIsHonoured() {
        var wrapped = new RuntimeException("bọc", new SlideOverflowException("s", List.of("x")));

        assertThat(policy.decide(wrapped, 1).retry()).isFalse();
    }
}
