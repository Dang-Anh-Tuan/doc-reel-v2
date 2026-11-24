package com.tuan.docreel.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.job.JobState;
import com.tuan.docreel.notify.JobStateChangedEvent;
import com.tuan.docreel.voice.TtsException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

class PipelineEventIT extends PipelineTestBase {

    @Test
    void oneEventPerStateChangeAfterCommit() {
        UUID id = newJob("Video event");

        drain(worker(RetryPolicy.DEFAULT, arr(fakeSteps())));

        assertThat(EVENTS).extracting(JobStateChangedEvent::jobId).containsOnly(id);
        assertThat(EVENTS)
                .extracting(e -> e.from() + "->" + e.to())
                .containsExactly("RECEIVED->RENDERED", "RENDERED->VOICED", "VOICED->ASSEMBLED", "ASSEMBLED->UPLOADED");
    }

    @Test
    void failureEmitsOneEventWithReason() {
        newJob("Video event lỗi");
        List<FakeStep> steps = fakeSteps();
        steps.get(1).behaviour = ctx -> {
            throw new TtsException("401");
        };

        drain(worker(RetryPolicy.DEFAULT, arr(steps)));

        assertThat(EVENTS).hasSize(2);
        JobStateChangedEvent last = EVENTS.getLast();
        assertThat(last.to()).isEqualTo(JobState.FAILED);
        assertThat(last.from()).isEqualTo(JobState.RENDERED);
        assertThat(last.error()).isEqualTo("401");
    }

    @Test
    void rolledBackTransactionPublishesNoEvent() {
        UUID id = newJob("Video rollback");
        PipelineWorker worker = worker(RetryPolicy.DEFAULT, arr(fakeSteps()));

        new TransactionTemplate(tm).executeWithoutResult(status -> {
            worker.runOnce(); // chạy trong transaction ngoài rồi hủy
            status.setRollbackOnly();
        });

        assertThat(EVENTS).isEmpty();
        assertThat(job(id).getState()).isEqualTo(JobState.RECEIVED);
    }
}
