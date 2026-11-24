package com.tuan.docreel.script;

import java.util.List;

/** {@code table}: 2–3 cột × 1–4 hàng. */
public record TableScene(
        String id,
        String narration,
        String spoken,
        String accent,
        String titleRest,
        List<String> columns,
        List<List<String>> rows)
        implements TitledScene {}
