package com.tuan.docreel.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.script.Origin;
import com.tuan.docreel.script.ScriptParser;
import com.tuan.docreel.script.VideoScript;
import org.junit.jupiter.api.Test;

class IdempotencyKeyTest {

    @Test
    void formatIsTopicVersionSha256() {
        VideoScript s = JobFixtures.sampleScript();
        String hash = ScriptParser.contentHash(s);
        assertThat(hash).matches("[0-9a-f]{64}");
        assertThat(IdempotencyKey.of(s)).isEqualTo(s.topicId() + ":" + s.version() + ":" + hash);
    }

    @Test
    void sameContentDifferentOriginSameKey() {
        VideoScript s = JobFixtures.sampleScript();
        VideoScript other = new VideoScript(
                s.topicId(),
                s.track(),
                s.title(),
                s.version(),
                s.videoType(),
                s.theme(),
                s.sources(),
                s.scenes(),
                new Origin("telegram", "1", "2"));
        assertThat(IdempotencyKey.of(other)).isEqualTo(IdempotencyKey.of(s));
    }

    @Test
    void changedContentChangesKey() {
        VideoScript s = JobFixtures.sampleScript();
        assertThat(IdempotencyKey.of(JobFixtures.withTitle(s, s.title() + "!"))).isNotEqualTo(IdempotencyKey.of(s));
    }
}
