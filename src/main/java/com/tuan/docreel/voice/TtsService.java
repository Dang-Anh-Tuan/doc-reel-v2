package com.tuan.docreel.voice;

import com.tuan.docreel.common.AppProperties.TtsProviderName;
import com.tuan.docreel.script.QuizScene;
import com.tuan.docreel.script.Scene;
import com.tuan.docreel.script.VideoScript;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Chọn provider theo job, đọc từng câu (có cache), nối thành audio scene và dựng {@code .srt}. Một video chỉ một
 * giọng: hết quota giữa chừng → bỏ hết audio, đọc lại toàn bộ bằng provider dự phòng.
 */
@Service
public class TtsService {

    static final double LEAD_SECONDS = 0.2;
    static final double TAIL_SECONDS = 0.4;
    static final String SRT_NAME = "subtitles.srt";

    private static final Logger log = LoggerFactory.getLogger(TtsService.class);

    private final Map<TtsProviderName, TtsProvider> providers = new EnumMap<>(TtsProviderName.class);
    private final Lexicon lexicon;
    private final TtsCache cache;
    private final AudioTool audio;
    private final TtsUsageTracker usage;
    private final SubtitleBuilder subtitles = new SubtitleBuilder();

    public TtsService(
            List<TtsProvider> providerList, Lexicon lexicon, TtsCache cache, AudioTool audio, TtsUsageTracker usage) {
        providerList.forEach(p -> providers.put(p.name(), p));
        this.lexicon = lexicon;
        this.cache = cache;
        this.audio = audio;
        this.usage = usage;
    }

    /** Phần đọc của một frame: câu đọc (đã áp lexicon) và câu hiện phụ đề (narration gốc). */
    private record Unit(String name, List<String> spoken, List<String> shown) {}

    /**
     * @param pinned provider job đã dùng ở lần trước (retry giữ nguyên); null = chọn mới
     */
    public VoiceResult voice(VideoScript script, Path workDir, TtsSettings settings, TtsProviderName pinned) {
        List<Unit> units = units(script);
        long needed = units.stream()
                .flatMap(u -> u.spoken().stream())
                .mapToLong(String::length)
                .sum();
        TtsProvider provider = choose(needed, settings, pinned);
        TtsProvider fallback = provider(settings.fallback());
        log.info("TTS: {} ký tự, provider {}", needed, provider.name());
        try {
            return render(units, workDir, settings, provider);
        } catch (TtsQuotaExceededException e) {
            if (provider == fallback) {
                throw e;
            }
            log.warn("{} hết quota giữa chừng → bỏ audio, đọc lại toàn bộ bằng {}", provider.name(), fallback.name());
            deleteOutputs(units, workDir);
            return render(units, workDir, settings, fallback);
        }
    }

    /** Còn đủ quota (≥ needed × 1.1, tính số nguyên) → provider chính; không thì dự phòng. */
    TtsProvider choose(long needed, TtsSettings settings, TtsProviderName pinned) {
        if (pinned != null) {
            return provider(pinned);
        }
        TtsProvider primary = provider(settings.primary());
        TtsProvider fallback = provider(settings.fallback());
        if (primary != fallback && primary.isConfigured() && primary.remainingChars() * 10 >= needed * 11) {
            return primary;
        }
        return fallback;
    }

    private TtsProvider provider(TtsProviderName name) {
        TtsProvider p = providers.get(name);
        if (p == null) {
            throw new TtsException("Chưa có provider TTS " + name);
        }
        return p;
    }

    private VoiceResult render(List<Unit> units, Path workDir, TtsSettings settings, TtsProvider provider) {
        try {
            Files.createDirectories(workDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<VoiceResult.UnitAudio> audios = new ArrayList<>();
        List<SubtitleBuilder.Segment> segments = new ArrayList<>();
        double offset = 0;
        for (Unit unit : units) {
            List<Path> parts = new ArrayList<>();
            List<Double> durations = new ArrayList<>();
            for (int i = 0; i < unit.spoken().size(); i++) {
                Path part = sentenceAudio(unit, i, settings, provider);
                parts.add(part);
                durations.add(audio.durationSeconds(part));
            }
            Path out = workDir.resolve(unit.name() + ".mp3");
            audio.concat(parts, out, LEAD_SECONDS, TAIL_SECONDS);
            double seconds = audio.durationSeconds(out);
            segments.addAll(subtitles.align(unit.shown(), durations, offset + LEAD_SECONDS));
            audios.add(new VoiceResult.UnitAudio(unit.name(), out, seconds));
            offset += seconds;
        }
        Path srt = workDir.resolve(SRT_NAME);
        try {
            Files.writeString(srt, subtitles.build(segments), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new VoiceResult(provider.name(), audios, srt);
    }

    private Path sentenceAudio(Unit unit, int index, TtsSettings settings, TtsProvider provider) {
        String sentence = unit.spoken().get(index);
        String key = TtsCache.key(provider.name(), provider.voiceKey(settings), sentence);
        var cached = cache.find(key);
        if (cached.isPresent()) {
            return cached.get();
        }
        String previous = index > 0 ? unit.spoken().get(index - 1) : null;
        String next = index + 1 < unit.spoken().size() ? unit.spoken().get(index + 1) : null;
        byte[] bytes = provider.synthesize(sentence, previous, next, settings);
        usage.add(provider.name(), sentence.length());
        return cache.store(key, bytes);
    }

    private void deleteOutputs(List<Unit> units, Path workDir) {
        for (Unit unit : units) {
            try {
                Files.deleteIfExists(workDir.resolve(unit.name() + ".mp3"));
            } catch (IOException e) {
                log.warn("Không xóa được audio {}: {}", unit.name(), e.getMessage());
            }
        }
        try {
            Files.deleteIfExists(workDir.resolve(SRT_NAME));
        } catch (IOException e) {
            log.warn("Không xóa được {}: {}", SRT_NAME, e.getMessage());
        }
    }

    private List<Unit> units(VideoScript script) {
        List<Unit> units = new ArrayList<>();
        for (Scene scene : script.scenes()) {
            String spoken = scene.spoken() != null && !scene.spoken().isBlank() ? scene.spoken() : scene.narration();
            units.add(unit(scene.id(), spoken, scene.narration()));
            if (scene instanceof QuizScene quiz) {
                units.add(unit(scene.id() + "-reveal", quiz.revealNarration(), quiz.revealNarration()));
            }
        }
        return units;
    }

    private Unit unit(String name, String spokenText, String shownText) {
        List<String> spoken =
                SentenceSplitter.split(spokenText).stream().map(lexicon::apply).toList();
        if (spoken.isEmpty()) {
            throw new TtsException("Scene " + name + " không có lời đọc");
        }
        return new Unit(name, spoken, SentenceSplitter.split(shownText));
    }
}
