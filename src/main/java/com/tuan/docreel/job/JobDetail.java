package com.tuan.docreel.job;

import com.tuan.docreel.script.VideoType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Chi tiết một job kèm các lần chạy bước (không trả kịch bản gốc và cấu hình TTS). */
public record JobDetail(
        UUID id,
        String topicId,
        int scriptVersion,
        VideoType videoType,
        JobState state,
        JobStep lastSuccessfulStep,
        String errorMessage,
        String youtubeId,
        String ttsProvider,
        String sourceChannel,
        String sourceRef,
        Instant createdAt,
        Instant updatedAt,
        List<Attempt> attempts) {

    public record Attempt(
            String step, int attemptNo, String outcome, Integer durationMs, String error, Instant startedAt) {

        static Attempt of(JobAttempt a) {
            return new Attempt(
                    a.getStep(), a.getAttemptNo(), a.getOutcome(), a.getDurationMs(), a.getError(), a.getStartedAt());
        }
    }

    static JobDetail of(VideoJob j, List<JobAttempt> attempts) {
        return new JobDetail(
                j.getId(),
                j.getTopicId(),
                j.getScriptVersion(),
                j.getVideoType(),
                j.getState(),
                j.getLastSuccessfulStep(),
                j.getErrorMessage(),
                j.getYoutubeId(),
                j.getTtsProvider(),
                j.getSourceChannel(),
                j.getSourceRef(),
                j.getCreatedAt(),
                j.getUpdatedAt(),
                attempts.stream().map(Attempt::of).toList());
    }
}
