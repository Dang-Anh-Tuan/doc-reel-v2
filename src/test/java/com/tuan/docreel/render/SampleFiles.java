package com.tuan.docreel.render;

import com.tuan.docreel.script.Scene;
import com.tuan.docreel.script.VideoScript;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Đọc mẫu {@code samples/scenes/*.json} (dạng video + scene). */
final class SampleFiles {

    static final Path SCENES = Path.of("samples", "scenes");
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    record Sample(VideoScript video, Scene scene) {}

    private SampleFiles() {}

    static Sample load(Path file) {
        try {
            JsonNode root = MAPPER.readTree(Files.readString(file));
            return new Sample(
                    MAPPER.treeToValue(root.get("video"), VideoScript.class),
                    MAPPER.treeToValue(root.get("scene"), Scene.class));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Sample scene(String type, String variant) {
        return load(SCENES.resolve(type + "." + variant + ".json"));
    }
}
