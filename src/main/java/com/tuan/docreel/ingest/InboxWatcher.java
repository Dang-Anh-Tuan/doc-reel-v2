package com.tuan.docreel.ingest;

import com.tuan.docreel.common.AppProperties;
import com.tuan.docreel.common.Result;
import com.tuan.docreel.job.JobRepository;
import com.tuan.docreel.job.JobService;
import com.tuan.docreel.job.JobState;
import com.tuan.docreel.job.SubmitResult;
import com.tuan.docreel.script.FieldError;
import com.tuan.docreel.script.Origin;
import com.tuan.docreel.script.ScriptParser;
import com.tuan.docreel.script.VideoScript;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * UC1: quét {@code inbox/} mỗi 10s, chỉ đọc {@code *.json} (không đọc {@code .part}).
 * Hợp lệ → job rồi file sang {@code work/{jobId}/input.json}; sai → {@code failed/} kèm {@code .error.txt};
 * trùng khóa idempotency → {@code duplicate/}. Việc chuyển từ work sang {@code done/} khi job xong thuộc pipeline.
 */
@Component
public class InboxWatcher {

    private static final Logger log = LoggerFactory.getLogger(InboxWatcher.class);
    static final String INPUT_FILE = "input.json";
    static final String ERROR_SUFFIX = ".error.txt";
    private static final String CHANNEL = "inbox";

    private final AppProperties.Dirs dirs;
    private final JobService jobs;
    private final JobRepository jobRepository;
    private final ScriptParser parser = new ScriptParser();

    public InboxWatcher(AppProperties props, JobService jobs, JobRepository jobRepository) {
        this.jobRepository = jobRepository;
        this.dirs = props.dirs();
        this.jobs = jobs;
    }

    @Scheduled(
            initialDelayString = "${docreel.inbox.poll-interval:PT10S}",
            fixedDelayString = "${docreel.inbox.poll-interval:PT10S}")
    public void scheduled() {
        try {
            scan();
        } catch (RuntimeException e) {
            log.error("Quét inbox lỗi, thử lại ở lần sau", e);
        }
    }

    /** Xử lý mọi file {@code *.json} đang có trong inbox; trả số file đã xử lý xong (đã di chuyển). */
    public synchronized int scan() {
        try {
            for (Path d : dirs.all()) {
                Files.createDirectories(d);
            }
        } catch (IOException e) {
            log.error("Không tạo được thư mục làm việc", e);
            return 0;
        }
        List<Path> files;
        try (Stream<Path> s = Files.list(dirs.inbox())) {
            files = s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.error("Không đọc được inbox {}", dirs.inbox(), e);
            return 0;
        }
        int handled = 0;
        for (Path file : files) {
            try {
                handle(file);
                handled++;
            } catch (IOException | RuntimeException e) {
                // Giữ nguyên file trong inbox để lần quét sau thử lại (vd DB tạm mất kết nối).
                log.error("Xử lý {} lỗi, giữ lại trong inbox", file.getFileName(), e);
            }
        }
        return handled;
    }

    private void handle(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        Result<VideoScript, List<FieldError>> parsed = parser.parse(bytes);
        String name = file.getFileName().toString();
        if (!parsed.isOk()) {
            Path target = freeTarget(dirs.failed(), name);
            // Ghi .error.txt trước: nếu crash giữa chừng, file vẫn ở inbox và lần sau xử lý lại.
            Files.writeString(
                    target.resolveSibling(target.getFileName() + ERROR_SUFFIX),
                    errorText(parsed.error()),
                    StandardCharsets.UTF_8);
            Files.move(file, target);
            log.warn(
                    "File {} không hợp lệ ({} lỗi) → failed/",
                    name,
                    parsed.error().size());
            return;
        }
        VideoScript script = parsed.value();
        Origin origin = script.origin() != null ? script.origin() : new Origin(CHANNEL, null, name);
        SubmitResult result = jobs.submit(script, origin);
        UUID jobId = result.jobId();
        if (!result.created()) {
            Path input = dirs.work().resolve(jobId.toString()).resolve(INPUT_FILE);
            boolean finished = jobRepository
                    .findById(jobId)
                    .map(j -> j.getState() == JobState.UPLOADED)
                    .orElse(true);
            if (finished || Files.exists(input)) {
                moveTo(file, dirs.duplicate(), name);
                log.info("File {} trùng job {} → duplicate/", name, jobId);
                return;
            }
            // Lần trước crash giữa submit và move: job đã tạo mà chưa có input → hoàn tất giao dịch dang dở.
            log.info("File {} thuộc job {} đã tạo nhưng chưa được chuyển → work/", name, jobId);
        }
        MDC.put("jobId", jobId.toString());
        try {
            Path jobDir = dirs.work().resolve(jobId.toString());
            Files.createDirectories(jobDir);
            Files.move(file, jobDir.resolve(INPUT_FILE), StandardCopyOption.REPLACE_EXISTING);
            log.info("File {} → job {} (work/)", name, jobId);
        } finally {
            MDC.remove("jobId");
        }
    }

    /** Chuyển file sang thư mục đích; trùng tên thì thêm tiền tố thời gian để không ghi đè file cũ. */
    private static Path moveTo(Path file, Path dir, String name) throws IOException {
        return Files.move(file, freeTarget(dir, name));
    }

    private static Path freeTarget(Path dir, String name) {
        Path target = dir.resolve(name);
        return Files.exists(target) ? dir.resolve(System.currentTimeMillis() + "-" + name) : target;
    }

    private static String errorText(List<FieldError> errors) {
        StringBuilder sb = new StringBuilder();
        for (FieldError e : errors) {
            sb.append(e.path()).append(": ").append(e.message()).append(System.lineSeparator());
        }
        return sb.toString();
    }
}
