package com.tuan.docreel.video;

import com.tuan.docreel.job.JobState;
import com.tuan.docreel.pipeline.Step;
import com.tuan.docreel.pipeline.StepContext;
import com.tuan.docreel.pipeline.StepResult;
import com.tuan.docreel.script.QuizScene;
import com.tuan.docreel.script.Scene;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Bước VOICED → ASSEMBLED: ghép {@code {id}.png} + {@code {id}.mp3} (quiz: thêm {@code {id}-reveal.png/.mp3}) và
 * {@code subtitles.srt} thành {@code work/{jobId}/video.mp4}. Tên file là hợp đồng với RenderStep / VoiceStep.
 */
@Component
public class AssembleStep implements Step {

    public static final String VIDEO_FILE = "video.mp4";
    static final String SRT_FILE = "subtitles.srt";
    private static final String REVEAL_SUFFIX = "-reveal";

    private static final Logger log = LoggerFactory.getLogger(AssembleStep.class);

    private final VideoAssembler assembler;

    public AssembleStep(VideoAssembler assembler) {
        this.assembler = assembler;
    }

    @Override
    public boolean supports(JobState state) {
        return state == JobState.VOICED;
    }

    @Override
    public StepResult execute(StepContext ctx) {
        MDC.put("jobId", ctx.jobId().toString());
        try {
            Path output = ctx.workDir().resolve(VIDEO_FILE);
            if (nonEmpty(output)) {
                log.info("Đã có {}, bỏ qua ghép", VIDEO_FILE);
                return new StepResult(JobState.ASSEMBLED, true);
            }
            Path srt = ctx.workDir().resolve(SRT_FILE);
            // ghi ra file tạm rồi mới đổi tên: video.mp4 có mặt nghĩa là đã ghép xong
            Path tmp = ctx.workDir().resolve("video.part.mp4");
            assembler.assemble(mediaOf(ctx), Files.isRegularFile(srt) ? srt : null, tmp);
            Files.move(tmp, output, StandardCopyOption.REPLACE_EXISTING);
            return new StepResult(JobState.ASSEMBLED, false);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            MDC.remove("jobId");
        }
    }

    static List<SceneMedia> mediaOf(StepContext ctx) {
        List<SceneMedia> media = new ArrayList<>();
        for (Scene scene : ctx.script().scenes()) {
            List<String> units =
                    scene instanceof QuizScene ? List.of(scene.id(), scene.id() + REVEAL_SUFFIX) : List.of(scene.id());
            List<Path> images = new ArrayList<>();
            List<Path> audios = new ArrayList<>();
            for (String unit : units) {
                images.add(require(ctx.workDir().resolve(unit + ".png")));
                audios.add(require(ctx.workDir().resolve(unit + ".mp3")));
            }
            media.add(new SceneMedia(scene.id(), images, audios));
        }
        return media;
    }

    private static Path require(Path file) {
        if (!nonEmpty(file)) {
            throw new IllegalStateException("Thiếu file cho bước ghép: " + file.getFileName());
        }
        return file;
    }

    private static boolean nonEmpty(Path p) {
        try {
            return Files.isRegularFile(p) && Files.size(p) > 0;
        } catch (IOException e) {
            return false;
        }
    }
}
