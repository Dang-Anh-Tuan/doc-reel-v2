package com.tuan.docreel.job;

import com.tuan.docreel.common.Result;
import com.tuan.docreel.script.ScriptParser;
import com.tuan.docreel.script.VideoScript;
import com.tuan.docreel.script.VideoType;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Dữ liệu dựng sẵn cho test job. */
final class JobFixtures {

    private static final Path SAMPLE = Path.of("samples", "full", "sample-all-scenes.json");

    private JobFixtures() {}

    static VideoScript sampleScript() {
        try {
            Result<VideoScript, ?> r = new ScriptParser().parse(Files.readAllBytes(SAMPLE));
            if (!r.isOk()) {
                throw new IllegalStateException("Mẫu không hợp lệ: " + r.error());
            }
            return r.value();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Cùng kịch bản mẫu nhưng đổi tiêu đề (nên khác hash). */
    static VideoScript withTitle(VideoScript s, String title) {
        return new VideoScript(
                s.topicId(),
                s.track(),
                title,
                s.version(),
                s.videoType(),
                s.theme(),
                s.sources(),
                s.scenes(),
                s.origin());
    }

    static VideoJob newJob() {
        VideoScript s = sampleScript();
        return new VideoJob(
                s.topicId(),
                s.version(),
                ScriptParser.contentHash(s),
                VideoType.SLIDES,
                "ELEVENLABS",
                "{}",
                "api",
                null,
                "{}");
    }

    /** Job đang ở trạng thái {@code state}, đi theo đường hợp lệ. */
    static VideoJob jobIn(JobState state) {
        VideoJob job = newJob();
        if (state == JobState.FAILED) {
            job.fail("lỗi");
            return job;
        }
        for (JobStep step : JobStep.values()) {
            if (job.getState() == state) {
                break;
            }
            job.completeStep(step);
        }
        return job;
    }

    /** Thử chuyển job sang {@code to} bằng API công khai tương ứng. */
    static void move(VideoJob job, JobState to) {
        switch (to) {
            case FAILED -> job.fail("lỗi");
            case RECEIVED -> job.retry();
            default ->
                job.completeStep(Arrays.stream(JobStep.values())
                        .filter(s -> s.resultingState() == to)
                        .findFirst()
                        .orElseThrow());
        }
    }
}
