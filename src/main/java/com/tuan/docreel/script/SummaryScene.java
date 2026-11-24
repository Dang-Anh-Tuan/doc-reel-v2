package com.tuan.docreel.script;

import java.util.List;

/** {@code summary}: kết video. */
public record SummaryScene(
        String id, String narration, String spoken, String accent, String titleRest, List<String> bullets, String outro)
        implements TitledScene {}
