package com.tuan.docreel.voice;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Phần chạy trong JVM con của edge-tts giả (chỉ phụ thuộc test-classes). Tham số đầu là chế độ: {@code ok},
 * {@code fail} (thoát mã 3), {@code hang} (treo), {@code empty} (file rỗng); phần còn lại giống edge-tts thật.
 */
public final class FakeEdgeCli {

    private FakeEdgeCli() {}

    public static void main(String[] args) throws Exception {
        String mode = args[0];
        String file = null;
        String media = null;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--file")) {
                file = args[i + 1];
            } else if (args[i].equals("--write-media")) {
                media = args[i + 1];
            }
        }
        switch (mode) {
            case "ok" -> {
                String text = Files.readString(Path.of(file), StandardCharsets.UTF_8);
                byte[] audio = new byte[text.length() * 100];
                java.util.Arrays.fill(audio, (byte) 2);
                Files.write(Path.of(media), audio);
            }
            case "empty" -> Files.write(Path.of(media), new byte[0]);
            case "hang" -> Thread.sleep(60_000);
            default -> {
                System.err.print("edge-tts giả lỗi");
                System.exit(3);
            }
        }
    }
}
