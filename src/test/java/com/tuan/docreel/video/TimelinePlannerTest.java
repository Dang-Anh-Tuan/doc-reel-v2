package com.tuan.docreel.video;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class TimelinePlannerTest {

    private final TimelinePlanner planner = new TimelinePlanner();

    @Test
    void threeScenesGiveOffsetsAndTotal() {
        Timeline t = planner.plan(
                List.of(
                        new SceneTiming("a", List.of(5.0)),
                        new SceneTiming("b", List.of(7.0)),
                        new SceneTiming("c", List.of(4.0))),
                0.3);

        assertThat(t.frames()).hasSize(3);
        assertThat(t.frames().get(1).xfadeOffset()).isCloseTo(5.0, within(1e-9));
        assertThat(t.frames().get(2).xfadeOffset()).isCloseTo(12.0, within(1e-9));
        assertThat(t.totalSeconds()).isCloseTo(16.0, within(1e-9));
        assertThat(t.frames().get(0).clipSeconds()).isCloseTo(5.3, within(1e-9));
        assertThat(t.frames().get(1).clipSeconds()).isCloseTo(7.3, within(1e-9));
        assertThat(t.frames().get(2).clipSeconds()).isCloseTo(4.0, within(1e-9));
    }

    @Test
    void quizSplitsIntoTwoFramesByTwoAudios() {
        Timeline t =
                planner.plan(List.of(new SceneTiming("q", List.of(6.0, 3.0)), new SceneTiming("s", List.of(4.0))), 0.3);

        assertThat(t.frames()).hasSize(3);
        assertThat(t.frames().get(0).indexInScene()).isZero();
        assertThat(t.frames().get(1).sceneId()).isEqualTo("q");
        assertThat(t.frames().get(1).indexInScene()).isEqualTo(1);
        assertThat(t.frames().get(1).xfadeOffset()).isCloseTo(6.0, within(1e-9));
        assertThat(t.frames().get(2).xfadeOffset()).isCloseTo(9.0, within(1e-9));
        assertThat(t.totalSeconds()).isCloseTo(13.0, within(1e-9));
    }

    @Test
    void singleSceneHasNoOverlap() {
        Timeline t = planner.plan(List.of(new SceneTiming("a", List.of(5.0))), 0.3);
        assertThat(t.frames().getFirst().clipSeconds()).isEqualTo(5.0);
        assertThat(t.totalSeconds()).isEqualTo(5.0);
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> planner.plan(List.of(), 0.3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> planner.plan(List.of(new SceneTiming("a", List.of(5.0))), -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> planner.plan(List.of(new SceneTiming("a", List.of(0.2))), 0.3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("xfade");
        assertThatThrownBy(() -> new SceneTiming("a", List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
