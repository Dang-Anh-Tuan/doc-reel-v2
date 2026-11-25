package com.tuan.docreel.pipeline;

import com.tuan.docreel.job.JobState;
import com.tuan.docreel.script.VideoScript;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Dữ liệu một bước cần để chạy.
 *
 * @param workDir {@code work/{jobId}/} — nơi ghi file trung gian (PNG, MP3, SRT, MP4)
 * @param attemptNo lần thử của bước này, đếm từ 1
 * @param usage nơi bước ghi tài nguyên trả phí đã tiêu (vd ký tự TTS) ngay khi tiêu; worker đọc cả khi bước lỗi
 */
public record StepContext(
        UUID jobId, JobState state, VideoScript script, Path workDir, int attemptNo, UsageMeter usage) {

    /** Không cần theo dõi chi phí (bước không trả phí, test). */
    public StepContext(UUID jobId, JobState state, VideoScript script, Path workDir, int attemptNo) {
        this(jobId, state, script, workDir, attemptNo, new UsageMeter());
    }
}
