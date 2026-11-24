package com.tuan.docreel.render;

import com.tuan.docreel.script.Scene;
import com.tuan.docreel.script.VideoScript;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Xem nhanh 1 template với mẫu trong {@code samples/scenes} (chỉ profile {@code dev}). */
@RestController
@Profile("dev")
@RequestMapping("/dev/preview")
public class DevPreviewController {

    private static final Set<String> VARIANTS = Set.of("ok", "overflow");

    private final SlideRenderer renderer;
    private final Path samplesDir;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public DevPreviewController(SlideRenderer renderer, @Value("${docreel.dev.samples-dir:samples}") Path samplesDir) {
        this.renderer = renderer;
        this.samplesDir = samplesDir;
    }

    @GetMapping(value = "/{type}", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> preview(
            @PathVariable String type,
            @RequestParam(defaultValue = "ok") String variant,
            @RequestParam(defaultValue = "1") int frame,
            @RequestParam(required = false) String theme)
            throws IOException {
        if (!type.matches("[A-Za-z]+") || !VARIANTS.contains(variant)) {
            return ResponseEntity.badRequest().build();
        }
        Path file = samplesDir.resolve("scenes").resolve(type + "." + variant + ".json");
        if (!Files.isRegularFile(file)) {
            return ResponseEntity.notFound().build();
        }
        JsonNode root = mapper.readTree(Files.readString(file));
        VideoScript video = mapper.treeToValue(root.get("video"), VideoScript.class);
        if (theme != null) {
            video = new VideoScript(
                    video.topicId(),
                    video.track(),
                    video.title(),
                    video.version(),
                    video.videoType(),
                    theme,
                    video.sources(),
                    video.scenes(),
                    video.origin());
        }
        Scene scene = mapper.treeToValue(root.get("scene"), Scene.class);
        return ResponseEntity.ok(renderer.render(video, scene, 1, 10, frame));
    }
}
