package com.tuan.docreel.voice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SentenceSplitterTest {

    @Test
    void splitsVietnameseSentences() {
        assertThat(SentenceSplitter.split("JPA là chuẩn ánh xạ. Nó giúp bạn bớt viết SQL! Bạn đã thử chưa? Hãy thử."))
                .containsExactly("JPA là chuẩn ánh xạ.", "Nó giúp bạn bớt viết SQL!", "Bạn đã thử chưa?", "Hãy thử.");
    }

    @Test
    void doesNotSplitAtVV() {
        assertThat(SentenceSplitter.split("Có list, set, map v.v. đều dùng được. Tiếp theo là gì?"))
                .containsExactly("Có list, set, map v.v. đều dùng được.", "Tiếp theo là gì?");
    }

    @Test
    void doesNotSplitAtEg() {
        assertThat(SentenceSplitter.split("Dùng collection, e.g. List hoặc Set. Xong."))
                .containsExactly("Dùng collection, e.g. List hoặc Set.", "Xong.");
    }

    @Test
    void doesNotSplitDecimalsOrDottedNames() {
        assertThat(SentenceSplitter.split("Phiên bản 3.14 chạy trên Node.js rất nhanh. Đúng vậy."))
                .containsExactly("Phiên bản 3.14 chạy trên Node.js rất nhanh.", "Đúng vậy.");
    }

    @Test
    void keepsTextWithoutTerminatorAsOneSentence() {
        assertThat(SentenceSplitter.split("  Không có dấu chấm  ")).containsExactly("Không có dấu chấm");
    }

    @Test
    void ellipsisAndClosingQuoteStayWithSentence() {
        assertThat(SentenceSplitter.split("Ông nói \"được thôi.\" Rồi đi... Đi mãi."))
                .containsExactly("Ông nói \"được thôi.\"", "Rồi đi...", "Đi mãi.");
    }

    @Test
    void blankGivesEmpty() {
        assertThat(SentenceSplitter.split(null)).isEmpty();
        assertThat(SentenceSplitter.split("   ")).isEqualTo(List.of());
    }
}
