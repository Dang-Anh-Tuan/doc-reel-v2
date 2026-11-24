package com.tuan.docreel.script;

/** {@code title}: mở đầu video. */
public record TitleScene(
        String id, String narration, String spoken, String chip, String accent, String titleRest, String hook)
        implements TitledScene {}
