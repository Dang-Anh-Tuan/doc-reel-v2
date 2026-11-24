package com.tuan.docreel.voice;

import com.tuan.docreel.job.JobState;
import com.tuan.docreel.pipeline.Step;
import com.tuan.docreel.pipeline.StepContext;
import com.tuan.docreel.pipeline.StepResult;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/** Bước RENDERED → VOICED: audio từng scene + {@code subtitles.srt} trong {@code work/{jobId}/}. */
@Component
public class VoiceStep implements Step {

    private final TtsService tts;
    private final TtsSettingsProvider settings;

    public VoiceStep(TtsService tts, TtsSettingsProvider settings) {
        this.tts = tts;
        this.settings = settings;
    }

    @Override
    public boolean supports(JobState state) {
        return state == JobState.RENDERED;
    }

    @Override
    public StepResult execute(StepContext context) {
        MDC.put("jobId", context.jobId().toString());
        try {
            // pinned = null: job.tts_provider chưa có entity (P2/P3); audio đã cache nên retry không tốn thêm quota
            tts.voice(context.script(), context.workDir(), settings.forJob(context.jobId()), null);
            return new StepResult(JobState.VOICED, false);
        } finally {
            MDC.remove("jobId");
        }
    }
}
