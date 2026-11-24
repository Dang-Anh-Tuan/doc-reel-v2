package com.tuan.docreel.script;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Kiểm tra ràng buộc của {@link VideoScript} theo SPEC 9.1: độ dài (đếm theo code point), số phần tử, luật chéo
 * giữa các trường/scene. Gom toàn bộ lỗi, mỗi lỗi có đường dẫn trường.
 */
public class ScriptValidator {

    static final Set<String> THEMES = Set.of(
            "blueprint-cream",
            "deep-ocean-coral",
            "indigo-dust",
            "midnight-brass",
            "navy-terracotta",
            "petrol-sage",
            "prussian-olive",
            "slate-mustard",
            "storm-rose",
            "teal-navy-copper");

    private static final Pattern TOPIC_ID = Pattern.compile("^[A-Z]+-\\d{2,}$");
    private static final Pattern SCENE_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$");
    private static final Pattern URL = Pattern.compile("^https?://\\S+$");
    private static final int MAX_CODE_LINE = 40;

    public List<FieldError> validate(VideoScript script) {
        Errors e = new Errors();
        if (script == null) {
            e.add("$", "kịch bản rỗng");
            return e.list;
        }
        validateVideo(script, e);
        validateScenes(script.scenes(), e);
        return e.list;
    }

    private void validateVideo(VideoScript s, Errors e) {
        if (isBlank(s.topicId())) {
            e.add("topicId", "bắt buộc");
        } else if (!TOPIC_ID.matcher(s.topicId()).matches()) {
            e.add("topicId", "phải có dạng chữ HOA-số (2+ chữ số), vd R-08");
        }
        e.text("track", s.track(), true, 20);
        e.text("title", s.title(), true, 80);
        if (s.version() == null) {
            e.add("version", "bắt buộc");
        } else if (s.version() < 1) {
            e.add("version", "phải >= 1");
        }
        if (s.videoType() == null) {
            e.add("videoType", "bắt buộc");
        }
        if (s.theme() != null && !THEMES.contains(s.theme())) {
            e.add("theme", "theme không có trong danh sách: " + String.join(", ", new java.util.TreeSet<>(THEMES)));
        }
        if (s.sources() != null) {
            for (int i = 0; i < s.sources().size(); i++) {
                String src = s.sources().get(i);
                if (src == null || !URL.matcher(src).matches()) {
                    e.add("sources[" + i + "]", "phải là URL http(s)");
                }
            }
        }
    }

