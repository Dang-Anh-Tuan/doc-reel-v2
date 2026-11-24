package com.tuan.docreel.voice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Audio giả: 1000 byte = 1 giây, ghép = nối byte + đệm lặng (byte 0). Không cần ffmpeg. */
class FakeAudioTool implements AudioTool {

    @Override
    public double durationSeconds(Path audio) {
        try {
            return Files.size(audio) / 1000.0;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void concat(List<Path> parts, Path output, double leadSeconds, double tailSeconds) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(new byte[(int) Math.round(leadSeconds * 1000)]);
            for (Path part : parts) {
                out.write(Files.readAllBytes(part));
            }
            out.write(new byte[(int) Math.round(tailSeconds * 1000)]);
            Files.write(output, out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Audio giả cho một câu: {@code marker} lặp, 0,1 giây mỗi ký tự. */
    static byte[] audioFor(int marker, String sentence) {
        byte[] bytes = new byte[sentence.length() * 100];
        java.util.Arrays.fill(bytes, (byte) marker);
        return bytes;
    }
}
