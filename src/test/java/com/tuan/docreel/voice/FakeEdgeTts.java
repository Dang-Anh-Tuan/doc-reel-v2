package com.tuan.docreel.voice;

import com.tuan.docreel.common.ProcessRequest;
import com.tuan.docreel.common.ProcessRunner;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * CLI {@code edge-tts} giả chạy trong JVM con. Tham số đầu là chế độ: {@code ok} (ghi audio theo độ dài câu),
 * {@code fail} (thoát mã 3), {@code hang} (treo → quá giờ), {@code empty} (file rỗng); phần còn lại giống edge-tts thật.
 */
public final class FakeEdgeTts {

    private FakeEdgeTts() {}

    /** Runner chạy FakeEdgeTts thật (process thật, exit code và timeout thật) thay cho lệnh edge-tts. */
    public static ProcessRunner runner(String mode) {
        return new ProcessRunner() {
            @Override
            public com.tuan.docreel.common.ProcessResult run(ProcessRequest request) {
                List<String> cmd = new ArrayList<>();
                cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
                cmd.add("-Dfile.encoding=UTF-8");
                cmd.add("-cp");
                cmd.add(classpath());
                cmd.add(FakeEdgeCli.class.getName());
                cmd.add(mode);
                cmd.addAll(request.command().subList(1, request.command().size()));
                return super.run(
                        new ProcessRequest(cmd, request.workingDir(), request.environment(), request.timeout()));
            }
        };
    }

    private static String classpath() {
        try {
            return Path.of(FakeEdgeTts.class
                            .getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI())
                    .toString();
        } catch (java.net.URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
