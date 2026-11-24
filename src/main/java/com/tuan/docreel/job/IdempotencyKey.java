package com.tuan.docreel.job;

import com.tuan.docreel.script.ScriptParser;
import com.tuan.docreel.script.VideoScript;

/** Khóa chống trùng {@code topicId:version:sha256} (SPEC mục 9). */
public final class IdempotencyKey {

    private IdempotencyKey() {}

    public static String of(String topicId, int version, String contentHash) {
        return topicId + ":" + version + ":" + contentHash;
    }

    public static String of(VideoScript script) {
        return of(script.topicId(), script.version(), ScriptParser.contentHash(script));
    }
}
