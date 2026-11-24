package com.tuan.docreel.common;

import java.io.Serial;
import java.time.Duration;
import java.util.List;

/** Lệnh chạy quá giờ; process (và con cháu) đã bị kill. */
public class ProcessTimeoutException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient List<String> command;
    private final Duration timeout;
    private final long pid;
    private final String stdout;
    private final String stderr;

    public ProcessTimeoutException(List<String> command, Duration timeout, long pid, String stdout, String stderr) {
        super("Lệnh " + command.getFirst() + " quá " + timeout + " (pid " + pid + " đã bị kill)");
        this.command = List.copyOf(command);
        this.timeout = timeout;
        this.pid = pid;
        this.stdout = stdout;
        this.stderr = stderr;
    }

    public List<String> command() {
        return command;
    }

    public Duration timeout() {
        return timeout;
    }

    public long pid() {
        return pid;
    }

    public String stdout() {
        return stdout;
    }

    public String stderr() {
        return stderr;
    }
}
