package com.tuan.docreel.script;

import java.util.List;

/** {@code steps}: 3–5 bước đánh số. */
public record StepsScene(
        String id, String narration, String spoken, String accent, String titleRest, List<String> steps)
        implements TitledScene {}
