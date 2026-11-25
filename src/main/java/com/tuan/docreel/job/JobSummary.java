package com.tuan.docreel.job;

import com.tuan.docreel.script.VideoType;
import java.time.Instant;
import java.util.UUID;

/**
 * Dòng trong danh sách job.
 *
 * @param title tiêu đề video lấy từ kịch bản ({@code null} nếu không đọc được)
 * @param currentStep bước đang / sẽ chạy (FAILED: bước đã hỏng); {@code null} khi đã UPLOADED
 * @param youtubeUrl {@code https://youtu.be/{youtubeId}} khi đã upload
 * @param ttsChars ký tự thực gửi provider TTS (P13b)
 * @param estimatedUsd chi phí TTS ước tính (USD), edge-tts = 0
 */
public record JobSummary(
        UUID id,
        String topicId,
        String title,
        int scriptVersion,
        VideoType videoType,
        JobState state,
        JobStep currentStep,
        JobStep lastSuccessfulStep,
        String errorMessage,
        String ttsProvider,
        String youtubeId,
        String youtubeUrl,
        Instant createdAt,
        Instant updatedAt,
        long ttsChars,
        double estimatedUsd) {

    static final String YOUTUBE_SHORT_URL = "https://youtu.be/";

    static JobSummary of(VideoJob j, String title, TtsCostEstimator estimator) {
        return new JobSummary(
                j.getId(),
                j.getTopicId(),
                title,
                j.getScriptVersion(),
                j.getVideoType(),
                j.getState(),
                j.currentStep(),
                j.getLastSuccessfulStep(),
                j.getErrorMessage(),
                j.getTtsProvider(),
                j.getYoutubeId(),
                youtubeUrl(j.getYoutubeId()),
                j.getCreatedAt(),
                j.getUpdatedAt(),
                j.getTtsChars(),
                estimator.usd(j.getTtsProvider(), j.getTtsChars()));
    }

    static String youtubeUrl(String youtubeId) {
        return youtubeId == null || youtubeId.isBlank() ? null : YOUTUBE_SHORT_URL + youtubeId;
    }
}
