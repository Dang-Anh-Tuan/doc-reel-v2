package com.tuan.docreel.voice;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Từ điển phát âm thuật ngữ ({@code config/lexicon.yaml}, dạng {@code JPA: "Giê Pi Ây"}). Chỉ thay nguyên từ
 * ({@code JPA} trong {@code JPAs} giữ nguyên). Tự tải lại khi file đổi → áp dụng cho job sau mà không restart.
 */
@Component
public class Lexicon {

    private static final Logger log = LoggerFactory.getLogger(Lexicon.class);

    private final Path file;
    private final boolean caseSensitive;
    private FileTime loadedAt;
    private Pattern pattern;
    private Map<String, String> replacements = Map.of();

    public Lexicon(
            @Value("${docreel.lexicon.path:config/lexicon.yaml}") Path file,
            @Value("${docreel.lexicon.case-sensitive:false}") boolean caseSensitive) {
        this.file = file;
        this.caseSensitive = caseSensitive;
    }

    /** Áp từ điển vào câu sẽ đọc (không dùng cho phụ đề). */
    public String apply(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Pattern p;
        Map<String, String> map;
        synchronized (this) {
            reloadIfChanged();
            p = pattern;
            map = replacements;
        }
        if (p == null) {
            return text;
        }
        Matcher m = p.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(map.get(fold(m.group()))));
        }
        m.appendTail(out);
        return out.toString();
    }

    private String fold(String s) {
        return caseSensitive ? s : s.toLowerCase(Locale.ROOT);
    }

    private void reloadIfChanged() {
        FileTime modified;
        try {
            modified = Files.exists(file) ? Files.getLastModifiedTime(file) : null;
        } catch (IOException e) {
            log.warn("Không đọc được thời gian sửa của {}: {}", file, e.getMessage());
            return;
        }
        if (Objects.equals(modified, loadedAt)) {
            return;
        }
        if (modified == null) {
            pattern = null;
            replacements = Map.of();
            loadedAt = null;
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            build(new Yaml().load(reader));
            log.info("Đã tải lexicon {} ({} mục)", file, replacements.size());
        } catch (IOException | RuntimeException e) {
            log.warn("Lexicon {} lỗi, giữ bản cũ: {}", file, e.getMessage());
        }
        loadedAt = modified; // file lỗi không bị đọc lại liên tục; giữ bản cũ đến khi file đổi
    }

    private void build(Object loaded) {
        Map<String, String> map = new HashMap<>();
        if (loaded instanceof Map<?, ?> raw) {
            raw.forEach((k, v) -> {
                if (k != null && v != null && !String.valueOf(k).isBlank()) {
                    map.put(fold(String.valueOf(k).strip()), String.valueOf(v));
                }
            });
        } else if (loaded != null) {
            throw new IllegalArgumentException("lexicon phải là map term: cách đọc");
        }
        if (map.isEmpty()) {
            pattern = null;
            replacements = Map.of();
            return;
        }
        String alternation = map.keySet().stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .map(Pattern::quote)
                .reduce((a, b) -> a + "|" + b)
                .orElseThrow();
        int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        pattern = Pattern.compile("(?<![\\p{L}\\p{N}_])(?:" + alternation + ")(?![\\p{L}\\p{N}_])", flags);
        replacements = Map.copyOf(map);
    }
}
