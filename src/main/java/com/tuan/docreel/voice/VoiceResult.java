package com.tuan.docreel.voice;

import com.tuan.docreel.common.AppProperties.TtsProviderName;
import java.nio.file.Path;
import java.util.List;

/**
 * Kết quả đọc cả video.
 *
 * @param provider provider đã đọc (lưu vào {@code job.tts_provider}; cả video chỉ 1 giọng)
 * @param audios audio theo thứ tự timeline; quiz có 2 phần tử ({@code id}, {@code id-reveal})
 * @param srt file phụ đề
 */
public record VoiceResult(TtsProviderName provider, List<UnitAudio> audios, Path srt) {

    /** Audio một frame: đã đệm 0,2s lặng đầu + 0,4s lặng cuối. */
    public record UnitAudio(String name, Path file, double seconds) {}
}
