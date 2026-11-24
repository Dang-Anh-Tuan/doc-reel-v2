package com.tuan.docreel.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.render.RenderStep;
import com.tuan.docreel.script.ScriptParser;
import com.tuan.docreel.script.VideoScript;
import com.tuan.docreel.support.TestAppProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class JobSlidesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    @TempDir
    Path root;

    private JobSlides slides;
    private Path work;

    @BeforeEach
    void setUp() {
        slides = new JobSlides(TestAppProperties.withDirsUnder(root), JSON);
        work = root.resolve("work");
    }

    static VideoJob jobWithScript(String scriptJson) {
        return new VideoJob("R-08", 1, "h" + UUID.randomUUID(), null, "EDGE", "{}", "api", null, scriptJson);
    }

    private VideoJob sampleJob() {
        return jobWithScript(JSON.writeValueAsString(JobFixtures.sampleScript()));
    }

    private Path write(UUID jobId, String name, byte[] bytes) throws Exception {
        Path dir = Files.createDirectories(work.resolve(jobId.toString()));
        return Files.write(dir.resolve(name), bytes);
    }

    @Test
    void imageNamesMatchRenderStep() {
        VideoScript script = JobFixtures.sampleScript();
        var expected = script.scenes().stream()
                .flatMap(s -> RenderStep.imageNames(s).stream())
                .toList();
        assertThat(JobSlides.imageNames(script)).containsExactlyElementsOf(expected);
        assertThat(JobSlides.imageNames(script)).contains("s09-quiz.png", "s09-quiz-reveal.png");
    }

    @Test
    void scriptRoundTripsAndBrokenJsonIsEmpty() {
        assertThat(slides.script(sampleJob())).map(VideoScript::title).contains("Rules of Hooks & useReducer");
        assertThat(slides.script(jobWithScript("không phải json"))).isEmpty();
        assertThat(JobSlides.imageNames(slides.script(jobWithScript("{}")).orElse(null)))
                .isEmpty();
        assertThat(JobSlides.imageNames(null)).isEmpty();
    }

    @Test
    void renderedListsOnlyExistingNonEmptyFilesKeepingIndex() throws Exception {
        VideoJob job = sampleJob();
        write(job.getId(), "s01-title.png", PNG);
        write(job.getId(), "s03-points.png", PNG);
        write(job.getId(), "s02-definition.png", new byte[0]);

        var list = slides.rendered(job.getId(), slides.script(job).orElseThrow());

        assertThat(list)
                .extracting(JobDetail.Slide::index, JobDetail.Slide::name, JobDetail.Slide::url)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                1, "s01-title", "/api/jobs/" + job.getId() + "/slides/1.png"),
                        org.assertj.core.groups.Tuple.tuple(
                                3, "s03-points", "/api/jobs/" + job.getId() + "/slides/3.png"));
    }

    @Test
    void findReturnsFileOfThisJobOnly() throws Exception {
        VideoJob a = sampleJob();
        VideoJob b = sampleJob();
        Path file = write(a.getId(), "s01-title.png", PNG);

        assertThat(slides.find(a, "1")).contains(file.toAbsolutePath().normalize());
        assertThat(slides.find(b, "1")).as("cùng tên file nhưng thuộc job khác").isEmpty();
    }

    @Test
    void invalidIndexIsEmpty() throws Exception {
        VideoJob job = sampleJob();
        write(job.getId(), "s01-title.png", PNG);
        for (String n :
                new String[] {"0", "01", "-1", "+1", "abc", "", "1 ", "12", "99999", "../1", "..", "1/../1", null}) {
            assertThat(slides.find(job, n)).as("n=%s", n).isEmpty();
        }
    }

    @Test
    void traversalInSceneIdIsBlocked() throws Exception {
        VideoJob victim = sampleJob();
        write(victim.getId(), "s01-title.png", PNG);
        Files.write(work.resolve("secret.png"), PNG);
        // Kịch bản trong DB bị sửa tay: id scene chứa ../ (validator thật không cho, đây là lớp phòng thủ thứ hai)
        VideoJob evil = jobWithScript("{\"scenes\":[{\"type\":\"title\",\"id\":\"../" + victim.getId()
                + "/s01-title\"},{\"type\":\"title\",\"id\":\"../secret\"}]}");
        Files.createDirectories(work.resolve(evil.getId().toString()));

        assertThat(JobSlides.imageNames(slides.script(evil).orElseThrow())).hasSize(2);
        assertThat(slides.find(evil, "1")).isEmpty();
        assertThat(slides.find(evil, "2")).isEmpty();
        assertThat(slides.rendered(evil.getId(), slides.script(evil).orElseThrow()))
                .isEmpty();
    }

    @Test
    void directoryNamedLikeSlideIsNotServed() throws Exception {
        VideoJob job = sampleJob();
        Files.createDirectories(work.resolve(job.getId().toString()).resolve("s01-title.png"));
        assertThat(slides.find(job, "1")).isEmpty();
    }

    @Test
    void parserRejectsTraversalSceneIds() {
        // Lớp phòng thủ thứ nhất: kịch bản qua API/inbox không thể có id như vậy
        String json =
                new String(JSON.writeValueAsBytes(JobFixtures.sampleScript())).replace("\"s01-title\"", "\"../x\"");
        assertThat(new ScriptParser().parse(json.getBytes()).isOk()).isFalse();
    }

    @Test
    void invalidPathCharacterInSceneIdIsEmptyNotError() throws Exception {
        VideoJob job = jobWithScript("{\"scenes\":[{\"type\":\"title\",\"id\":\"bad\\u0000name\"}]}");
        Files.createDirectories(work.resolve(job.getId().toString()));

        assertThat(JobSlides.imageNames(slides.script(job).orElseThrow())).containsExactly("bad\u0000name.png");
        assertThat(slides.find(job, "1")).isEmpty();
        assertThat(slides.rendered(job.getId(), slides.script(job).orElseThrow()))
                .isEmpty();
    }
}
