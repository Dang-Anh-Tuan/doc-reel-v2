package com.tuan.docreel.script;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Màu cột của scene {@code compare}; null = {@code neutral}. */
public enum CompareKind {
    @JsonProperty("neutral")
    NEUTRAL,
    @JsonProperty("bad")
    BAD,
    @JsonProperty("good")
    GOOD
}
