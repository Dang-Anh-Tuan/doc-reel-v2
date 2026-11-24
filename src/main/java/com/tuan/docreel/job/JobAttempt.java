package com.tuan.docreel.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Một lần chạy một bước của job (bảng {@code job_attempt}), để xem lịch sử retry. */
@Entity
@Table(name = "job_attempt")
public class JobAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(nullable = false)
    private String step;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(nullable = false)
    private String outcome;

    @Column(name = "duration_ms")
    private Integer durationMs;

    private String error;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    protected JobAttempt() {}

    public JobAttempt(UUID jobId, JobStep step, int attemptNo, String outcome, Integer durationMs, String error) {
        this.jobId = jobId;
        this.step = step.name();
        this.attemptNo = attemptNo;
        this.outcome = outcome;
        this.durationMs = durationMs;
        this.error = error;
        // Attempt được ghi khi bước đã xong → lùi lại đúng thời lượng để có thời điểm bắt đầu thật
        Instant now = Instant.now();
        this.startedAt = durationMs == null ? now : now.minusMillis(durationMs);
    }

    public Long getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public String getStep() {
        return step;
    }

    public int getAttemptNo() {
        return attemptNo;
    }

    public String getOutcome() {
        return outcome;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public String getError() {
        return error;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    /** Thời điểm kết thúc = bắt đầu + thời lượng; {@code null} nếu không có thời lượng. */
    public Instant getFinishedAt() {
        return durationMs == null ? null : startedAt.plusMillis(durationMs);
    }
}
