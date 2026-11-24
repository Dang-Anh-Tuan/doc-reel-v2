package com.tuan.docreel.job;

import com.tuan.docreel.script.VideoType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Một lần làm video từ một kịch bản (bảng {@code video_job}). Đổi trạng thái chỉ qua các hàm của lớp này. */
@Entity
@Table(name = "video_job")
public class VideoJob {

    @Id
    private UUID id;

    @Column(name = "topic_id", nullable = false)
    private String topicId;

    @Column(name = "script_version", nullable = false)
    private int scriptVersion;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "video_type", nullable = false)
    private VideoType videoType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobState state;

    @Enumerated(EnumType.STRING)
    @Column(name = "last_successful_step")
    private JobStep lastSuccessfulStep;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "youtube_id")
    private String youtubeId;

    @Column(name = "tts_provider")
    private String ttsProvider;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tts_settings", nullable = false, columnDefinition = "jsonb")
    private String ttsSettings;

    @Column(name = "source_channel", nullable = false)
    private String sourceChannel;

    @Column(name = "source_ref")
    private String sourceRef;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String script;

    @Version
    private int version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VideoJob() {}

    /** Job mới ở trạng thái RECEIVED. */
    public VideoJob(
            String topicId,
            int scriptVersion,
            String contentHash,
            VideoType videoType,
            String ttsProvider,
            String ttsSettingsJson,
            String sourceChannel,
            String sourceRef,
            String scriptJson) {
        this.id = UUID.randomUUID();
        this.topicId = topicId;
        this.scriptVersion = scriptVersion;
        this.contentHash = contentHash;
        this.idempotencyKey = IdempotencyKey.of(topicId, scriptVersion, contentHash);
        this.videoType = videoType;
        this.state = JobState.RECEIVED;
        this.ttsProvider = ttsProvider;
        this.ttsSettings = ttsSettingsJson;
        this.sourceChannel = sourceChannel;
        this.sourceRef = sourceRef;
        this.script = scriptJson;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** Hoàn thành một bước: sang trạng thái kết quả của bước và ghi nhớ bước đó. */
    public void completeStep(JobStep step) {
        transitionTo(step.resultingState());
        this.lastSuccessfulStep = step;
    }

    public void fail(String message) {
        transitionTo(JobState.FAILED);
        this.errorMessage = message;
    }

    /** FAILED → RECEIVED, giữ {@code lastSuccessfulStep} để chạy tiếp. */
    public void retry() {
        transitionTo(JobState.RECEIVED);
        this.errorMessage = null;
    }

    public void markUploaded(String youtubeId) {
        completeStep(JobStep.UPLOAD);
        this.youtubeId = youtubeId;
    }

    /**
     * Job vừa retry (RECEIVED nhưng đã có bước xong) → đưa state về điểm đã dừng để chạy tiếp từ bước lỗi. Không
     * qua kiểm tra chuyển trạng thái vì đây là "nhập lại pipeline", không phải một bước mới.
     */
    public void resume() {
        if (state == JobState.RECEIVED && lastSuccessfulStep != null) {
            this.state = lastSuccessfulStep.resultingState();
            this.updatedAt = Instant.now();
        }
    }

    /** State mà pipeline coi là hiện tại: job retry (RECEIVED + bước đã xong) tiếp tục từ sau bước đó. */
    public JobState pipelineState() {
        return state == JobState.RECEIVED && lastSuccessfulStep != null ? lastSuccessfulStep.resultingState() : state;
    }

    /**
     * Bước đang chạy / sẽ chạy (job FAILED: bước đã hỏng) = bước sau {@code lastSuccessfulStep}; {@code null} khi
     * đã UPLOADED.
     */
    public JobStep currentStep() {
        if (state == JobState.UPLOADED) {
            return null;
        }
        return lastSuccessfulStep == null ? JobStep.RENDER : lastSuccessfulStep.next();
    }

    /** Ghim provider TTS đã đọc xong video (retry giữ nguyên giọng). */
    public void pinTtsProvider(String provider) {
        this.ttsProvider = provider;
        this.updatedAt = Instant.now();
    }

    private void transitionTo(JobState next) {
        if (!state.canTransitionTo(next)) {
            throw new IllegalStateTransitionException(state, next);
        }
        this.state = next;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTopicId() {
        return topicId;
    }

    public int getScriptVersion() {
        return scriptVersion;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public VideoType getVideoType() {
        return videoType;
    }

    public JobState getState() {
        return state;
    }

    public JobStep getLastSuccessfulStep() {
        return lastSuccessfulStep;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getYoutubeId() {
        return youtubeId;
    }

    public String getTtsProvider() {
        return ttsProvider;
    }

    public String getTtsSettings() {
        return ttsSettings;
    }

    public String getSourceChannel() {
        return sourceChannel;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public String getScript() {
        return script;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
