package com.tuan.docreel.script;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.common.Result;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Hợp đồng dùng chung Java/TypeScript: samples/full hợp lệ, samples/invalid báo đúng đường dẫn lỗi. */
class ScriptContractTest {

    private static final Path SAMPLES = Path.of("samples");
    private final ScriptParser parser = new ScriptParser();

    static Stream<Path> validFiles() throws IOException {
        try (Stream<Path> files = Files.list(SAMPLES.resolve("full"))) {
            return files.filter(f -> f.toString().endsWith(".json")).sorted().toList().stream();
        }
    }

    static Stream<String> invalidFiles() throws IOException {
        return expected().keySet().stream().sorted();
    }

    private static Map<String, List<String>> expected() throws IOException {
        return JsonMapper.builder()
                .build()
                .readValue(
                        Files.readAllBytes(SAMPLES.resolve("invalid/expected.json")),
                        new TypeReference<Map<String, List<String>>>() {});
    }

    @ParameterizedTest
    @MethodSource("validFiles")
    void acceptsEveryFullSample(Path file) throws IOException {
        Result<VideoScript, List<FieldError>> result = parser.parse(Files.readAllBytes(file));

        assertThat(result.isOk())
                .as("%s: %s", file, result.isOk() ? "" : result.error())
                .isTrue();
    }

    @ParameterizedTest
    @MethodSource("invalidFiles")
    void reportsExactErrorPathsForInvalidSample(String name) throws IOException {
        Result<VideoScript, List<FieldError>> result =
                parser.parse(Files.readAllBytes(SAMPLES.resolve("invalid/" + name)));

        assertThat(result.isOk()).isFalse();
        assertThat(result.error())
                .extracting(FieldError::path)
                .containsExactlyElementsOf(expected().get(name));
        assertThat(result.error()).allSatisfy(e -> assertThat(e.message()).isNotBlank());
    }

    @Test
    void everyInvalidFileOnDiskIsListedInExpectedJson() throws IOException {
        try (Stream<Path> files = Files.list(SAMPLES.resolve("invalid"))) {
            List<String> onDisk = files.map(f -> f.getFileName().toString())
                    .filter(n -> !n.equals("expected.json"))
                    .sorted()
                    .toList();
            assertThat(expected().keySet()).containsExactlyInAnyOrderElementsOf(onDisk);
            assertThat(onDisk).hasSizeGreaterThanOrEqualTo(15);
        }
    }
}
