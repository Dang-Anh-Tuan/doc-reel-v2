package com.tuan.docreel.common;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Chương trình giả cho ProcessRunnerTest (chạy bằng JVM con, không phụ thuộc lệnh của hệ điều hành). */
public final class FakeProcess {

    private FakeProcess() {}

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "echo" -> System.out.print(args[1]);
            case "fail" -> {
                System.err.print(args[2]);
                System.exit(Integer.parseInt(args[1]));
            }
            case "sleep" -> Thread.sleep(Long.parseLong(args[1]));
            case "env" -> System.out.print(System.getenv(args[1]));
            case "pwd" -> System.out.print(Path.of("").toAbsolutePath());
            case "big" -> {
                String chunk = "x".repeat(1024);
                int kb = Integer.parseInt(args[1]);
                for (int i = 0; i < kb; i++) {
                    System.out.print(chunk);
                    System.err.print(chunk);
                }
            }
            case "spawn-and-sleep" -> {
                Process child = new ProcessBuilder(command("sleep", "60000")).start();
                Files.writeString(Path.of(args[1]), Long.toString(child.pid()), StandardCharsets.UTF_8);
                Thread.sleep(60_000);
            }
            default -> System.exit(99);
        }
    }

    /** Lệnh chạy FakeProcess trong JVM con. */
    public static List<String> command(String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-Dstdout.encoding=UTF-8");
        cmd.add("-Dstderr.encoding=UTF-8");
        cmd.add("-cp");
        cmd.add(classpath());
        cmd.add(FakeProcess.class.getName());
        cmd.addAll(List.of(args));
        return cmd;
    }

    private static String classpath() {
        try {
            return Path.of(FakeProcess.class
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
