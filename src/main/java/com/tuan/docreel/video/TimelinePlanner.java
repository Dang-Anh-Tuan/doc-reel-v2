package com.tuan.docreel.video;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** Logic thuần: từ thời lượng audio tính độ dài clip và offset xfade. */
@Component
public class TimelinePlanner {

    public Timeline plan(List<SceneTiming> scenes, double transitionSeconds) {
        if (scenes.isEmpty()) {
            throw new IllegalArgumentException("Không có scene nào");
        }
        if (transitionSeconds < 0) {
            throw new IllegalArgumentException("T phải >= 0");
        }
        List<Timeline.Frame> frames = new ArrayList<>();
        double elapsed = 0;
        for (SceneTiming scene : scenes) {
            for (int i = 0; i < scene.audioSeconds().size(); i++) {
                double d = scene.audioSeconds().get(i);
                if (!(d > transitionSeconds)) {
                    throw new IllegalArgumentException("Audio scene " + scene.sceneId() + " (" + d
                            + "s) phải dài hơn thời gian xfade " + transitionSeconds + "s");
                }
                frames.add(new Timeline.Frame(scene.sceneId(), i, d, d + transitionSeconds, elapsed));
                elapsed += d;
            }
        }
        // frame cuối không cần phần chồng
        Timeline.Frame last = frames.removeLast();
        frames.add(new Timeline.Frame(
                last.sceneId(), last.indexInScene(), last.audioSeconds(), last.audioSeconds(), last.xfadeOffset()));
        return new Timeline(frames, transitionSeconds, elapsed);
    }
}
