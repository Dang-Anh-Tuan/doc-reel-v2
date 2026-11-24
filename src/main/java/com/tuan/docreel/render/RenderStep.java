package com.tuan.docreel.render;

import com.tuan.docreel.job.JobState;
import com.tuan.docreel.pipeline.Step;
import com.tuan.docreel.pipeline.StepContext;
import com.tuan.docreel.pipeline.StepResult;
import com.tuan.docreel.script.QuizScene;
import com.tuan.docreel.script.Scene;
import com.tuan.docreel.script.VideoScript;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Bước RECEIVED → RENDERED: mỗi scene thành {@code work/{jobId}/{sceneId}.png}; {@code quiz} có thêm
 * {@code {sceneId}-reveal.png} (frame đáp án). Idempotent: đủ PNG khác rỗng thì bỏ qua. Slide tràn →
 * {@link SlideOverflowException} (không retry).
 */
@Component
public class RenderStep implements Step {

    public static final String REVEAL_SUFFIX = "-reveal";

    private static final Logger log = LoggerFactory.getLogger(RenderStep.class);

    private final SlideRenderer renderer;
    private final SlideCapturer capturer;

    public RenderStep(SlideRenderer renderer, SlideCapturer capturer) {
        this.renderer = renderer;
        this.capturer = capturer;
    }

    /** Tên file PNG theo thứ tự timeline của một scene (quiz: 2 file). */
    public static List<String> imageNames(Scene scene) {
        return scene instanceof QuizScene
                ? List.of(scene.id() + ".png", scene.id() + REVEAL_SUFFIX + ".png")
                : List.of(scene.id() + ".png");
    }

    @Override
    public boolean supports(JobState state) {
        return state == JobState.RECEIVED;
    }

    @Override
    public StepResult execute(StepContext ctx) {
        MDC.put("jobId", ctx.jobId().toString());
        try {
            VideoScript script = ctx.script();
            List<Scene> scenes = script.scenes();
            boolean complete = scenes.stream()
                    .flatMap(s -> imageNames(s).stream())
                    .allMatch(name -> isNonEmpty(ctx.workDir().resolve(name)));
            if (complete) {
                log.info("Đã có đủ PNG, bỏ qua render");
                return new StepResult(JobState.RENDERED, true);
            }
            for (int i = 0; i < scenes.size(); i++) {
                Scene scene = scenes.get(i);
                String html = renderer.render(script, scene, i + 1, scenes.size(), null);
                SlideCapturer.Capture capture = capturer.capture(scene.id(), html);
                List<String> names = imageNames(scene);
                if (capture.frames().size() != names.size()) {
                    throw new IllegalStateException("Scene " + scene.id() + ": chụp được "
                            + capture.frames().size() + " frame, cần " + names.size());
                }
                for (int f = 0; f < names.size(); f++) {
                    write(ctx.workDir().resolve(names.get(f)), capture.frames().get(f));
                }
            }
            return new StepResult(JobState.RENDERED, false);
        } finally {
            MDC.remove("jobId");
        }
    }

    /** Ghi qua file tạm rồi đổi tên: crash giữa chừng không để lại PNG dở mà lần sau tưởng hợp lệ. */
    private static void write(Path target, byte[] bytes) {
        try {
            Path tmp = target.resolveSibling(target.getFileName() + ".part");
            Files.write(tmp, bytes);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isNonEmpty(Path p) {
        try {
            return Files.isRegularFile(p) && Files.size(p) > 0;
        } catch (IOException e) {
            return false;
        }
    }
}
