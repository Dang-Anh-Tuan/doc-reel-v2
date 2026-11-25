package com.tuan.docreel.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tuan.docreel.script.FieldError;
import com.tuan.docreel.script.VideoType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Mỗi mã trả của mỗi endpoint (SPEC mục 11), service được mock. */
class JobControllerTest {

    private final JobApiService service = mock(JobApiService.class);
    private final UUID id = UUID.randomUUID();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new JobController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private JobSummary summary(JobState state) {
        return new JobSummary(
                id,
                "t1",
                "Tiêu đề",
                1,
                VideoType.SLIDES,
                state,
                JobStep.RENDER,
                null,
                null,
                "EDGE",
                null,
                null,
                Instant.now(),
                Instant.now(),
                0,
                0);
    }

    @Test
    void createReturns201WithJobIdAndLocation() throws Exception {
        when(service.submit(any())).thenReturn(id);
        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/jobs/" + id))
                .andExpect(jsonPath("$.jobId").value(id.toString()));
    }

    @Test
    void createInvalidReturns400WithFieldErrors() throws Exception {
        when(service.submit(any()))
                .thenThrow(new InvalidScriptException(
                        List.of(new FieldError("scenes[1].id", "trùng id"), new FieldError("title", "quá dài"))));
        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Kịch bản không hợp lệ"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].field").value("scenes[1].id"))
                .andExpect(jsonPath("$.errors[0].message").value("trùng id"));
    }

    @Test
    void createDuplicateReturns409WithRetryHintForFailedJob() throws Exception {
        when(service.submit(any())).thenThrow(new DuplicateJobException(id, JobState.FAILED));
        mvc.perform(post("/api/jobs").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.jobId").value(id.toString()))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("retry")));
    }

    @Test
    void createWithWrongContentTypeIs415ProblemDetail() throws Exception {
        mvc.perform(post("/api/jobs").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    void listReturns200AndFiltersByState() throws Exception {
        when(service.list(JobState.FAILED, 0, JobApiService.MAX_PAGE_SIZE))
                .thenReturn(new JobPage(List.of(summary(JobState.FAILED)), 1, 0, JobApiService.MAX_PAGE_SIZE));
        mvc.perform(get("/api/jobs").param("state", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Total-Count", "1"))
                .andExpect(jsonPath("$[0].state").value("FAILED"))
                .andExpect(jsonPath("$[0].currentStep").value("RENDER"));
    }

    @Test
    void listWithUnknownStateIs400() throws Exception {
        mvc.perform(get("/api/jobs").param("state", "NOPE")).andExpect(status().isBadRequest());
    }

    @Test
    void getReturns200() throws Exception {
        when(service.detail(id))
                .thenReturn(new JobDetail(
                        id,
                        "t1",
                        "Tiêu đề",
                        1,
                        VideoType.SLIDES,
                        JobState.RECEIVED,
                        JobStep.RENDER,
                        null,
                        null,
                        null,
                        null,
                        "EDGE",
                        "api",
                        null,
                        Instant.now(),
                        Instant.now(),
                        List.of(),
                        List.of(),
                        new JobDetail.Cost("EDGE", 0, 0, 0, null, 0, 0)));
        mvc.perform(get("/api/jobs/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.attempts").isArray());
    }

    @Test
    void getUnknownReturns404ProblemDetail() throws Exception {
        when(service.detail(id)).thenThrow(new JobNotFoundException(id));
        mvc.perform(get("/api/jobs/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").exists());
    }

    @Test
    void retryReturns202() throws Exception {
        when(service.retry(id)).thenReturn(summary(JobState.RECEIVED));
        mvc.perform(post("/api/jobs/" + id + "/retry"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.state").value("RECEIVED"));
    }

    @Test
    void retryJobNotFailedReturns409ProblemDetail() throws Exception {
        when(service.retry(id)).thenThrow(new JobNotRetryableException(id, JobState.RENDERED));
        mvc.perform(post("/api/jobs/" + id + "/retry"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("RENDERED")));
    }

    @Test
    void retryUnknownReturns404() throws Exception {
        when(service.retry(id)).thenThrow(new JobNotFoundException(id));
        mvc.perform(post("/api/jobs/" + id + "/retry")).andExpect(status().isNotFound());
    }

    @Test
    void unexpectedErrorIs500WithoutLeakingDetails() throws Exception {
        when(service.detail(id)).thenThrow(new IllegalStateException("secret-internal"));
        mvc.perform(get("/api/jobs/" + id))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret-internal"))));
    }
}
