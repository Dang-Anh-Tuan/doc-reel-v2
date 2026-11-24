package com.tuan.docreel.render;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tuan.docreel.script.TitleScene;
import com.tuan.docreel.script.VideoScript;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SlideRendererTest {

    private final SlideRenderer renderer = new SlideRenderer();

    @Test
    void modelHasVideoSceneIndexTotalFrame() {
        SampleFiles.Sample s = SampleFiles.scene("quiz", "ok");

        Map<String, Object> model = renderer.model(s.video(), s.scene(), 9, 10, 2);

        assertThat(model).containsKeys("video", "scene", "index", "total", "frame");
        assertThat(model.get("scene")).isSameAs(s.scene());
        assertThat(model).containsEntry("index", 9).containsEntry("total", 10).containsEntry("frame", 2);
        assertThat(video(model))
                .containsEntry("topicId", "R-08")
                .containsEntry("track", "React")
                .containsEntry("title", "Rules of Hooks & useReducer")
                .containsEntry("theme", "midnight-brass");
    }

    @Test
    void nullThemeFallsBackToDefaultAndFrameToOne() {
        SampleFiles.Sample s = SampleFiles.scene("title", "ok");
        VideoScript v = new VideoScript("T-1", "Java", "t", null, null, null, null, null, null);

        Map<String, Object> model = renderer.model(v, s.scene(), 1, 3, null);

        assertThat(video(model)).containsEntry("theme", SlideRenderer.DEFAULT_THEME);
        assertThat(model).containsEntry("frame", 1);
    }

    @Test
    void unknownThemeIsRejectedBeforeRendering() {
        SampleFiles.Sample s = SampleFiles.scene("title", "ok");
        VideoScript v = new VideoScript("T-1", "Java", "t", null, null, "../evil", null, null, null);

        assertThatThrownBy(() -> renderer.render(v, s.scene(), 1, 3, 1))
                .isInstanceOf(UnknownThemeException.class)
                .hasMessageContaining("../evil");
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
    void rendersEveryTypeWithLocalAssets(String type) {
        SampleFiles.Sample s = SampleFiles.scene(type, "ok");

        String html = renderer.render(s.video(), s.scene(), 2, 10, 1);

        assertThat(html).contains("data-index=\"2\"").contains("data-total=\"10\"");
        assertThat(html).contains("href=\"/css/base.css\"").contains("/css/themes/midnight-brass.css");
        assertThat(html).doesNotContain("http://").doesNotContain("https://");
    }

    @Test
    void escapesHtmlInContent() {
        SampleFiles.Sample s = SampleFiles.scene("title", "ok");
        TitleScene t = (TitleScene) s.scene();
        TitleScene evil =
                new TitleScene(t.id(), t.narration(), null, t.chip(), "<script>x</script>", "a & b", t.hook());

        String html = renderer.render(s.video(), evil, 1, 3, 1);

        assertThat(html)
                .doesNotContain("<script>x</script>")
                .contains("&lt;script&gt;")
                .contains("a &amp; b");
    }

    @Test
    void quizFrameIsWrittenToBody() {
        SampleFiles.Sample s = SampleFiles.scene("quiz", "ok");

        assertThat(renderer.render(s.video(), s.scene(), 9, 10, 2)).contains("data-frame=\"2\"");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> video(Map<String, Object> model) {
        return (Map<String, Object>) model.get("video");
    }
}
