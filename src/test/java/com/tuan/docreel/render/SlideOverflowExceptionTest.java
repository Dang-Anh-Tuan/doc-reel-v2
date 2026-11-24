package com.tuan.docreel.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SlideOverflowExceptionTest {

    @Test
    void nonEmptyProblemsThrow() {
        assertThatThrownBy(() -> SlideOverflowException.throwIfAny("s01", "title, hook"))
                .isInstanceOfSatisfying(SlideOverflowException.class, e -> {
                    assertThat(e.problems()).containsExactly("title", "hook");
                    assertThat(e.getMessage()).contains("s01").contains("title");
                });
    }

    @Test
    void emptyOrMissingProblemsPass() {
        assertThatCode(() -> SlideOverflowException.throwIfAny("s01", "")).doesNotThrowAnyException();
        assertThatCode(() -> SlideOverflowException.throwIfAny("s01", null)).doesNotThrowAnyException();
        assertThatCode(() -> SlideOverflowException.throwIfAny("s01", " , ")).doesNotThrowAnyException();
    }

    @Test
    void contentTypes() {
        assertThat(SlideCapturer.contentType("/css/base.css")).startsWith("text/css");
        assertThat(SlideCapturer.contentType("/js/slide.js")).startsWith("text/javascript");
        assertThat(SlideCapturer.contentType("/vendor/fonts/a.ttf")).isEqualTo("font/ttf");
        assertThat(SlideCapturer.contentType("/x.woff2")).isEqualTo("font/woff2");
        assertThat(SlideCapturer.contentType("/x.bin")).isEqualTo("application/octet-stream");
    }
}
