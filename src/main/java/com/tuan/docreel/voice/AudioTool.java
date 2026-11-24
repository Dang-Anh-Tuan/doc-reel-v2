package com.tuan.docreel.voice;

import java.nio.file.Path;
import java.util.List;

/** Thao tác audio (ffmpeg/ffprobe). Tách interface để test logic mà không cần ffmpeg. */
public interface AudioTool {

    double durationSeconds(Path audio);

    /** Nối các câu, đệm {@code leadSeconds} lặng đầu và {@code tailSeconds} lặng cuối → file MP3 của scene. */
    void concat(List<Path> parts, Path output, double leadSeconds, double tailSeconds);
}
