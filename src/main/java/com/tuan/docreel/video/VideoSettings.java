package com.tuan.docreel.video;

/**
 * Thông số khung hình (SPEC: 1080×1920). {@code maxZoom} ≤ 1 = slide tĩnh, không zoom (mặc định từ 25/11/2026 theo yêu
 * cầu người dùng); &gt; 1 = zoom chậm vào giữa tới mức đó trong suốt slide.
 */
public record VideoSettings(int width, int height, int fps, int crf, double transitionSeconds, double maxZoom) {

    public static final VideoSettings DEFAULT = new VideoSettings(1080, 1920, 30, 20, 0.3, 1.0);

    /** Kiểu cũ: zoom 6 % mỗi slide. */
    public static final VideoSettings ZOOM = new VideoSettings(1080, 1920, 30, 20, 0.3, 1.06);

    public boolean zoom() {
        return maxZoom > 1.0;
    }
}
