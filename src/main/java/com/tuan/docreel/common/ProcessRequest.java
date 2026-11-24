package com.tuan.docreel.common;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Lệnh cần chạy. Tham số truyền dạng danh sách (không qua shell) → không lo escape.
 *
 * @param environment biến môi trường thêm vào (không bao giờ được log)
 */
public record ProcessRequest(List<String> command, Path workingDir, Map<String, String> environment, Duration timeout) {

    public ProcessRequest {
        command = List.copyOf(Objects.requireNonNull(command, "command"));
        if (command.isEmpty()) {
            throw new IllegalArgumentException("command rỗng");
        }
        environment = Map.copyOf(environment == null ? Map.of() : environment);
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout phải > 0");
        }
    }

    public static ProcessRequest of(Duration timeout, String... command) {
        return new ProcessRequest(List.of(command), null, Map.of(), timeout);
    }

    public static ProcessRequest of(Duration timeout, List<String> command) {
        return new ProcessRequest(command, null, Map.of(), timeout);
    }

    public ProcessRequest withWorkingDir(Path dir) {
        return new ProcessRequest(command, dir, environment, timeout);
    }

    public ProcessRequest withEnv(String name, String value) {
        Map<String, String> env = new HashMap<>(environment);
        env.put(name, value);
        return new ProcessRequest(command, workingDir, env, timeout);
    }

    /** Tên chương trình (phần tử đầu) để log ngắn gọn. */
    public String program() {
        return command.getFirst();
    }
}
