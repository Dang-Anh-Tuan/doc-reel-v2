package com.tuan.docreel.job;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    @GetMapping
    public List<JobSummary> list(@RequestParam(required = false) JobState state) {
        return service.list(state);
    }

    @GetMapping("/{id}")
    public JobDetail get(@PathVariable UUID id) {
        return service.detail(id);
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<JobSummary> retry(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(service.retry(id));
    }
}
