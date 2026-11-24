package com.tuan.docreel.script;

/** {@code definition}: thuật ngữ cỡ lớn (không có tiêu đề chung). */
public record DefinitionScene(
        String id, String narration, String spoken, String term, String expansion, String definition, String example)
        implements Scene {}
