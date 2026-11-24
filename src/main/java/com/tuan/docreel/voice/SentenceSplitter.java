package com.tuan.docreel.voice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Tách lời đọc thành câu. Không tách ở {@code v.v.}, {@code e.g.}, số thập phân, tên có dấu chấm ({@code Node.js}). */
public final class SentenceSplitter {

    private static final Set<String> ABBREVIATIONS =
            Set.of("v.v", "vd", "e.g", "i.e", "etc", "vs", "tp", "ts", "th.s", "pgs", "gs", "dr", "mr", "mrs", "ms");
    private static final String CLOSERS = "\"')]”’»";
    private static final String OPENERS = "\"'([“‘«`";

    private SentenceSplitter() {}

    public static List<String> split(String text) {
        List<String> sentences = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return sentences;
        }
        String t = text.strip().replaceAll("\\s+", " ");
        int length = t.length();
        int start = 0;
        int i = 0;
        while (i < length) {
            char c = t.charAt(i);
            if (!isTerminator(c)) {
                i++;
                continue;
            }
            int end = i;
            while (end + 1 < length && isTerminator(t.charAt(end + 1))) {
                end++;
            }
            int cut = end + 1;
            while (cut < length && CLOSERS.indexOf(t.charAt(cut)) >= 0) {
                cut++;
            }
            if (cut < length && !startsNewSentence(t, cut)) {
                i = end + 1;
                continue;
            }
            if (c == '.' && end == i && cut < length && isAbbreviation(t, i)) {
                i = end + 1;
                continue;
            }
            addIfNotBlank(sentences, t.substring(start, cut));
            start = cut;
            i = cut;
        }
        addIfNotBlank(sentences, t.substring(start));
        return sentences;
    }

    private static boolean isTerminator(char c) {
        return c == '.' || c == '!' || c == '?' || c == '…';
    }

    /** Sau dấu kết câu phải là khoảng trắng rồi chữ hoa / số / dấu mở (không thì là số thập phân, tên file...). */
    private static boolean startsNewSentence(String t, int cut) {
        if (t.charAt(cut) != ' ') {
            return false;
        }
        int k = cut;
        while (k < t.length() && t.charAt(k) == ' ') {
            k++;
        }
        if (k >= t.length()) {
            return true;
        }
        char next = t.charAt(k);
        return Character.isUpperCase(next) || Character.isDigit(next) || OPENERS.indexOf(next) >= 0;
    }

    private static boolean isAbbreviation(String t, int dotIndex) {
        int s = dotIndex;
        while (s > 0 && t.charAt(s - 1) != ' ') {
            s--;
        }
        String token = t.substring(s, dotIndex).toLowerCase(Locale.ROOT);
        while (!token.isEmpty() && OPENERS.indexOf(token.charAt(0)) >= 0) {
            token = token.substring(1);
        }
        return ABBREVIATIONS.contains(token);
    }

    private static void addIfNotBlank(List<String> out, String s) {
        String stripped = s.strip();
        if (!stripped.isEmpty()) {
            out.add(stripped);
        }
    }
}
