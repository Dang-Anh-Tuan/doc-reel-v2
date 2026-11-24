package com.tuan.docreel.common;

import java.io.Serial;
import java.util.List;

/** Lệnh kết thúc với exit code khác 0. Message chứa stderr để log / lưu vào {@code job_attempt.error}. */
public class ProcessFailedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Giới hạn độ dài stderr đưa vào message (ffmpeg có thể in rất dài). */
    static final int MAX_STDERR_IN_MESSAGE = 4_000;

    private final transient List<String> command;
    private final transient ProcessResult result;

    public ProcessFailedException(List<String> command, ProcessResult result) {
        super("Lệnh " + command.getFirst() + " thoát mã " + result.exitCode() + ": " + tail(result.stderr()));
        this.command = List.copyOf(command);
        this.result = result;
    }

    public List<String> command() {
        return command;
    }

    public int exitCode() {
        return result.exitCode();
    }

    public String stdout() {
        return result.stdout();
    }

    public String stderr() {
        return result.stderr();
    }

    /** Lỗi thường nằm cuối stderr → giữ phần đuôi. */
    static String tail(String stderr) {
        String trimmed = stderr == null ? "" : stderr.strip();
        if (trimmed.length() <= MAX_STDERR_IN_MESSAGE) {
            return trimmed;
        }
        return "…" + trimmed.substring(trimmed.length() - MAX_STDERR_IN_MESSAGE);
    }
}
