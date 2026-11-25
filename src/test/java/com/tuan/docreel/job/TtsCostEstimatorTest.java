package com.tuan.docreel.job;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TtsCostEstimatorTest {

    private final TtsCostEstimator estimator = new TtsCostEstimator(1.0, 0.0002);

    @Test
    void elevenLabsCostIsCharsTimesCreditsTimesUsd() {
        assertThat(estimator.credits("ELEVENLABS", 1200)).isEqualTo(1200.0);
        assertThat(estimator.usd("ELEVENLABS", 1200)).isEqualTo(0.24);
        assertThat(estimator.usd("elevenlabs", 24)).isEqualTo(0.0048); // làm tròn 6 chữ số, không 0.004800000001
    }

    @Test
    void creditsPerCharIsConfigurable() {
        TtsCostEstimator flash = new TtsCostEstimator(0.5, 0.0002);
        assertThat(flash.credits("ELEVENLABS", 1000)).isEqualTo(500.0);
        assertThat(flash.usd("ELEVENLABS", 1000)).isEqualTo(0.1);
    }

    @Test
    void edgeIsFreeAndZeroCharsCostNothing() {
        assertThat(estimator.credits("EDGE", 5000)).isZero();
        assertThat(estimator.usd("edge", 5000)).isZero();
        assertThat(estimator.usd("ELEVENLABS", 0)).isZero();
        assertThat(estimator.usd(null, 0)).isZero();
    }
}
