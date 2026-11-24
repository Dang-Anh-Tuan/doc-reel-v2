package com.tuan.docreel.script;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.common.Result;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class ScriptValidatorTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private final ScriptParser parser = new ScriptParser();

    private static ObjectNode full() throws Exception {
        return (ObjectNode) MAPPER.readTree(Files.readAllBytes(Path.of("samples/full/sample-all-scenes.json")));
    }

    private List<String> errorPaths(Consumer<ObjectNode> mutation) throws Exception {
        ObjectNode root = full();
        mutation.accept(root);
        Result<VideoScript, List<FieldError>> r = parser.parse(MAPPER.writeValueAsBytes(root));
        return r.isOk() ? List.of() : r.error().stream().map(FieldError::path).toList();
    }

    private static ObjectNode scene(ObjectNode root, int i) {
        return (ObjectNode) root.get("scenes").get(i);
    }

    private static ObjectNode child(ObjectNode node, String field) {
        return (ObjectNode) node.get(field);
    }

    static Stream<Path> sceneOkSamples() throws Exception {
        try (Stream<Path> f = Files.list(Path.of("samples/scenes"))) {
            return f.filter(p -> p.toString().endsWith(".ok.json")).sorted().toList().stream();
        }
    }

    /** Bọc 1 scene mẫu vào video tối thiểu: title + scene + points + summary (4 scene). */
    @ParameterizedTest
    @MethodSource("sceneOkSamples")
    void acceptsSceneOkSampleWrappedInMinimalVideo(Path file) throws Exception {
        JsonNode sample = MAPPER.readTree(Files.readAllBytes(file));
        ObjectNode root = full();
        ArrayNode all = (ArrayNode) root.get("scenes");
        ArrayNode scenes = MAPPER.createArrayNode();
        ObjectNode s = (ObjectNode) sample.get("scene").deepCopy();
        String type = s.get("type").asString();
        s.put("id", "x-" + type);
        if (type.equals("title")) {
            scenes.add(s).add(all.get(1)).add(all.get(2)).add(all.get(9));
        } else if (type.equals("summary")) {
            scenes.add(all.get(0)).add(all.get(1)).add(all.get(2)).add(s);
        } else {
            scenes.add(all.get(0)).add(s).add(all.get(2)).add(all.get(9));
        }
        root.set("scenes", scenes);

        Result<VideoScript, List<FieldError>> r = parser.parse(MAPPER.writeValueAsBytes(root));

        assertThat(r.isOk()).as("%s", r.isOk() ? "" : r.error()).isTrue();
    }

    @Test
    void rejectsFirstSceneThatIsNotTitleAndLastThatIsNotSummary() throws Exception {
        assertThat(errorPaths(r -> ((ArrayNode) r.get("scenes")).remove(0))).containsExactly("scenes");
        assertThat(errorPaths(r -> ((ArrayNode) r.get("scenes")).remove(9))).containsExactly("scenes");
    }

    @Test
    void rejectsQuizAnswerOutOfRange() throws Exception {
        assertThat(errorPaths(r -> scene(r, 8).put("answer", 4))).containsExactly("scenes[8].answer");
        assertThat(errorPaths(r -> scene(r, 8).put("answer", 3))).isEmpty();
    }

    @Test
    void rejectsTableRowWithMissingColumnAndLongCell() throws Exception {
        assertThat(errorPaths(r -> ((ArrayNode) scene(r, 7).get("rows").get(1)).remove(2)))
                .containsExactly("scenes[7].rows[1]");
        assertThat(errorPaths(r -> ((ArrayNode) scene(r, 7).get("rows").get(0)).set(0, "x".repeat(19))))
                .containsExactly("scenes[7].rows[0][0]");
    }

    @Test
    void rejectsHighlightOutsideCodeLines() throws Exception {
        assertThat(errorPaths(r -> scene(r, 4).putArray("highlight").add(1).add(7)))
                .containsExactly("scenes[4].highlight[1]");
        assertThat(errorPaths(r -> scene(r, 4).putArray("highlight").add(6))).isEmpty();
    }

    @Test
    void limitsCodeToEightLinesOfFortyCodePoints() throws Exception {
        String eight = String.join("\n", Collections.nCopies(8, "x"));
        assertThat(errorPaths(r -> scene(r, 4).put("code", eight).putArray("highlight")))
                .isEmpty();
        assertThat(errorPaths(r -> scene(r, 4).put("code", eight + "\nx").putArray("highlight")))
                .containsExactly("scenes[4].code");
        assertThat(errorPaths(r -> scene(r, 4).put("code", "x".repeat(40)).putArray("highlight")))
                .isEmpty();
        assertThat(errorPaths(r -> scene(r, 4).put("code", "x".repeat(41)).putArray("highlight")))
                .containsExactly("scenes[4].code");
    }

    @Test
    void countsCharactersByCodePoint() throws Exception {
        // 17 chữ có dấu + 1 emoji (2 char UTF-16) = 18 code point, ô bảng cho phép ≤ 18
        String cell = "ế".repeat(17) + "😀";
        assertThat(cell.length()).isEqualTo(19);
        assertThat(errorPaths(r -> ((ArrayNode) scene(r, 7).get("rows").get(0)).set(0, cell)))
                .isEmpty();
        assertThat(errorPaths(r -> ((ArrayNode) scene(r, 7).get("rows").get(0)).set(0, cell + "a")))
                .containsExactly("scenes[7].rows[0][0]");
    }

    @Test
    void rejectsDuplicateIdAndUnsafeId() throws Exception {
        assertThat(errorPaths(r -> scene(r, 3).put("id", scene(r, 2).get("id").asString())))
                .containsExactly("scenes[3].id");
        assertThat(errorPaths(r -> scene(r, 3).put("id", "../evil"))).containsExactly("scenes[3].id");
    }

    @Test
    void rejectsVideoLevelViolations() throws Exception {
        assertThat(errorPaths(r -> r.put("topicId", "r8"))).containsExactly("topicId");
        assertThat(errorPaths(r -> r.put("track", "x".repeat(21)))).containsExactly("track");
        assertThat(errorPaths(r -> r.put("title", "x".repeat(81)))).containsExactly("title");
        assertThat(errorPaths(r -> r.put("version", 0))).containsExactly("version");
        assertThat(errorPaths(r -> r.remove("version"))).containsExactly("version");
        assertThat(errorPaths(r -> r.put("theme", "neon"))).containsExactly("theme");
        assertThat(errorPaths(r -> r.remove("theme"))).isEmpty();
        assertThat(errorPaths(r -> r.putArray("sources").add("ftp://x").add("https://ok.dev")))
                .containsExactly("sources[0]");
        assertThat(errorPaths(r -> r.remove("scenes"))).containsExactly("scenes");
    }

    @Test
    void rejectsSceneCountOutsideFourToTen() throws Exception {
        assertThat(errorPaths(r -> {
                    ArrayNode a = (ArrayNode) r.get("scenes");
                    for (int i = 8; i >= 2; i--) {
                        a.remove(i);
                    }
                }))
                .containsExactly("scenes");
    }

    @Test
    void rejectsFieldLimitsOfTitleDefinitionPointsStepsSummary() throws Exception {
        assertThat(errorPaths(r -> scene(r, 0).put("hook", "x".repeat(61)))).containsExactly("scenes[0].hook");
        assertThat(errorPaths(r -> scene(r, 0).put("chip", "x".repeat(25)))).containsExactly("scenes[0].chip");
        assertThat(errorPaths(r -> scene(r, 0).remove("hook"))).containsExactly("scenes[0].hook");
        assertThat(errorPaths(r -> scene(r, 1).put("term", "x".repeat(21)))).containsExactly("scenes[1].term");
        assertThat(errorPaths(r -> scene(r, 1).put("expansion", "x".repeat(41))))
                .containsExactly("scenes[1].expansion");
        assertThat(errorPaths(r -> scene(r, 1).put("definition", "x".repeat(121))))
                .containsExactly("scenes[1].definition");
        assertThat(errorPaths(r -> scene(r, 1).put("example", "x".repeat(61)))).containsExactly("scenes[1].example");
        assertThat(errorPaths(r -> scene(r, 2).putArray("points"))).containsExactly("scenes[2].points");
        assertThat(errorPaths(r -> ((ObjectNode) scene(r, 2).get("points").get(0)).put("heading", "x".repeat(25))))
                .containsExactly("scenes[2].points[0].heading");
        assertThat(errorPaths(r -> ((ObjectNode) scene(r, 2).get("points").get(0)).remove("detail")))
                .containsExactly("scenes[2].points[0].detail");
        assertThat(errorPaths(r -> ((ArrayNode) scene(r, 3).get("steps")).set(0, "x".repeat(41))))
                .containsExactly("scenes[3].steps[0]");
        assertThat(errorPaths(r ->
                        ((ArrayNode) scene(r, 3).get("steps")).add("a").add("b").add("c")))
                .containsExactly("scenes[3].steps");
        assertThat(errorPaths(r -> scene(r, 9).putArray("bullets"))).containsExactly("scenes[9].bullets");
        assertThat(errorPaths(r -> ((ArrayNode) scene(r, 9).get("bullets")).set(0, "x".repeat(41))))
                .containsExactly("scenes[9].bullets[0]");
        assertThat(errorPaths(r -> scene(r, 9).put("outro", "x".repeat(71)))).containsExactly("scenes[9].outro");
        assertThat(errorPaths(r -> scene(r, 9).remove("accent"))).containsExactly("scenes[9].accent");
    }

    @Test
    void rejectsFieldLimitsOfCodeCompareTableQuiz() throws Exception {
        assertThat(errorPaths(r -> scene(r, 4).put("note", "x".repeat(71)))).containsExactly("scenes[4].note");
        assertThat(errorPaths(r -> scene(r, 4).remove("lang"))).containsExactly("scenes[4].lang");
        assertThat(errorPaths(r -> scene(r, 4).remove("code"))).containsExactly("scenes[4].code");
        assertThat(errorPaths(r -> child(scene(r, 5), "bad").put("code", "a\nb\nc\nd\ne\nf")))
                .containsExactly("scenes[5].bad.code");
        assertThat(errorPaths(r -> child(scene(r, 5), "good").put("label", "x".repeat(25))))
                .containsExactly("scenes[5].good.label");
        assertThat(errorPaths(r -> scene(r, 5).remove("good"))).containsExactly("scenes[5].good");
        assertThat(errorPaths(r -> child(scene(r, 6), "left").put("heading", "x".repeat(17))))
                .containsExactly("scenes[6].left.heading");
        assertThat(errorPaths(r -> child(scene(r, 6), "right")
                        .putArray("items")
                        .add("a")
                        .add("b")
                        .add("c")
                        .add("d")))
                .containsExactly("scenes[6].right.items");
        assertThat(errorPaths(r -> child(scene(r, 6), "right").putArray("items").add("x".repeat(31))))
                .containsExactly("scenes[6].right.items[0]");
        assertThat(errorPaths(r -> scene(r, 6).remove("left"))).containsExactly("scenes[6].left");
        assertThat(errorPaths(r -> scene(r, 7).putArray("rows"))).containsExactly("scenes[7].rows");
        assertThat(errorPaths(r -> scene(r, 7).putArray("columns").add("a"))).contains("scenes[7].columns");
        assertThat(errorPaths(r -> scene(r, 8).put("question", "x".repeat(81)))).containsExactly("scenes[8].question");
        assertThat(errorPaths(r -> scene(r, 8).put("explain", "x".repeat(81)))).containsExactly("scenes[8].explain");
        assertThat(errorPaths(r -> scene(r, 8).put("revealNarration", " ")))
                .containsExactly("scenes[8].revealNarration");
        assertThat(errorPaths(r -> scene(r, 8).putArray("options").add("only")))
                .containsExactly("scenes[8].options", "scenes[8].answer");
        assertThat(errorPaths(r -> ((ArrayNode) scene(r, 8).get("options")).set(0, "x".repeat(31))))
                .containsExactly("scenes[8].options[0]");
        assertThat(errorPaths(r -> scene(r, 8).remove("answer"))).containsExactly("scenes[8].answer");
    }

    @Test
    void rejectsTitleLongerThan28CharactersInTotal() throws Exception {
        assertThat(errorPaths(r -> scene(r, 2).put("accent", "x".repeat(14)).put("titleRest", "y".repeat(14))))
                .isEmpty();
        assertThat(errorPaths(r -> scene(r, 2).put("accent", "x".repeat(14)).put("titleRest", "y".repeat(15))))
                .containsExactly("scenes[2].accent");
    }
}
