package com.tuan.docreel.notify;

import com.tuan.docreel.job.JobState;
import java.util.UUID;

/**
 * Job đổi state. Phát trong transaction của worker; listener {@code AFTER_COMMIT} chỉ nhận khi đã commit.
 *
 * @param error lý do, chỉ có khi {@code to == FAILED}
 */
public record JobStateChangedEvent(UUID jobId, String topicId, JobState from, JobState to, String error) {}
