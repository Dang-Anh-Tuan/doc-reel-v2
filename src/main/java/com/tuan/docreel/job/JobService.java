package com.tuan.docreel.job;

import com.tuan.docreel.script.Origin;
import com.tuan.docreel.script.ScriptParser;
import com.tuan.docreel.script.VideoScript;
import com.tuan.docreel.settings.TtsSettings;
import com.tuan.docreel.settings.TtsSettingsProvider;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** UC5: nhận kịch bản đã hợp lệ, tạo job một lần duy nhất cho mỗi {@code topicId:version:hash}. */
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);
    private static final String DEFAULT_CHANNEL = "api";

    private final JobRepository repository;
    private final TtsSettingsProvider ttsSettings;
    private final JsonMapper mapper;
    private final TransactionTemplate tx;

    public JobService(
            JobRepository repository,
            TtsSettingsProvider ttsSettings,
            JsonMapper mapper,
            PlatformTransactionManager txManager) {
        this.repository = repository;
        this.ttsSettings = ttsSettings;
        this.mapper = mapper;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * Tạo job RECEIVED, hoặc trả {@code DUPLICATE} nếu khóa idempotency đã có (kể cả khi 2 lời gọi chạy đồng thời:
     * unique constraint quyết định, bên thua được map thành DUPLICATE, không ném lỗi). Không dùng
     * {@code @Transactional} ở đây vì lỗi trùng khóa xảy ra lúc commit nên phải bắt ở ngoài transaction.
     */
    public SubmitResult submit(VideoScript script, Origin origin) {
        String hash = ScriptParser.contentHash(script);
        String key = IdempotencyKey.of(script.topicId(), script.version(), hash);

        var existing = repository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return duplicate(existing.get().getId(), key);
        }
        TtsSettings tts = ttsSettings.current();
        Origin effective = origin != null ? origin : script.origin();
        VideoJob job = new VideoJob(
                script.topicId(),
                script.version(),
                hash,
                script.videoType(),
                tts.provider(),
                tts.json(),
                channel(effective),
                ref(effective),
                mapper.writeValueAsString(script));
        try {
            tx.executeWithoutResult(s -> repository.saveAndFlush(job));
        } catch (DataIntegrityViolationException e) {
            // Bên thua cuộc đua: dòng của bên thắng đã commit nên tìm lại được. Không thấy → lỗi ràng buộc khác.
            var winner = repository.findByIdempotencyKey(key).orElseThrow(() -> e);
            return duplicate(winner.getId(), key);
        }
        MDC.put("jobId", job.getId().toString());
        try {
            log.info("Tạo job {} cho {}", job.getId(), key);
        } finally {
            MDC.remove("jobId");
        }
        return new SubmitResult(SubmitResult.Status.CREATED, job.getId());
    }

    private static SubmitResult duplicate(UUID id, String key) {
        log.info("Kịch bản trùng khóa {} (job {})", key, id);
        return new SubmitResult(SubmitResult.Status.DUPLICATE, id);
    }

    private static String channel(Origin o) {
        return o == null || o.channel() == null || o.channel().isBlank() ? DEFAULT_CHANNEL : o.channel();
    }

    private static String ref(Origin o) {
        if (o == null || (o.chatId() == null && o.messageId() == null)) {
            return null;
        }
        String ref = (o.chatId() == null ? "" : o.chatId()) + "/" + (o.messageId() == null ? "" : o.messageId());
        return ref.length() > 200 ? ref.substring(0, 200) : ref;
    }
}
