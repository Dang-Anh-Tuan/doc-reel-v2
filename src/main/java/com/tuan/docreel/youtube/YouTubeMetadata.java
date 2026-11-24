package com.tuan.docreel.youtube;

import com.tuan.docreel.script.VideoScript;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Metadata video gửi lên YouTube (luôn {@code private}). */
public record YouTubeMetadata(String title, String description, List<String> tags, String privacyStatus) {

    public static final int MAX_TITLE = 100;
    public static final String PRIVATE = "private";
    private static final int MAX_TAGS_CHARS = 450;

    public static YouTubeMetadata from(VideoScript script) {
        return new YouTubeMetadata(title(script), description(script), tags(script), PRIVATE);
    }

    private static String title(VideoScript s) {
        String t = s.title() == null ? "" : s.title().strip();
        int[] cps = t.codePoints().toArray();
        if (cps.length <= MAX_TITLE) {
            return t;
        }
        return new String(cps, 0, MAX_TITLE - 1) + "…";
    }

    private static String description(VideoScript s) {
        StringBuilder sb = new StringBuilder();
        sb.append(s.title()).append("\n\n");
        sb.append("Chủ đề: ")
                .append(s.topicId())
                .append(" · ")
                .append(s.track())
                .append('\n');
        if (s.sources() != null && !s.sources().isEmpty()) {
            sb.append("\nNguồn:\n");
            s.sources().forEach(u -> sb.append(u).append('\n'));
        }
        sb.append("\n#Shorts #").append(hashtag(s.track()));
        return sb.toString();
    }

    private static List<String> tags(VideoScript s) {
        Set<String> out = new LinkedHashSet<>();
        out.add(s.track());
        out.add(s.topicId());
        out.add("Shorts");
        out.add("lập trình");
        List<String> result = new ArrayList<>();
        int chars = 0;
        for (String t : out) {
            if (t == null || t.isBlank()) {
                continue;
            }
            chars += t.length() + 1;
            if (chars > MAX_TAGS_CHARS) {
                break;
            }
            result.add(t);
        }
        return result;
    }

    private static String hashtag(String track) {
        return track == null ? "" : track.replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
