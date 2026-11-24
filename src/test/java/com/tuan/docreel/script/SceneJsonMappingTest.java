package com.tuan.docreel.script;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Khung record khớp đúng tên trường của mẫu JSON có sẵn (SPEC 9.1). Kiểm ràng buộc nội dung là việc của P1.
 */
class SceneJsonMappingTest {

    private static final Path SAMPLES = Path.of("samples");

    private final JsonMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    static Stream<Path> fullSamples() throws IOException {
        return list(SAMPLES.resolve("full"));
    }

    static Stream<Path> sceneSamples() throws IOException {
        return list(SAMPLES.resolve("scenes"));
    }

    private static Stream<Path> list(Path dir) throws IOException {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(f -> f.toString().endsWith(".json")).sorted().toList().stream();
        }
    }

    @ParameterizedTest
    @MethodSource("fullSamples")
    void mapsEveryFullSampleWithoutUnknownFields(Path file) {
        VideoScript script = mapper.readValue(file.toFile(), VideoScript.class);

        assertThat(script.topicId()).isNotBlank();
        assertThat(script.videoType()).isEqualTo(VideoType.SLIDES);
        assertThat(script.scenes())
                .isNotEmpty()
                .allSatisfy(s -> assertThat(s.id()).isNotBlank());
        assertThat(script.scenes().getFirst()).isInstanceOf(TitleScene.class);
        assertThat(script.scenes().getLast()).isInstanceOf(SummaryScene.class);
    }

    @ParameterizedTest
    @MethodSource("sceneSamples")
    void mapsEverySceneSampleToMatchingRecord(Path file) {
        JsonNode root = mapper.readTree(file.toFile());
        String type = root.get("scene").get("type").asString();

        Scene scene = mapper.treeToValue(root.get("scene"), Scene.class);

        assertThat(scene.getClass().getSimpleName()).isEqualToIgnoringCase(type + "Scene");
        assertThat(scene.narration()).isNotBlank();
    }

    @Test
    void mapsAllTenTypesWithTheirSpecificFields() {
        VideoScript script =
                mapper.readValue(SAMPLES.resolve("full/sample-all-scenes.json").toFile(), VideoScript.class);

        assertThat(script.scenes())
                .extracting(s -> s.getClass().getSimpleName())
                .containsExactlyInAnyOrder(
                        "TitleScene",
                        "DefinitionScene",
                        "PointsScene",
                        "StepsScene",
                        "CodeScene",
                        "CodeCompareScene",
                        "CompareScene",
                        "TableScene",
                        "QuizScene",
                        "SummaryScene");

        List<Scene> scenes = script.scenes();
        CodeScene code = find(scenes, CodeScene.class);
        assertThat(code.highlight()).isNotEmpty();
        assertThat(code.noteKind()).isNotNull();
        CompareScene compare = find(scenes, CompareScene.class);
        assertThat(compare.left().items()).isNotEmpty();
        QuizScene quiz = find(scenes, QuizScene.class);
        assertThat(quiz.answer()).isBetween(0, quiz.options().size() - 1);
        assertThat(quiz.revealNarration()).isNotBlank();
        assertThat(find(scenes, TableScene.class).rows())
                .allSatisfy(row -> assertThat(row)
                        .hasSameSizeAs(find(scenes, TableScene.class).columns()));
        assertThat(find(scenes, CodeCompareScene.class).bad().code()).isNotBlank();
        assertThat(find(scenes, PointsScene.class).points().getFirst().heading())
                .isNotBlank();
        assertThat(find(scenes, DefinitionScene.class).term()).isNotBlank();
        assertThat(find(scenes, TitledScene.class).accent()).isNotBlank();
    }

    @Test
    void readsOriginAndEnumValuesInLowercase() {
        String json = """
                {"topicId":"R-01","track":"t","title":"x","version":1,"videoType":"SLIDES","scenes":[
                  {"id":"a","type":"compare","narration":"n","accent":"A","left":{"heading":"h","kind":"bad","items":["i"]},
                   "right":{"heading":"h","items":["i"]}},
                  {"id":"b","type":"code","narration":"n","accent":"A","lang":"java","code":"x","noteKind":"warn"}],
                 "origin":{"channel":"telegram","chatId":"1","messageId":"2"}}
                """;

        VideoScript script = mapper.readValue(json, VideoScript.class);

        assertThat(script.origin()).isEqualTo(new Origin("telegram", "1", "2"));
        assertThat(((CompareScene) script.scenes().get(0)).left().kind()).isEqualTo(CompareKind.BAD);
        assertThat(((CompareScene) script.scenes().get(0)).right().kind()).isNull();
        assertThat(((CodeScene) script.scenes().get(1)).noteKind()).isEqualTo(NoteKind.WARN);
    }

    @Test
    void rejectsUnknownSceneType() {
        String json = """
                {"id":"a","type":"video","narration":"n"}
                """;

        assertThatThrownBy(() -> mapper.readValue(json, Scene.class)).hasMessageContaining("video");
    }

    private static <T> T find(List<Scene> scenes, Class<T> type) {
        return scenes.stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findFirst()
                .orElseThrow();
    }
}
