package com.tuan.docreel.video;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Dựng lệnh ffmpeg: mỗi ảnh → clip d_i×fps frame (tĩnh, hoặc zoom chậm nếu {@link VideoSettings#zoom()}) → xfade nối
 * tiếp; audio concat; tuỳ chọn burn .srt.
 * Thuần chuỗi, không chạy gì (test snapshot).
 */
@Component
public class FfmpegCommandBuilder {

    private static final String SUBTITLE_STYLE = "FontName=Arial,FontSize=8,Outline=1,Alignment=2,MarginV=45";

    /**
     * @param subtitleFileName tên file .srt, tương đối so với thư mục làm việc của lệnh (tránh escape đường dẫn
     *     Windows trong filter); null = không phụ đề
     */
    public List<String> build(
            String ffmpeg,
            Timeline timeline,
            List<Path> images,
            List<Path> audios,
            String subtitleFileName,
            Path output,
            VideoSettings s) {
        int n = timeline.frames().size();
        if (images.size() != n || audios.size() != n) {
            throw new IllegalArgumentException("Cần đúng " + n + " ảnh và " + n + " audio");
        }
        List<String> cmd = new ArrayList<>(List.of(ffmpeg, "-y", "-hide_banner", "-nostdin", "-loglevel", "error"));
        for (Path image : images) {
            cmd.add("-i");
            cmd.add(image.toString());
        }
        for (Path audio : audios) {
            cmd.add("-i");
            cmd.add(audio.toString());
        }

        List<String> graph = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            graph.add(zoomClip(i, timeline.frames().get(i).clipSeconds(), s));
        }
        String last = "[v0]";
        for (int i = 1; i < n; i++) {
            String out = "[x" + i + "]";
            graph.add(last + "[v" + i + "]xfade=transition=fade:duration=" + num(timeline.transitionSeconds())
                    + ":offset=" + num(timeline.frames().get(i).xfadeOffset()) + out);
            last = out;
        }
        String tail = "format=yuv420p";
        if (subtitleFileName != null) {
            tail += ",subtitles=" + subtitleFileName + ":force_style='" + SUBTITLE_STYLE + "'";
        }
        graph.add(last + tail + "[vout]");
        StringBuilder concat = new StringBuilder();
        for (int i = 0; i < n; i++) {
            concat.append('[').append(n + i).append(":a]");
        }
        graph.add(concat + "concat=n=" + n + ":v=0:a=1[aout]");

        cmd.addAll(List.of("-filter_complex", String.join(";", graph)));
        cmd.addAll(List.of("-map", "[vout]", "-map", "[aout]"));
        cmd.addAll(List.of("-c:v", "libx264", "-preset", "medium", "-crf", String.valueOf(s.crf())));
        cmd.addAll(List.of("-r", String.valueOf(s.fps()), "-c:a", "aac", "-b:a", "192k", "-ar", "44100"));
        cmd.addAll(List.of("-movflags", "+faststart", output.toString()));
        return cmd;
    }

    private static String zoomClip(int i, double clipSeconds, VideoSettings s) {
        int frames = Math.max(1, (int) Math.round(clipSeconds * s.fps()));
        if (!s.zoom()) {
            // Slide tĩnh: zoompan z=1 chỉ để nhân 1 ảnh thành đúng `frames` khung (giữ nguyên cách tính thời lượng)
            return "[" + i + ":v]scale=" + s.width() + ":" + s.height()
                    + ",zoompan=z=1:d=" + frames + ":s=" + s.width() + "x" + s.height() + ":fps=" + s.fps()
                    + ",setsar=1[v" + i + "]";
        }
        String step = String.format(Locale.ROOT, "%.6f", (s.maxZoom() - 1) / frames);
        return "[" + i + ":v]scale=" + s.width() * 3 / 2 + ":" + s.height() * 3 / 2
                + ",zoompan=z='min(zoom+" + step + "," + num(s.maxZoom()) + ")'"
                + ":x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)'"
                + ":d=" + frames + ":s=" + s.width() + "x" + s.height() + ":fps=" + s.fps()
                + ",setsar=1[v" + i + "]";
    }

    private static String num(double v) {
        String text = String.format(Locale.ROOT, "%.3f", v);
        return text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
