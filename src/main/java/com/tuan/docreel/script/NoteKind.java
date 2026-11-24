package com.tuan.docreel.script;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Kiểu ghi chú của scene {@code code}; null = {@code info}. */
public enum NoteKind {
    @JsonProperty("info")
    INFO,
    @JsonProperty("warn")
    WARN,
    @JsonProperty("tip")
    TIP
}
