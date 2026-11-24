package com.tuan.docreel.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tuan.docreel.script.Scene;
import com.tuan.docreel.script.VideoScript;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Chụp thật bằng Chromium: 10 mẫu ok không lỗi, overflow có lỗi, ghép contact sheet. */
@Tag("media")
class TemplateGalleryTest {

    private static final String[] TYPES = {
        "title", "points", "code", "codeCompare", "compare", "definition", "steps", "table", "quiz", "summary"
    };
    private static final Path OUT = Path.of("build", "gallery");

    private static final SlideRenderer RENDERER = new SlideRenderer();
    private static final SlideCapturer CAPTURER = new SlideCapturer();

    @AfterAll
    static void closeBrowser() {
        CAPTURER.close();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "title",
                "points",
                "code",
                "codeCompare",
                "compare",
                "definition",
                "steps",
                "table",
                "quiz",
                "summary"
            })
    void okSampleHasNoProblems(String type) throws IOException {
        SampleFiles.Sample s = SampleFiles.scene(type, "ok");

        SlideCapturer.Capture c = CAPTURER.inspect(RENDERER.render(s.video(), s.scene(), 1, 10, 1));

        assertThat(c.problems()).as(type).isEmpty();
        assertPng(c.frames().getFirst());
        Files.createDirectories(OUT);
        for (int i = 0; i < c.frames().size(); i++) {
            Files.write(OUT.resolve(type + "-" + (i + 1) + ".png"), c.frames().get(i));
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "title",
                "points",
                "code",
                "codeCompare",
                "compare",
                "definition",
                "steps",
                "table",
                "quiz",
                "summary"
            })
    void overflowSampleReportsProblems(String type) {
        SampleFiles.Sample s = SampleFiles.scene(type, "overflow");
        String html = RENDERER.render(s.video(), s.scene(), 1, 10, 1);

        assertThat(CAPTURER.inspect(html).problems()).as(type).isNotEmpty();
        assertThatThrownBy(() -> CAPTURER.capture(s.scene().id(), html)).isInstanceOf(SlideOverflowException.class);
    }

    @Test
    void maxLimitsScriptHasNoProblems() throws IOException {
        JsonMapper mapper = JsonMapper.builder().build();
        JsonNode root = mapper.readTree(Files.readString(Path.of("samples", "full", "test-max-limits.json")));
        VideoScript script = mapper.treeToValue(root, VideoScript.class);
        int total = script.scenes().size();

        for (int i = 0; i < total; i++) {
            Scene scene = script.scenes().get(i);
            SlideCapturer.Capture c = CAPTURER.inspect(RENDERER.render(script, scene, i + 1, total, 1));
            assertThat(c.problems()).as(scene.id()).isEmpty();
        }
    }

    @Test
    void quizProducesTwoDifferentFrames() {
        SampleFiles.Sample s = SampleFiles.scene("quiz", "ok");

        SlideCapturer.Capture c = CAPTURER.inspect(RENDERER.render(s.video(), s.scene(), 9, 10, 1));

        assertThat(c.frames()).hasSize(2);
        assertThat(c.frames().get(0)).isNotEqualTo(c.frames().get(1));
    }

    @Test
    void unknownThemeIsBlockedBeforeRender() {
        SampleFiles.Sample s = SampleFiles.scene("title", "ok");
        VideoScript v = new VideoScript("T", "t", "t", null, null, "no-such-theme", null, null, null);

        assertThatThrownBy(() -> RENDERER.render(v, s.scene(), 1, 1, 1)).isInstanceOf(UnknownThemeException.class);
    }

    /** Ghép 10 ảnh ok thành 1 tấm để duyệt bằng mắt (cổng G1). */
    @Test
    void contactSheet() throws IOException {
        List<BufferedImage> tiles = new ArrayList<>();
        for (String type : TYPES) {
            SampleFiles.Sample s = SampleFiles.scene(type, "ok");
            SlideCapturer.Capture c = CAPTURER.inspect(RENDERER.render(s.video(), s.scene(), 1, 10, 1));
            tiles.add(ImageIO.read(new ByteArrayInputStream(c.frames().getFirst())));
        }
        int w = 270;
        int h = 480;
        int cols = 5;
        int pad = 12;
        int rows = (tiles.size() + cols - 1) / cols;
        BufferedImage sheet =
                new BufferedImage(cols * (w + pad) + pad, rows * (h + pad) + pad, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setColor(new Color(0x20, 0x20, 0x20));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        for (int i = 0; i < tiles.size(); i++) {
            g.drawImage(tiles.get(i), pad + (i % cols) * (w + pad), pad + (i / cols) * (h + pad), w, h, null);
        }
        g.dispose();
        Files.createDirectories(OUT);
        Path file = OUT.resolve("contact-sheet.png");
        ImageIO.write(sheet, "png", file.toFile());

        assertThat(file).exists();
    }

    private static void assertPng(byte[] bytes) throws IOException {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(img.getWidth()).isEqualTo(SlideCapturer.WIDTH);
        assertThat(img.getHeight()).isEqualTo(SlideCapturer.HEIGHT);
    }
}
