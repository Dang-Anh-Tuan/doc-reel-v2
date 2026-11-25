package com.tuan.docreel.voice;

import com.tuan.docreel.common.AppProperties.TtsProviderName;
import com.tuan.docreel.job.JobRepository;
import com.tuan.docreel.job.JobState;
import com.tuan.docreel.job.VideoJob;
import com.tuan.docreel.pipeline.Step;
import com.tuan.docreel.pipeline.StepContext;
import com.tuan.docreel.pipeline.StepResult;
import java.util.Locale;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bước RENDERED → VOICED: audio từng scene + {@code subtitles.srt} trong {@code work/{jobId}/}. Giọng lấy từ
 * {@code job.tts_settings}, provider đã dùng (retry) từ {@code job.tts_provider}. Lưu {@link VoiceResult#provider()}
 * trả qua {@link StepResult#ttsProvider()}, độ dài audio qua {@link StepResult#audioMs()}; số ký tự đã gửi ghi dần
 * vào {@code StepContext.usage()} (không mất khi lỗi giữa chừng). {@code PipelineWorker} mới là nơi ghi vào job.
 */
@Component
public class VoiceStep implements Step {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final TtsService tts;
    private final JobRepository jobs;

    public VoiceStep(TtsService tts, JobRepository jobs) {
        this.tts = tts;
        this.jobs = jobs;
    }

    @Override
    public boolean supports(JobState state) {
        return state == JobState.RENDERED;
    }

    @Override
    public StepResult execute(StepContext context) {
        MDC.put("jobId", context.jobId().toString());
        try {
            VideoJob job = jobs.findById(context.jobId())
                    .orElseThrow(() -> new IllegalStateException("Không thấy job " + context.jobId()));
            VoiceSettings settings = VoiceSettings.fromJson(job.getTtsSettings(), MAPPER);
            TtsProviderName stored = job.getTtsProvider() == null
                    ? null
                    : TtsProviderName.valueOf(job.getTtsProvider().toUpperCase(Locale.ROOT));
            // Lúc tạo job, tts_provider = provider chính (ý định, chưa phải kết quả) → vẫn phải kiểm quota.
            // Chỉ khi khác provider chính (P5 đã ghi kết quả fallback) mới ghim để không đổi giọng.
            TtsProviderName pinned = stored == settings.primary() ? null : stored;
            VoiceResult result =
                    tts.voice(context.script(), context.workDir(), settings, pinned, context.usage()::addTtsChars);
            return new StepResult(JobState.VOICED, false, result.provider().name(), result.audioMs());
        } finally {
            MDC.remove("jobId");
        }
    }
}
