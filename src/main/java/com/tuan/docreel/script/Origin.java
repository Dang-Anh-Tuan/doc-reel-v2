package com.tuan.docreel.script;

/** Nơi gửi kịch bản (SPEC mục 10), vd {@code {"channel":"telegram","chatId":"…","messageId":"…"}}. */
public record Origin(String channel, String chatId, String messageId) {}
