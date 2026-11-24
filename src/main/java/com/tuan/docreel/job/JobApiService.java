package com.tuan.docreel.job;

import com.tuan.docreel.common.Result;
import com.tuan.docreel.script.FieldError;
import com.tuan.docreel.script.ScriptParser;
import com.tuan.docreel.script.VideoScript;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Các thao tác REST trên job (UC2 gửi, UC3 xem, UC4 retry). Controller chỉ gọi lớp này. */
@Service
public class JobApiService {

    static final int LIST_LIMIT = 200;

    private final JobService jobs;
    private final JobRepository repository;
    private final JobAttemptRepository attempts;
    private final ScriptParser parser = new ScriptParser();

    public JobApiService(JobService jobs, JobRepository repository, JobAttemptRepository attempts) {
        this.jobs = jobs;
        this.repository = repository;
        this.attempts = attempts;
    }

    /** Validate rồi tạo job. Không {@code @Transactional}: {@link JobService#submit} tự quản transaction. */
    public UUID submit(byte[] json) {
        Result<VideoScript, List<FieldError>> parsed = parser.parse(json);
        if (!parsed.isOk()) {
            throw new InvalidScriptException(parsed.error());
        }
        SubmitResult r = jobs.submit(parsed.value(), null);
        if (!r.created()) {
            JobState state =
                    repository.findById(r.jobId()).map(VideoJob::getState).orElse(null);
            throw new DuplicateJobException(r.jobId(), state);
        }
        return r.jobId();
    }

    /** Job mới nhất trước, tối đa {@value #LIST_LIMIT} dòng; {@code state} null = mọi trạng thái. */
    @Transactional(readOnly = true)
    public List<JobSummary> list(JobState state) {
        var page = PageRequest.of(0, LIST_LIMIT, Sort.by(Sort.Direction.DESC, "createdAt"));
        var rows = state == null ? repository.findAll(page).getContent() : repository.findByState(state, page);
        return rows.stream().map(JobSummary::of).toList();
    }

    @Transactional(readOnly = true)
    public JobDetail detail(UUID id) {
        VideoJob job = repository.findById(id).orElseThrow(() -> new JobNotFoundException(id));
        return JobDetail.of(job, attempts.findByJobIdOrderByStartedAt(id));
    }

    /** FAILED → RECEIVED. Hai retry đồng thời: bên thua gặp lỗi khóa lạc quan (→ 409 ở handler). */
    @Transactional
    public JobSummary retry(UUID id) {
        VideoJob job = repository.findById(id).orElseThrow(() -> new JobNotFoundException(id));
        if (job.getState() != JobState.FAILED) {
            throw new JobNotRetryableException(id, job.getState());
        }
        job.retry();
        return JobSummary.of(repository.saveAndFlush(job));
    }
}
