package com.tuan.docreel.render;

import java.util.List;

/** Slide tràn khung / vượt giới hạn (slide.js ghi {@code data-problems}). Lỗi nội dung → không retry. */
public class SlideOverflowException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String[] problems;

    public SlideOverflowException(String sceneId, List<String> problems) {
        super("Slide '" + sceneId + "' bị tràn: " + String.join(", ", problems));
        this.problems = problems.toArray(new String[0]);
    }

    public List<String> problems() {
        return List.of(problems);
    }

    /** Parse giá trị {@code body[data-problems]} (phân cách bằng dấu phẩy, có thể rỗng/null). */
    public static List<String> parseProblems(String dataProblems) {
        if (dataProblems == null || dataProblems.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(dataProblems.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Ném {@link SlideOverflowException} nếu {@code dataProblems} không rỗng. */
    public static void throwIfAny(String sceneId, String dataProblems) {
        List<String> problems = parseProblems(dataProblems);
        if (!problems.isEmpty()) {
            throw new SlideOverflowException(sceneId, problems);
        }
    }
}
