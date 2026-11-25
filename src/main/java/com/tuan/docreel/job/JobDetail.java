package com.tuan.docreel.job;

import com.tuan.docreel.script.VideoType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Chi tiết một job kèm các lần chạy bước và slide đã render (không trả kịch bản gốc và cấu hình TTS).
 *
 * @param currentStep bước đang / sẽ chạy (FAILED: bước đã hỏng); {@code null} khi đã UPLOADED
 * @param youtubeUrl {@code https://youtu.be/{youtubeId}} khi đã upload
 * @param slides PNG đã render có trên đĩa, theo thứ tự timeline
 * @param cost chi phí ước tính (P13b)
 */
public record JobDetail(
        UUID id,
        String topicId,
        String title,
        int scriptVersion,
        VideoType videoType,
        JobState state,
        JobStep currentStep,
        JobStep lastSuccessfulStep,
        String errorMessage,
        String youtubeId,
        String youtubeUrl,
        String ttsProvider,
        String sourceChannel,
        String sourceRef,
        Instant createdAt,
        Instant updatedAt,
        List<Attempt> attempts,
        List<Slide> slides,
        Cost cost) {

    /**
     * Chi phí ƯỚC TÍNH của video (P13b).
     *
     * @param ttsChars ký tự thực gửi provider TTS (không tính câu lấy từ cache), cộng dồn các lần voice
     * @param estimatedCredits {@code ttsChars × credits-per-char}; edge-tts = 0
     * @param estimatedUsd {@code estimatedCredits × usd-per-credit}
     * @param audioMs tổng độ dài audio; null = chưa voice
     * @param processingMs tổng {@code duration_ms} của mọi attempt (kể cả lần lỗi)
     * @param wallMs từ lúc nhận job ({@code createdAt}) tới lần cập nhật cuối (= lúc UPLOADED khi đã xong)
     */
    public record Cost(
            String ttsProvider,
            long ttsChars,
            double estimatedCredits,
            double estimatedUsd,
            Long audioMs,
            long processingMs,
            long wallMs) {

        static Cost of(VideoJob j, List<JobAttempt> attempts, TtsCostEstimator estimator) {
            long processing = attempts.stream()
                    .map(JobAttempt::getDurationMs)
                    .filter(Objects::nonNull)
                    .mapToLong(Integer::longValue)
                    .sum();
            long wall = j.getCreatedAt() == null || j.getUpdatedAt() == null
                    ? 0
                    : Math.max(
                            0,
                            Duration.between(j.getCreatedAt(), j.getUpdatedAt()).toMillis());
            return new Cost(
                    j.getTtsProvider(),
                    j.getTtsChars(),
                    estimator.credits(j.getTtsProvider(), j.getTtsChars()),
                    estimator.usd(j.getTtsProvider(), j.getTtsChars()),
                    j.getAudioMs(),
                    processing,
                    wall);
        }
    }

    /** Một lần chạy một bước; {@code finishedAt = startedAt + durationMs}. */
    public record Attempt(
            String step,
            int attemptNo,
            String outcome,
            Integer durationMs,
            String error,
            Instant startedAt,
            Instant finishedAt) {

        static Attempt of(JobAttempt a) {
            return new Attempt(
                    a.getStep(),
                    a.getAttemptNo(),
                    a.getOutcome(),
                    a.getDurationMs(),
                    a.getError(),
                    a.getStartedAt(),
                    a.getFinishedAt());
        }
    }

    /**
     * Một ảnh slide.
     *
     * @param index số thứ tự từ 1 theo timeline (dùng trong URL)
     * @param name tên file không đuôi, vd {@code s09-quiz-reveal}
     * @param url {@code /api/jobs/{id}/slides/{index}.png}
     */
    public record Slide(int index, String name, String url) {}

    static JobDetail of(
            VideoJob j, String title, List<JobAttempt> attempts, List<Slide> slides, TtsCostEstimator estimator) {
        return new JobDetail(
                j.getId(),
                j.getTopicId(),
                title,
                j.getScriptVersion(),
                j.getVideoType(),
                j.getState(),
                j.currentStep(),
                j.getLastSuccessfulStep(),
                j.getErrorMessage(),
                j.getYoutubeId(),
                JobSummary.youtubeUrl(j.getYoutubeId()),
                j.getTtsProvider(),
                j.getSourceChannel(),
                j.getSourceRef(),
                j.getCreatedAt(),
                j.getUpdatedAt(),
                attempts.stream().map(Attempt::of).toList(),
                slides,
                Cost.of(j, attempts, estimator));
    }
}
