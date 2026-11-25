package com.tuan.docreel.youtube;

import com.tuan.docreel.job.JobState;
import com.tuan.docreel.pipeline.Step;
import com.tuan.docreel.pipeline.StepContext;
import com.tuan.docreel.pipeline.StepResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Bước cuối: ASSEMBLED → UPLOADED. Idempotent: id YouTube ghi vào {@code work/{jobId}/youtube-id.txt}; có sẵn thì
 * không upload lại (tránh video trùng khi retry sau khi upload xong mà chưa kịp đổi state).
 */
@Component
public class YouTubeUploadStep implements Step {

    public static final String VIDEO_FILE = "video.mp4";
    public static final String ID_FILE = "youtube-id.txt";

    private static final Logger log = LoggerFactory.getLogger(YouTubeUploadStep.class);

    private final YouTubeUploader uploader;
    private final ApplicationEventPublisher events;

    public YouTubeUploadStep(YouTubeUploader uploader, ApplicationEventPublisher events) {
        this.uploader = uploader;
        this.events = events;
    }

    @Override
    public boolean supports(JobState state) {
        return state == JobState.ASSEMBLED;
    }

    @Override
    public StepResult execute(StepContext ctx) {
        MDC.put("jobId", ctx.jobId().toString());
        try {
            Path idFile = ctx.workDir().resolve(ID_FILE);
            boolean alreadyUploaded = Files.isRegularFile(idFile);
            String id;
            if (alreadyUploaded) {
                // Lần trước upload xong nhưng hỏng ở bước playlist (job chưa UPLOADED) → không upload lại, chỉ làm nốt
                id = Files.readString(idFile).strip();
                log.info("Job đã có youtube id {}, bỏ qua upload, thêm vào playlist", id);
            } else {
                id = uploader.upload(ctx.workDir().resolve(VIDEO_FILE), ctx.script());
                Files.writeString(idFile, id);
            }
            uploader.addToPlaylist(ctx.script().track(), id);
            events.publishEvent(new VideoUploadedEvent(
                    ctx.jobId(),
                    id,
                    ctx.script().topicId(),
                    ctx.script().track(),
                    ctx.script().title(),
                    YouTubeMetadata.PRIVATE));
            return new StepResult(JobState.UPLOADED, alreadyUploaded);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            MDC.remove("jobId");
        }
    }
}
