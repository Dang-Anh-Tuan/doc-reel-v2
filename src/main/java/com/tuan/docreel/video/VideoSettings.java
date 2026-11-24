package com.tuan.docreel.video;

/** Thông số khung hình (SPEC: 1080×1920). */
public record VideoSettings(int width, int height, int fps, int crf, double transitionSeconds, double maxZoom) {

    public static final VideoSettings DEFAULT = new VideoSettings(1080, 1920, 30, 20, 0.3, 1.06);
}
