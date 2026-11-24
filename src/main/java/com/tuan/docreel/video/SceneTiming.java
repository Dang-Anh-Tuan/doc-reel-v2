package com.tuan.docreel.video;

import java.util.List;

/**
 * Thời lượng audio của một scene. Scene thường có 1 phần tử; {@code quiz} có 2 (hỏi, đáp án) → 2 frame.
 */
public record SceneTiming(String sceneId, List<Double> audioSeconds) {

    public SceneTiming {
        audioSeconds = List.copyOf(audioSeconds);
        if (audioSeconds.isEmpty()) {
            throw new IllegalArgumentException("Scene " + sceneId + " không có audio");
        }
    }
}
