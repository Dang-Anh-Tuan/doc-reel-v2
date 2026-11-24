package com.tuan.docreel.voice;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LexiconTest {

    @TempDir
    Path dir;

    private Path write(String yaml, long millis) throws IOException {
        Path file = dir.resolve("lexicon.yaml");
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
        Files.setLastModifiedTime(file, FileTime.fromMillis(millis));
        return file;
    }

    @Test
    void replacesWholeWordsOnly() throws IOException {
        Lexicon lexicon = new Lexicon(write("JPA: \"Giê Pi Ây\"\n", 1000), false);
        assertThat(lexicon.apply("JPA là chuẩn. Dùng JPAs thì không, (JPA), JPA."))
                .isEqualTo("Giê Pi Ây là chuẩn. Dùng JPAs thì không, (Giê Pi Ây), Giê Pi Ây.");
    }

    @Test
    void caseInsensitiveByConfig() throws IOException {
        Path file = write("JPA: \"Giê Pi Ây\"\n", 1000);
        assertThat(new Lexicon(file, false).apply("jpa và Jpa")).isEqualTo("Giê Pi Ây và Giê Pi Ây");
        assertThat(new Lexicon(file, true).apply("jpa và JPA")).isEqualTo("jpa và Giê Pi Ây");
    }

    @Test
    void longerTermWinsAndOutputIsNotReplacedAgain() throws IOException {
        Lexicon lexicon = new Lexicon(write("Spring: \"Xờ-pring\"\nSpring Boot: \"Xờ-pring But\"\n", 1000), false);
        assertThat(lexicon.apply("Spring Boot và Spring")).isEqualTo("Xờ-pring But và Xờ-pring");
    }

    @Test
    void replacementWithSpecialCharsIsLiteral() throws IOException {
        Lexicon lexicon = new Lexicon(write("cost: \"$1 \\\\ x\"\n", 1000), false);
        assertThat(lexicon.apply("the cost")).isEqualTo("the $1 \\ x");
    }

    @Test
    void reloadsWhenFileChanges() throws IOException {
        Path file = write("JPA: \"một\"\n", 1000);
        Lexicon lexicon = new Lexicon(file, false);
        assertThat(lexicon.apply("JPA")).isEqualTo("một");
        write("JPA: \"hai\"\n", 5000);
        assertThat(lexicon.apply("JPA")).isEqualTo("hai");
    }

    @Test
    void missingFileMeansNoReplacementAndBadFileKeepsOld() throws IOException {
        Lexicon missing = new Lexicon(dir.resolve("none.yaml"), false);
        assertThat(missing.apply("JPA")).isEqualTo("JPA");
        assertThat(missing.apply("")).isEmpty();

        Path file = write("JPA: \"một\"\n", 1000);
        Lexicon lexicon = new Lexicon(file, false);
        assertThat(lexicon.apply("JPA")).isEqualTo("một");
        write("- not\n- a map\n", 5000);
        assertThat(lexicon.apply("JPA")).isEqualTo("một");
        write("{ broken: [", 9000);
        assertThat(lexicon.apply("JPA")).isEqualTo("một");
        Files.delete(file);
        assertThat(lexicon.apply("JPA")).isEqualTo("JPA");
    }

    @Test
    void emptyFileIsFine() throws IOException {
        assertThat(new Lexicon(write("", 1000), false).apply("JPA")).isEqualTo("JPA");
    }
}
