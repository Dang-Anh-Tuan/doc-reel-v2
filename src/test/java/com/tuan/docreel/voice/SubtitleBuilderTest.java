package com.tuan.docreel.voice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class SubtitleBuilderTest {

    private final SubtitleBuilder builder = new SubtitleBuilder();

    @Test
    void formatsTimestamps() {
        assertThat(SubtitleBuilder.timestamp(0)).isEqualTo("00:00:00,000");
        assertThat(SubtitleBuilder.timestamp(3661.5)).isEqualTo("01:01:01,500");
        assertThat(SubtitleBuilder.timestamp(1.2345)).isEqualTo("00:00:01,235");
    }

    @Test
    void sentencesAreContiguousAndNumbered() {
        List<SubtitleBuilder.Segment> segs = builder.align(List.of("Câu một.", "Câu hai."), List.of(1.5, 2.0), 0.2);
        String srt = builder.build(segs);
        assertThat(srt)
                .isEqualTo("1\n00:00:00,200 --> 00:00:01,700\nCâu một.\n\n"
                        + "2\n00:00:01,700 --> 00:00:03,700\nCâu hai.\n\n");
    }

    @Test
    void longSentenceWrapsIntoAtMostTwoLinesPerCue() {
        String longText = "một hai ba bốn năm sáu bảy tám chín mười một hai ba bốn năm sáu bảy tám chín mười "
                + "một hai ba bốn năm sáu bảy tám chín mười một hai ba bốn năm sáu bảy tám chín mười";
        String srt = builder.build(List.of(new SubtitleBuilder.Segment(longText, 0, 10)));

        for (String cue : srt.strip().split("\n\n")) {
            String[] lines = cue.split("\n");
            assertThat(lines.length).isBetween(3, 4); // số thứ tự + thời gian + 1-2 dòng chữ
            for (int i = 2; i < lines.length; i++) {
                assertThat(lines[i].length()).isLessThanOrEqualTo(SubtitleBuilder.MAX_LINE_CHARS);
            }
        }
        // các cue của cùng câu nối liền nhau và phủ đúng [0, 10]
        Matcher m = Pattern.compile("(\\d\\d:\\d\\d:\\d\\d,\\d{3}) --> (\\d\\d:\\d\\d:\\d\\d,\\d{3})")
                .matcher(srt);
        String prevEnd = "00:00:00,000";
        int cues = 0;
        while (m.find()) {
            assertThat(m.group(1)).isEqualTo(prevEnd);
            prevEnd = m.group(2);
            cues++;
        }
        assertThat(cues).isGreaterThan(1);
        assertThat(prevEnd).isEqualTo("00:00:10,000");
    }

    @Test
    void mismatchedSentenceCountSplitsTimeByLength() {
        // spoken có 3 câu (thời lượng 1+1+2 = 4s) nhưng phụ đề chỉ 2 câu
        List<SubtitleBuilder.Segment> segs = builder.align(List.of("aaaa", "bbbbbbbb"), List.of(1.0, 1.0, 2.0), 0);
        assertThat(segs).hasSize(2);
        assertThat(segs.get(0).start()).isZero();
        assertThat(segs.get(0).end()).isEqualTo(segs.get(1).start());
        assertThat(segs.get(0).end()).isCloseTo(4.0 / 3, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(segs.get(1).end()).isEqualTo(4.0);
    }

    @Test
    void blankSegmentIsSkipped() {
        assertThat(builder.build(List.of(new SubtitleBuilder.Segment("   ", 0, 1))))
                .isEmpty();
    }
}
