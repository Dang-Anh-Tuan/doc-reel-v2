package com.tuan.docreel.script;

import java.util.List;

/** {@code points}: 1–3 ý chính. */
public record PointsScene(
        String id, String narration, String spoken, String accent, String titleRest, List<Point> points)
        implements TitledScene {

    public record Point(String heading, String detail) {}
}
