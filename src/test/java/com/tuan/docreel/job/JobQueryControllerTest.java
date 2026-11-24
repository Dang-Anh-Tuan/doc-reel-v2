package com.tuan.docreel.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tuan.docreel.support.TestAppProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

/** P13: phân trang + lọc state, chi tiết có currentStep/slides/youtubeUrl, phục vụ PNG an toàn (MockMvc, repo mock). */
class JobQueryControllerTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    @TempDir
    Path root;

    private final JobRepository repository = mock(JobRepository.class);
    private final JobAttemptRepository attempts = mock(JobAttemptRepository.class);
    private MockMvc mvc;
    private Path work;

    @BeforeEach
    void setUp() {
        var slides = new JobSlides(TestAppProperties.withDirsUnder(root), JSON);
        var service = new JobApiService(mock(JobService.class), repository, attempts, slides, JSON);
        mvc = MockMvcBuilders.standaloneSetup(new JobController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        work = root.resolve("work");
    }

    private VideoJob saved(VideoJob job) {
        when(repository.findById(job.getId())).thenReturn(Optional.of(job));
        return job;
    }

    private VideoJob sampleJob() {
        return saved(JobSlidesTest.jobWithScript(JSON.writeValueAsString(JobFixtures.sampleScript())));
    }

    private void writePng(UUID jobId, String name) throws Exception {
        Path dir = Files.createDirectories(work.resolve(jobId.toString()));
        Files.write(dir.resolve(name), PNG);
    }

    @Test
    void listPagesAndReportsTotalInHeaders() throws Exception {
        VideoJob job = sampleJob();
        Page<VideoJob> page = new PageImpl<>(List.of(job), Pageable.ofSize(2).withPage(1), 3);
        when(repository.findAll(any(Pageable.class))).thenReturn(page);

        mvc.perform(get("/api/jobs").param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(header().string(JobController.TOTAL_COUNT, "3"))
                .andExpect(header().string(JobController.PAGE, "1"))
                .andExpect(header().string(JobController.PAGE_SIZE, "2"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Rules of Hooks & useReducer"))
                .andExpect(jsonPath("$[0].currentStep").value("RENDER"))
                .andExpect(jsonPath("$[0].ttsProvider").value("EDGE"));

        ArgumentCaptor<Pageable> req = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(req.capture());
        assertThat(req.getValue().getPageNumber()).isEqualTo(1);
        assertThat(req.getValue().getPageSize()).isEqualTo(2);
        assertThat(req.getValue().getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void listFiltersByStateAndClampsPaging() throws Exception {
        when(repository.findByState(any(), any())).thenReturn(Page.empty());

        mvc.perform(get("/api/jobs")
                        .param("state", "FAILED")
                        .param("page", "-3")
                        .param("size", "100000"))
                .andExpect(status().isOk())
                .andExpect(header().string(JobController.PAGE, "0"))
                .andExpect(header().string(JobController.PAGE_SIZE, "" + JobApiService.MAX_PAGE_SIZE))
                .andExpect(header().string(JobController.TOTAL_COUNT, "0"))
                .andExpect(jsonPath("$").isArray());

        ArgumentCaptor<Pageable> req = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByState(org.mockito.ArgumentMatchers.eq(JobState.FAILED), req.capture());
        assertThat(req.getValue().getPageNumber()).isZero();
        assertThat(req.getValue().getPageSize()).isEqualTo(JobApiService.MAX_PAGE_SIZE);
        verify(repository, never()).findAll(any(Pageable.class));
    }

    @Test
    void listSizeZeroBecomesOne() throws Exception {
        when(repository.findAll(any(Pageable.class))).thenReturn(Page.empty());
        mvc.perform(get("/api/jobs").param("size", "0"))
                .andExpect(status().isOk())
                .andExpect(header().string(JobController.PAGE_SIZE, "1"));
    }

    @Test
    void listWithBrokenScriptHasNullTitle() throws Exception {
        VideoJob job = saved(JobSlidesTest.jobWithScript("không phải json"));
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(job)));
        mvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").doesNotExist());
    }

    @Test
    void detailHasCurrentStepSlidesYoutubeUrlAndAttemptTimes() throws Exception {
        VideoJob job = sampleJob();
        for (JobStep step : JobStep.values()) {
            if (step == JobStep.UPLOAD) {
                job.markUploaded("abc123XYZ");
            } else {
                job.completeStep(step);
            }
        }
        writePng(job.getId(), "s01-title.png");
        writePng(job.getId(), "s09-quiz-reveal.png");
        when(attempts.findByJobIdOrderByStartedAt(job.getId()))
                .thenReturn(List.of(new JobAttempt(job.getId(), JobStep.RENDER, 1, "SUCCESS", 1500, null)));

        mvc.perform(get("/api/jobs/" + job.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Rules of Hooks & useReducer"))
                .andExpect(jsonPath("$.state").value("UPLOADED"))
                .andExpect(jsonPath("$.currentStep").doesNotExist())
                .andExpect(jsonPath("$.youtubeUrl").value("https://youtu.be/abc123XYZ"))
                .andExpect(jsonPath("$.slides.length()").value(2))
                .andExpect(jsonPath("$.slides[0].url").value("/api/jobs/" + job.getId() + "/slides/1.png"))
                .andExpect(jsonPath("$.slides[1].index").value(10))
                .andExpect(jsonPath("$.slides[1].name").value("s09-quiz-reveal"))
                .andExpect(jsonPath("$.attempts[0].durationMs").value(1500))
                .andExpect(jsonPath("$.attempts[0].finishedAt").exists());
    }

    @Test
    void failedJobCurrentStepIsTheFailedStep() throws Exception {
        VideoJob job = sampleJob();
        job.completeStep(JobStep.RENDER);
        job.fail("TTS lỗi");
        mvc.perform(get("/api/jobs/" + job.getId()))
                .andExpect(jsonPath("$.currentStep").value("VOICE"))
                .andExpect(jsonPath("$.youtubeUrl").doesNotExist())
                .andExpect(jsonPath("$.slides").isEmpty());
    }

    @Test
    void slideOfJobIsServedAsPng() throws Exception {
        VideoJob job = sampleJob();
        writePng(job.getId(), "s01-title.png");

        mvc.perform(get("/api/jobs/" + job.getId() + "/slides/1.png"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG));
    }

    @Test
    void slideNotBelongingToJobIs404() throws Exception {
        VideoJob owner = sampleJob();
        VideoJob other = sampleJob();
        writePng(owner.getId(), "s01-title.png");

        mvc.perform(get("/api/jobs/" + other.getId() + "/slides/1.png")).andExpect(status().isNotFound());
        mvc.perform(get("/api/jobs/" + UUID.randomUUID() + "/slides/1.png")).andExpect(status().isNotFound());
    }

    @Test
    void invalidSlideIndexIs404() throws Exception {
        VideoJob job = sampleJob();
        writePng(job.getId(), "s01-title.png");
        for (String n : List.of("0", "01", "abc", "-1", "12", "1.png")) {
            mvc.perform(get("/api/jobs/" + job.getId() + "/slides/" + n + ".png"))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void pathTraversalIs404() throws Exception {
        VideoJob owner = sampleJob();
        VideoJob attacker = sampleJob();
        writePng(owner.getId(), "s01-title.png");
        Files.write(work.resolve("secret.png"), PNG);

        String base = "/api/jobs/" + attacker.getId() + "/slides/";
        for (String n : List.of(
                "..%2F" + owner.getId() + "%2Fs01-title",
                "..%2F..%2Fsecret",
                "%2E%2E%2Fsecret",
                "..%5Csecret",
                "1%2F..%2F1")) {
            mvc.perform(get(java.net.URI.create(base + n + ".png"))).andExpect(status().isNotFound());
        }
    }

    @Test
    void retryReturnsSummaryWithTitle() throws Exception {
        VideoJob job = sampleJob();
        job.fail("x");
        when(repository.saveAndFlush(job)).thenReturn(job);
        mvc.perform(post("/api/jobs/" + job.getId() + "/retry"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.title").value("Rules of Hooks & useReducer"))
                .andExpect(jsonPath("$.currentStep").value("RENDER"));
    }
}
