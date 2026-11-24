package com.tuan.docreel.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JobStateTest {

    @Test
    void declaresStatesInPipelineOrder() {
        assertThat(JobState.values())
                .containsExactly(
                        JobState.RECEIVED,
                        JobState.RENDERED,
                        JobState.VOICED,
                        JobState.ASSEMBLED,
                        JobState.UPLOADED,
                        JobState.FAILED);
        assertThat(JobState.valueOf("FAILED")).isEqualTo(JobState.FAILED);
    }
}
