package com.tuan.docreel.voice;

import com.tuan.docreel.common.AppProperties;
import com.tuan.docreel.common.AppProperties.TtsProviderName;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Cache audio từng câu trong {@code work/tts-cache/} → retry không tốn thêm quota. */
@Component
public class TtsCache {

    private final Path dir;

    public TtsCache(AppProperties props) {
        this.dir = props.dirs().work().resolve("tts-cache");
    }

    /** {@code sha256(provider + voice + speed + câu)}; {@code voiceKey} đã gồm voice + tốc độ. */
    public static String key(TtsProviderName provider, String voiceKey, String sentence) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            String joined = provider.name() + '\u0000' + voiceKey + '\u0000' + sentence;
            return HexFormat.of().formatHex(sha.digest(joined.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<Path> find(String key) {
        Path file = dir.resolve(key + ".mp3");
        try {
            return Files.exists(file) && Files.size(file) > 0 ? Optional.of(file) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public Path store(String key, byte[] audio) {
        try {
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, "part-", ".tmp");
            Files.write(tmp, audio);
            Path file = dir.resolve(key + ".mp3");
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("Không ghi được cache TTS", e);
        }
    }
}
