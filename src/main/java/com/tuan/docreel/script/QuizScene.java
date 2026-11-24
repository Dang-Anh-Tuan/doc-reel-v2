package com.tuan.docreel.script;

import java.util.List;

/**
 * {@code quiz}: 2 frame — {@code narration} đọc ở frame hỏi, {@code revealNarration} ở frame đáp án.
 *
 * @param answer index đáp án đúng, đếm từ 0
 */
public record QuizScene(
        String id,
        String narration,
        String spoken,
        String question,
        List<String> options,
        Integer answer,
        String explain,
        String revealNarration)
        implements Scene {}
