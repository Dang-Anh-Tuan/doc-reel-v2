package com.tuan.docreel.script;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.common.Result;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class ScriptParserTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private final ScriptParser parser = new ScriptParser();

    private static byte[] sample() throws Exception {
        return Files.readAllBytes(Path.of("samples/full/sample-all-scenes.json"));
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void parsesFullSampleIntoTenScenes() throws Exception {
        Result<VideoScript, List<FieldError>> r = parser.parse(sample());

        assertThat(r.isOk()).isTrue();
        assertThat(r.value().scenes()).hasSize(10);
        assertThat(r.value().topicId()).isEqualTo("R-08");
    }

    @Test
    void rejectsUnknownFieldWithItsPath() throws Exception {
        ObjectNode root = (ObjectNode) MAPPER.readTree(sample());
        root.put("extra", 1);

        Result<VideoScript, List<FieldError>> r = parser.parse(MAPPER.writeValueAsBytes(root));

        assertThat(r.error()).extracting(FieldError::path).containsExactly("extra");
    }

    @Test
    void explainsUnknownSceneTypeAndListsValidOnes() throws Exception {
        ObjectNode root = (ObjectNode) MAPPER.readTree(sample());
        ((ObjectNode) root.get("scenes").get(2)).put("type", "video");

        Result<VideoScript, List<FieldError>> r = parser.parse(MAPPER.writeValueAsBytes(root));

        assertThat(r.error()).hasSize(1);
        assertThat(r.error().getFirst().path()).isEqualTo("scenes[2].type");
        assertThat(r.error().getFirst().message())
                .contains("video")
                .contains("quiz")
                .contains("codeCompare");
    }

    @Test
    void reportsMissingTypeOnScene() throws Exception {
        ObjectNode root = (ObjectNode) MAPPER.readTree(sample());
        ((ObjectNode) root.get("scenes").get(2)).remove("type");

        Result<VideoScript, List<FieldError>> r = parser.parse(MAPPER.writeValueAsBytes(root));

        assertThat(r.error()).extracting(FieldError::path).containsExactly("scenes[2].type");
    }

    @Test
    void reportsMalformedAndEmptyJsonAtRoot() {
        assertThat(parser.parse(utf8("{ nope")).error())
                .extracting(FieldError::path)
                .containsExactly("$");
        assertThat(parser.parse(new byte[0]).error())
                .extracting(FieldError::path)
                .containsExactly("$");
        assertThat(parser.parse(null).error()).extracting(FieldError::path).containsExactly("$");
    }

    @Test
    void rejectsNonObjectRootAndTrailingContentAndDuplicateKeys() {
        assertThat(parser.parse(utf8("[1]")).isOk()).isFalse();
        assertThat(parser.parse(utf8("{} {}")).isOk()).isFalse();
        assertThat(parser.parse(utf8("{\"a\":1,\"a\":2}")).isOk()).isFalse();
    }

    @Test
    void reportsWrongValueTypeAtFieldPath() throws Exception {
        ObjectNode root = (ObjectNode) MAPPER.readTree(sample());
        ((ObjectNode) root.get("scenes").get(8)).put("answer", "one");

        Result<VideoScript, List<FieldError>> r = parser.parse(MAPPER.writeValueAsBytes(root));

        assertThat(r.error()).extracting(FieldError::path).containsExactly("scenes[8].answer");
    }

    @Test
    void reportsInvalidEnumValue() throws Exception {
        ObjectNode root = (ObjectNode) MAPPER.readTree(sample());
        ((ObjectNode) root.get("scenes").get(4)).put("noteKind", "loud");

        Result<VideoScript, List<FieldError>> r = parser.parse(MAPPER.writeValueAsBytes(root));

        assertThat(r.error()).extracting(FieldError::path).containsExactly("scenes[4].noteKind");
    }

    @Test
    void validatorRejectsNullScript() {
        assertThat(new ScriptValidator().validate(null))
                .extracting(FieldError::path)
                .containsExactly("$");
    }
}
