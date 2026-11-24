package com.tuan.docreel.script;

import com.tuan.docreel.common.Result;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.exc.InvalidTypeIdException;
import tools.jackson.databind.json.JsonMapper;

/** Đọc JSON thành {@link VideoScript} rồi kiểm tra bằng {@link ScriptValidator}. */
public class ScriptParser {

    private static final String ROOT = "$";
    private static final String VALID_TYPES =
            "title, definition, points, steps, code, codeCompare, compare, table, quiz, summary";

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private final ScriptValidator validator = new ScriptValidator();

    /** Trả kịch bản hợp lệ, hoặc toàn bộ lỗi tìm thấy (lỗi cú pháp/trường lạ chỉ báo được lỗi đầu tiên). */
    public Result<VideoScript, List<FieldError>> parse(byte[] json) {
        if (json == null || json.length == 0) {
            return Result.failure(List.of(new FieldError(ROOT, "JSON rỗng")));
        }
        VideoScript script;
        try {
            script = MAPPER.readValue(json, VideoScript.class);
        } catch (JacksonException e) {
            return Result.failure(List.of(toFieldError(e)));
        }
        List<FieldError> errors = validator.validate(script);
        return errors.isEmpty() ? Result.ok(script) : Result.failure(errors);
    }

    /**
     * SHA-256 (hex) của nội dung kịch bản, bỏ {@code origin} và không phụ thuộc thứ tự key JSON. Dùng cho khóa
     * idempotency {@code topicId:version:hash}.
     */
    public static String contentHash(VideoScript script) {
        JsonNode tree = MAPPER.valueToTree(withoutOrigin(script));
        Object canonical = canonical(tree);
        byte[] bytes = MAPPER.writeValueAsBytes(canonical);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 luôn có trong JDK", e);
        }
    }

    private static VideoScript withoutOrigin(VideoScript s) {
        return new VideoScript(
                s.topicId(),
                s.track(),
                s.title(),
                s.version(),
                s.videoType(),
                s.theme(),
                s.sources(),
                s.scenes(),
                null);
    }

    /** Đổi cây JSON sang Map/List với key đã sắp xếp, bỏ null để thiếu trường == trường null. */
    private static Object canonical(JsonNode node) {
        if (node.isObject()) {
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<String, JsonNode> e : node.properties()) {
                if (!e.getValue().isNull()) {
                    sorted.put(e.getKey(), canonical(e.getValue()));
                }
            }
            return sorted;
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            node.forEach(n -> list.add(canonical(n)));
            return list;
        }
        return node;
    }

    private static FieldError toFieldError(JacksonException e) {
        String path = path(e);
        if (e instanceof InvalidTypeIdException) {
            String base = path.equals(ROOT) ? "" : path + ".";
            return new FieldError(
                    base + "type",
                    "type không hợp lệ hoặc thiếu; chỉ nhận: " + VALID_TYPES + " (" + firstLine(e) + ")");
        }
        return new FieldError(path, firstLine(e));
    }

    private static String path(JacksonException e) {
        StringBuilder sb = new StringBuilder();
        for (JacksonException.Reference ref : e.getPath()) {
            if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            } else if (ref.getPropertyName() != null) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(ref.getPropertyName());
            }
        }
        return sb.isEmpty() ? ROOT : sb.toString();
    }

    private static String firstLine(JacksonException e) {
        String m = e.getOriginalMessage();
        if (m == null) {
            return "JSON không hợp lệ";
        }
        int nl = m.indexOf('\n');
        return nl >= 0 ? m.substring(0, nl) : m;
    }
}
