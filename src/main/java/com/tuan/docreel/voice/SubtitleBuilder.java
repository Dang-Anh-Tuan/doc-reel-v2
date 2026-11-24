package com.tuan.docreel.voice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dựng {@code .srt} từ thời lượng từng câu (không cần API căn thời gian). Mỗi cue tối đa 2 dòng; câu dài được chia
 * thành nhiều cue liên tiếp theo tỉ lệ số ký tự.
 */
public class SubtitleBuilder {

    static final int MAX_LINE_CHARS = 42;
    private static final int MAX_LINES = 2;

    /** Một đoạn phụ đề: chữ + mốc (giây) trên timeline của cả video. */
    public record Segment(String text, double start, double end) {}

    /**
     * Gán chữ phụ đề (narration) vào thời lượng các câu đã đọc (spoken). Cùng số câu → 1:1; lệch số câu (do
     * {@code spoken} khác narration) → chia thời gian theo tỉ lệ độ dài chữ.
     */
    public List<Segment> align(List<String> subtitleSentences, List<Double> spokenDurations, double startOffset) {
        double total = spokenDurations.stream().mapToDouble(Double::doubleValue).sum();
        List<Segment> out = new ArrayList<>();
        double t = startOffset;
        if (subtitleSentences.size() == spokenDurations.size()) {
            for (int i = 0; i < subtitleSentences.size(); i++) {
                double end = t + spokenDurations.get(i);
                out.add(new Segment(subtitleSentences.get(i), t, end));
                t = end;
            }
            return out;
        }
        int chars = subtitleSentences.stream().mapToInt(String::length).sum();
        for (int i = 0; i < subtitleSentences.size(); i++) {
            String s = subtitleSentences.get(i);
            double end = i == subtitleSentences.size() - 1
                    ? startOffset + total
                    : t + total * s.length() / Math.max(chars, 1);
            out.add(new Segment(s, t, end));
            t = end;
        }
        return out;
    }

    public String build(List<Segment> segments) {
        StringBuilder srt = new StringBuilder();
        int index = 1;
        for (Segment segment : segments) {
            List<String> lines = wrap(segment.text());
            if (lines.isEmpty()) {
                continue;
            }
            List<String> cues = new ArrayList<>();
            for (int i = 0; i < lines.size(); i += MAX_LINES) {
                cues.add(String.join("\n", lines.subList(i, Math.min(i + MAX_LINES, lines.size()))));
            }
            int totalChars = cues.stream().mapToInt(String::length).sum();
            double t = segment.start();
            double span = segment.end() - segment.start();
            for (int i = 0; i < cues.size(); i++) {
                double end = i == cues.size() - 1
                        ? segment.end()
                        : t + span * cues.get(i).length() / totalChars;
                srt.append(index++)
                        .append('\n')
                        .append(timestamp(t))
                        .append(" --> ")
                        .append(timestamp(end))
                        .append('\n')
                        .append(cues.get(i))
                        .append("\n\n");
                t = end;
            }
        }
        return srt.toString();
    }

    /** {@code HH:MM:SS,mmm}. */
    public static String timestamp(double seconds) {
        long ms = Math.round(Math.max(seconds, 0) * 1000);
        return String.format(
                Locale.ROOT, "%02d:%02d:%02d,%03d", ms / 3_600_000, ms / 60_000 % 60, ms / 1000 % 60, ms % 1000);
    }

    static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.strip().split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (line.length() > 0 && line.length() + 1 + word.length() > MAX_LINE_CHARS) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }
}
