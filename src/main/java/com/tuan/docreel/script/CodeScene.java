package com.tuan.docreel.script;

import java.util.List;

/** {@code code}: một đoạn code, dòng {@code highlight} đếm từ 1. */
public record CodeScene(
        String id,
        String narration,
        String spoken,
        String accent,
        String titleRest,
        String file,
        String lang,
        String code,
        List<Integer> highlight,
        String note,
        NoteKind noteKind)
        implements TitledScene {}