    private void validateScenes(List<Scene> scenes, Errors e) {
        if (scenes == null || scenes.isEmpty()) {
            e.add("scenes", "bắt buộc, 4-10 scene");
            return;
        }
        if (scenes.size() < 4 || scenes.size() > 10) {
            e.add("scenes", "cần 4-10 scene, đang có " + scenes.size());
        }
        if (!(scenes.getFirst() instanceof TitleScene)) {
            e.add("scenes", "scene đầu phải là type title");
        }
        if (!(scenes.getLast() instanceof SummaryScene)) {
            e.add("scenes", "scene cuối phải là type summary");
        }
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < scenes.size(); i++) {
            Scene scene = scenes.get(i);
            String p = "scenes[" + i + "]";
            if (scene == null) {
                e.add(p, "scene rỗng");
                continue;
            }
            validateCommon(scene, p, ids, e);
            if (scene instanceof TitledScene t) {
                e.text(p + ".accent", t.accent(), true, 28);
                int total = length(t.accent()) + length(t.titleRest());
                if (!isBlank(t.accent()) && total > 28) {
                    e.add(p + ".accent", "accent + titleRest tối đa 28 ký tự, đang có " + total);
                }
            }
            validateSpecific(scene, p, e);
        }
    }

    private void validateCommon(Scene scene, String p, Set<String> ids, Errors e) {
        if (isBlank(scene.id())) {
            e.add(p + ".id", "bắt buộc");
        } else if (!SCENE_ID.matcher(scene.id()).matches()) {
            e.add(p + ".id", "chỉ gồm chữ, số, '-' và '_' (dùng làm tên file)");
        } else if (!ids.add(scene.id())) {
            e.add(p + ".id", "trùng id '" + scene.id() + "'");
        }
        if (isBlank(scene.narration())) {
            e.add(p + ".narration", "bắt buộc");
        }
    }

    private void validateSpecific(Scene scene, String p, Errors e) {
        switch (scene) {
            case TitleScene s -> {
                e.text(p + ".chip", s.chip(), false, 24);
                e.text(p + ".hook", s.hook(), true, 60);
            }
            case DefinitionScene s -> {
                e.text(p + ".term", s.term(), true, 20);
                e.text(p + ".expansion", s.expansion(), false, 40);
                e.text(p + ".definition", s.definition(), true, 120);
                e.text(p + ".example", s.example(), false, 60);
            }
            case PointsScene s -> points(s, p, e);
            case StepsScene s -> e.textList(p + ".steps", s.steps(), 3, 5, 40);
            case CodeScene s -> code(s, p, e);
            case CodeCompareScene s -> codeCompare(s, p, e);
            case CompareScene s -> {
                column(p + ".left", s.left(), e);
                column(p + ".right", s.right(), e);
                e.text(p + ".note", s.note(), false, 70);
            }
            case TableScene s -> table(s, p, e);
            case QuizScene s -> quiz(s, p, e);
            case SummaryScene s -> {
                e.textList(p + ".bullets", s.bullets(), 1, 4, 40);
                e.text(p + ".outro", s.outro(), false, 70);
            }
        }
    }

    private void points(PointsScene s, String p, Errors e) {
        if (s.points() == null || s.points().isEmpty() || s.points().size() > 3) {
            e.add(p + ".points", "cần 1-3 ý");
        }
        if (s.points() == null) {
            return;
        }
        for (int i = 0; i < s.points().size(); i++) {
            PointsScene.Point pt = s.points().get(i);
            String q = p + ".points[" + i + "]";
            if (pt == null) {
                e.add(q, "ý rỗng");
                continue;
            }
            e.text(q + ".heading", pt.heading(), true, 24);
            e.text(q + ".detail", pt.detail(), true, 70);
        }
    }

    private void code(CodeScene s, String p, Errors e) {
        e.text(p + ".lang", s.lang(), true, 30);
        e.text(p + ".file", s.file(), false, 60);
        int lines = e.codeBlock(p + ".code", s.code(), 8);
        if (s.highlight() != null && lines > 0) {
            for (int i = 0; i < s.highlight().size(); i++) {
                Integer h = s.highlight().get(i);
                if (h == null || h < 1 || h > lines) {
                    e.add(p + ".highlight[" + i + "]", "dòng highlight phải trong 1-" + lines + " (đếm từ 1)");
                }
            }
        }
        e.text(p + ".note", s.note(), false, 70);
    }

    private void codeCompare(CodeCompareScene s, String p, Errors e) {
        e.text(p + ".lang", s.lang(), true, 30);
        block(p + ".bad", s.bad(), e);
        block(p + ".good", s.good(), e);
        e.text(p + ".note", s.note(), false, 70);
    }

    private void block(String p, CodeCompareScene.CodeBlock b, Errors e) {
        if (b == null) {
            e.add(p, "bắt buộc");
            return;
        }
        e.text(p + ".label", b.label(), true, 24);
        e.codeBlock(p + ".code", b.code(), 5);
    }

    private void column(String p, CompareScene.Column c, Errors e) {
        if (c == null) {
            e.add(p, "bắt buộc");
            return;
        }
        e.text(p + ".heading", c.heading(), true, 16);
        e.textList(p + ".items", c.items(), 1, 3, 30);
    }

    private void table(TableScene s, String p, Errors e) {
        e.textList(p + ".columns", s.columns(), 2, 3, 18);
        if (s.rows() == null || s.rows().isEmpty() || s.rows().size() > 4) {
            e.add(p + ".rows", "cần 1-4 hàng");
        }
        if (s.rows() == null) {
            return;
        }
        int cols = s.columns() == null ? -1 : s.columns().size();
        for (int r = 0; r < s.rows().size(); r++) {
            List<String> row = s.rows().get(r);
            String q = p + ".rows[" + r + "]";
            if (row == null) {
                e.add(q, "hàng rỗng");
                continue;
            }
            if (cols >= 0 && row.size() != cols) {
                e.add(q, "hàng có " + row.size() + " ô, cần đúng " + cols + " (bằng số cột)");
            }
            for (int c = 0; c < row.size(); c++) {
                e.text(q + "[" + c + "]", row.get(c), false, 18);
            }
        }
    }

    private void quiz(QuizScene s, String p, Errors e) {
        e.text(p + ".question", s.question(), true, 80);
        e.textList(p + ".options", s.options(), 2, 4, 30);
        if (s.answer() == null) {
            e.add(p + ".answer", "bắt buộc");
        } else {
            int n = s.options() == null ? 0 : s.options().size();
            if (s.answer() < 0 || s.answer() >= n) {
                e.add(p + ".answer", "answer là index từ 0, phải trong 0-" + (n - 1) + ", đang là " + s.answer());
            }
        }
        e.text(p + ".explain", s.explain(), true, 80);
        if (isBlank(s.revealNarration())) {
            e.add(p + ".revealNarration", "bắt buộc");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Độ dài theo code point (chữ có dấu = 1). */
    private static int length(String s) {
        return s == null ? 0 : s.codePointCount(0, s.length());
    }

    private static final class Errors {
        final List<FieldError> list = new ArrayList<>();

        void add(String path, String message) {
            list.add(new FieldError(path, message));
        }

        void text(String path, String value, boolean required, int max) {
            if (isBlank(value)) {
                if (required) {
                    add(path, "bắt buộc");
                }
                return;
            }
            int len = length(value);
            if (len > max) {
                add(path, "tối đa " + max + " ký tự, đang có " + len);
            }
        }

        void textList(String path, List<String> values, int min, int max, int maxItem) {
            if (values == null || values.size() < min || values.size() > max) {
                add(path, "cần " + min + "-" + max + " phần tử" + (values == null ? "" : ", đang có " + values.size()));
            }
            if (values == null) {
                return;
            }
            for (int i = 0; i < values.size(); i++) {
                text(path + "[" + i + "]", values.get(i), true, maxItem);
            }
        }

        /** Trả số dòng của code (0 nếu thiếu); báo lỗi nếu quá số dòng hoặc có dòng dài hơn 40 ký tự. */
        int codeBlock(String path, String code, int maxLines) {
            if (isBlank(code)) {
                add(path, "bắt buộc");
                return 0;
            }
            List<String> lines = new ArrayList<>(List.of(code.split("\\R", -1)));
            if (lines.size() > 1 && lines.getLast().isEmpty()) {
                lines.removeLast();
            }
            if (lines.size() > maxLines) {
                add(path, "tối đa " + maxLines + " dòng, đang có " + lines.size());
            }
            for (int i = 0; i < lines.size(); i++) {
                int len = length(lines.get(i));
                if (len > MAX_CODE_LINE) {
                    add(path, "dòng " + (i + 1) + " dài " + len + " ký tự, tối đa " + MAX_CODE_LINE);
                    break;
                }
            }
            return lines.size();
        }
    }
}
