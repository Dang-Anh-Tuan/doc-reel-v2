package com.tuan.docreel.ingest;

import com.tuan.docreel.common.AppProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Cổng ghi duy nhất vào {@code inbox/} (SPEC mục 10): ghi {@code *.json.part} rồi đổi tên nguyên tử sang
 * {@code {channel}-{yyyyMMdd'T'HHmmss}-{ref}.json}, nên watcher không bao giờ thấy file ghi dở.
 */
@Component
public class InboxWriter {

    static final String PART_SUFFIX = ".part";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private final Path inbox;
    private final Clock clock;
    private final Consumer<Path> afterPartWritten;

    @Autowired
    public InboxWriter(AppProperties props) {
        this(props.dirs().inbox(), Clock.systemDefaultZone(), p -> {});
    }

    /** Dùng cho test: {@code afterPartWritten} chạy giữa lúc ghi xong {@code .part} và lúc đổi tên. */
    InboxWriter(Path inbox, Clock clock, Consumer<Path> afterPartWritten) {
        this.inbox = inbox;
        this.clock = clock;
        this.afterPartWritten = afterPartWritten;
    }

    /** Tên file theo quy ước; ký tự ngoài {@code [A-Za-z0-9_-]} trong channel/ref đổi thành {@code _}. */
    public String fileName(String channel, String ref) {
        return clean(channel, "inbox") + "-" + STAMP.format(LocalDateTime.now(clock)) + "-" + clean(ref, "x") + ".json";
    }

    /** Ghi nội dung vào inbox, trả đường dẫn file {@code .json} cuối cùng (trùng tên thì thêm hậu tố -2, -3...). */
    public Path write(String channel, String ref, byte[] json) {
        try {
            Files.createDirectories(inbox);
            String base = fileName(channel, ref);
            String stem = base.substring(0, base.length() - ".json".length());
            for (int n = 1; ; n++) {
                String name = n == 1 ? base : stem + "-" + n + ".json";
                Path target = inbox.resolve(name);
                Path part = inbox.resolve(name + PART_SUFFIX);
                try {
                    // Chiếm tên .part (CREATE_NEW) để 2 bên ghi cùng lúc không dẫm nhau.
                    Files.createFile(part);
                } catch (FileAlreadyExistsException e) {
                    continue;
                }
                if (Files.exists(target)) {
                    Files.deleteIfExists(part);
                    continue;
                }
                Files.write(part, json);
                afterPartWritten.accept(part);
                Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
                return target;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String clean(String s, String fallback) {
        String c = s == null ? "" : s.replaceAll("[^A-Za-z0-9_-]", "_");
        if (c.isBlank()) {
            return fallback;
        }
        return c.length() > 64 ? c.substring(0, 64) : c;
    }
}
