package com.tuan.docreel.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InboxWriterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-23T21:30:00Z"), ZoneOffset.UTC);

    @TempDir
    Path inbox;

    @Test
    void targetAppearsOnlyAfterWriteCompletes() throws Exception {
        AtomicBoolean checked = new AtomicBoolean();
        byte[] content = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        InboxWriter writer = new InboxWriter(inbox, CLOCK, part -> {
            // Lúc này nội dung đã nằm trong .part nhưng file .json đích chưa tồn tại.
            try (var s = Files.list(inbox)) {
                assertThat(s.map(p -> p.getFileName().toString()).toList())
                        .noneMatch(n -> n.endsWith(".json"))
                        .anyMatch(n -> n.endsWith(".json.part"));
                assertThat(Files.readAllBytes(part)).isEqualTo(content);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            checked.set(true);
        });

        Path written = writer.write("telegram", "5521", content);

        assertThat(checked).isTrue();
        assertThat(Files.readAllBytes(written)).isEqualTo(content);
        try (var s = Files.list(inbox)) {
            assertThat(s.map(p -> p.getFileName().toString()).toList())
                    .containsExactly(written.getFileName().toString());
        }
    }

    @Test
    void fileNameFollowsChannelTimestampRefPattern() {
        InboxWriter writer = new InboxWriter(inbox, CLOCK, p -> {});

        assertThat(writer.fileName("telegram", "5521")).isEqualTo("telegram-20261123T213000-5521.json");
        assertThat(writer.fileName("a/b", "../x y")).matches("a_b-\\d{8}T\\d{6}-___x_y\\.json");
        assertThat(writer.fileName(null, null)).isEqualTo("inbox-20261123T213000-x.json");
    }

    @Test
    void twoWritesInSameSecondKeepBothFiles() throws Exception {
        InboxWriter writer = new InboxWriter(inbox, CLOCK, p -> {});

        Path first = writer.write("telegram", "5521", "A".getBytes(StandardCharsets.UTF_8));
        Path second = writer.write("telegram", "5521", "B".getBytes(StandardCharsets.UTF_8));

        assertThat(second).isNotEqualTo(first);
        assertThat(Files.readString(first)).isEqualTo("A");
        assertThat(Files.readString(second)).isEqualTo("B");
        assertThat(second.getFileName().toString()).isEqualTo("telegram-20261123T213000-5521-2.json");
    }
}
