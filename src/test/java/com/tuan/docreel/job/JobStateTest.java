package com.tuan.docreel.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JobStateTest {

    private static final Set<String> VALID = Set.of(
            "RECEIVED>RENDERED",
            "RENDERED>VOICED",
            "VOICED>ASSEMBLED",
            "ASSEMBLED>UPLOADED",
            "RECEIVED>FAILED",
            "RENDERED>FAILED",
            "VOICED>FAILED",
            "ASSEMBLED>FAILED",
            "FAILED>RECEIVED");

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

    static Stream<Arguments> allPairs() {
        return Stream.of(JobState.values())
                .flatMap(from -> Stream.of(JobState.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allPairs")
    void fullTransitionMatrix(JobState from, JobState to) {
        boolean valid = VALID.contains(from + ">" + to);
        assertThat(from.canTransitionTo(to)).isEqualTo(valid);
        VideoJob job = JobFixtures.jobIn(from);
        if (valid) {
            JobFixtures.move(job, to);
            assertThat(job.getState()).isEqualTo(to);
        } else {
            assertThatThrownBy(() -> JobFixtures.move(job, to))
                    .isInstanceOf(IllegalStateTransitionException.class)
                    .hasMessageContaining(from.name())
                    .hasMessageContaining(to.name());
            assertThat(job.getState()).isEqualTo(from);
        }
    }

    @Test
    void uploadedIsTerminal() {
        assertThat(JobState.UPLOADED.allowedNext()).isEmpty();
    }
}
