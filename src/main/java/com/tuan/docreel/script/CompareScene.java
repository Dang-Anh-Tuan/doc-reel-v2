package com.tuan.docreel.script;

import java.util.List;

/** {@code compare}: 2 cột A vs B. */
public record CompareScene(
        String id,
        String narration,
        String spoken,
        String accent,
        String titleRest,
        Column left,
        Column right,
        String note)
        implements TitledScene {

    /** @param kind null = {@code neutral} */
    public record Column(String heading, CompareKind kind, List<String> items) {}
}
