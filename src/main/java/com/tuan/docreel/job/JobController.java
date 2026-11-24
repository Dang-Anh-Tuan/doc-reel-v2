package com.tuan.docreel.job;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST API job (SPEC mục 11). */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobApiService service;

    public JobController(JobApiService service) {
        this.service = service;
    }

    /** Nhận JSON thô để ScriptParser kiểm tra chặt (trường lạ, key trùng...). */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, UUID>> create(@RequestBody byte[] body) {
        UUID id = service.submit(body);
        return ResponseEntity.created(URI.create("/api/jobs/" + id)).body(Map.of("jobId", id));
    }

    static final String TOTAL_COUNT = "X-Total-Count";
    static final String PAGE = "X-Page";
    static final String PAGE_SIZE = "X-Page-Size";

    /**
     * Body vẫn là mảng như P3 (không phá client cũ); thông tin phân trang nằm ở header {@value #TOTAL_COUNT},
     * {@value #PAGE}, {@value #PAGE_SIZE}. {@code page} đếm từ 0.
     */
    @GetMapping
    public ResponseEntity<List<JobSummary>> list(
            @RequestParam(required = false) JobState state,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + JobApiService.MAX_PAGE_SIZE) int size) {
        JobPage result = service.list(state, page, size);
        return ResponseEntity.ok()
                .header(TOTAL_COUNT, Long.toString(result.total()))
                .header(PAGE, Integer.toString(result.page()))
                .header(PAGE_SIZE, Integer.toString(result.size()))
                .body(result.items());
    }

    @GetMapping("/{id}")
    public JobDetail get(@PathVariable UUID id) {
        return service.detail(id);
    }

    /** PNG đã render; {@code n} nhận dạng chuỗi để mọi giá trị lạ (chữ, {@code ../}) đều thành 404. */
    @GetMapping("/{id}/slides/{n}.png")
    public ResponseEntity<Resource> slide(@PathVariable UUID id, @PathVariable String n) {
        return service.slide(id, n)
                .map(path -> ResponseEntity.ok()
                        .contentType(MediaType.IMAGE_PNG)
                        .cacheControl(CacheControl.noCache())
                        .body((Resource) new FileSystemResource(path)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<JobSummary> retry(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(service.retry(id));
    }
}
