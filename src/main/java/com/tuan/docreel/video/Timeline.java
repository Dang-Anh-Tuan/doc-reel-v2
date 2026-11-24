package com.tuan.docreel.video;

import java.util.List;

/**
 * Kế hoạch dựng hình (SPEC mục 8, "Đồng bộ slide ↔ audio").
 *
 * @param transitionSeconds T, thời gian xfade
 * @param totalSeconds tổng = Σ d_i = độ dài audio nối liền
 */
public record Timeline(List<Frame> frames, double transitionSeconds, double totalSeconds) {

    public Timeline {
        frames = List.copyOf(frames);
    }

    /**
     * @param audioSeconds d_i
     * @param clipSeconds d_i + T (frame cuối: d_i)
     * @param xfadeOffset offset xfade nối frame này vào frame trước = Σ d trước nó (frame đầu: 0, không dùng)
     */
    public record Frame(
            String sceneId, int indexInScene, double audioSeconds, double clipSeconds, double xfadeOffset) {}
}
