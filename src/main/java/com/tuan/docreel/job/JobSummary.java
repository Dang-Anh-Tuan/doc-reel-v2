package com.tuan.docreel.job;

import com.tuan.docreel.script.VideoType;
import java.time.Instant;
import java.util.UUID;

/** Dòng trong danh sách job. */
public record JobSummary(
        UUID id,
        String topicId,
        int scriptVersion,
        VideoType videoType,
        JobState state,
        JobStep lastSuccessfulStep,
        String errorMessage,
        String youtubeId,
        Instant createdAt,
        Instant updatedAt) {

    static JobSummary of(VideoJob j) {
        return new JobSummary(
                j.getId(),
                j.getTopicId(),
                j.getScriptVersion(),
                j.getVideoType(),
                j.getState(),
                j.getLastSuccessfulStep(),
                j.getErrorMessage(),
                j.getYoutubeId(),
                j.getCreatedAt(),
                j.getUpdatedAt());
    }
}
