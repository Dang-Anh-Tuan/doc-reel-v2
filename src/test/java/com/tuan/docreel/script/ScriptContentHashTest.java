package com.tuan.docreel.script;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class ScriptContentHashTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private final ScriptParser parser = new ScriptParser();

    private ObjectNode sample() throws Exception {
        return (ObjectNode) MAPPER.readTree(Files.readAllBytes(Path.of("samples/full/sample-all-scenes.json")));
    }

    private String hash(JsonNode node) throws Exception {
        return ScriptParser.contentHash(
                parser.parse(MAPPER.writeValueAsBytes(node)).value());
    }

    @Test
    void ignoresOrigin() throws Exception {
        ObjectNode a = sample();
        ObjectNode b = sample();
        b.putObject("origin").put("channel", "telegram").put("chatId", "1").put("messageId", "2");

        assertThat(hash(b)).isEqualTo(hash(a));
    }

    @Test
    void ignoresJsonKeyOrder() throws Exception {
        ObjectNode a = sample();
        ObjectNode reordered = MAPPER.createObjectNode();
        List<String> names = a.propertyNames().stream().toList();
        for (int i = names.size() - 1; i >= 0; i--) {
            reordered.set(names.get(i), a.get(names.get(i)));
        }

        assertThat(hash(reordered)).isEqualTo(hash(a));
    }

    @Test
    void changesWhenOneNarrationCharacterChanges() throws Exception {
        ObjectNode a = sample();
        ObjectNode b = sample();
        ObjectNode scene = (ObjectNode) b.get("scenes").get(1);
        scene.put("narration", scene.get("narration").asString() + "!");

        assertThat(hash(b)).isNotEqualTo(hash(a));
    }

    @Test
    void treatsMissingThemeAsDefaultTheme() throws Exception {
        ObjectNode withDefault = sample();
        withDefault.put("theme", "midnight-brass");
        ObjectNode withoutTheme = sample();
        withoutTheme.remove("theme");
        ObjectNode otherTheme = sample();
        otherTheme.put("theme", "petrol-sage");

        assertThat(hash(withoutTheme)).isEqualTo(hash(withDefault));
        assertThat(hash(otherTheme)).isNotEqualTo(hash(withDefault));
    }

    @Test
    void producesSha256Hex() throws Exception {
        assertThat(hash(sample())).matches("[0-9a-f]{64}");
    }
}
