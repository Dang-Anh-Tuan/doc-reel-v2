package com.tuan.docreel.voice;

import static org.assertj.core.api.Assertions.assertThat;

import com.tuan.docreel.common.AppProperties.TtsProviderName;
import com.tuan.docreel.job.JobRepository;
import com.tuan.docreel.job.JobService;
import com.tuan.docreel.job.JobState;
import com.tuan.docreel.job.SubmitResult;
import com.tuan.docreel.pipeline.StepContext;
import com.tuan.docreel.script.VideoScript;
import com.tuan.docreel.settings.TtsSettings;
import com.tuan.docreel.settings.TtsSettingsProvider;
import com.tuan.docreel.settings.YamlTtsSettingsProvider;
import com.tuan.docreel.support.TestAppProperties;
import com.tuan.docreel.support.TestcontainersConfiguration;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Đổi cấu hình yml sau khi submit job → voice vẫn đọc giọng cũ từ job.tts_settings. */
@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, VoiceStepIT.MutableTts.class})
class VoiceStepIT {

    static final AtomicReference<TtsSettings> CURRENT = new AtomicReference<>();

    @TestConfiguration(proxyBeanMethods = false)
    static class MutableTts {
        @Bean
        @Primary
        TtsSettingsProvider mutableTtsSettings() {
            return CURRENT::get;
        }
    }

    @Autowired
    JobService jobs;

    @Autowired
    JobRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @TempDir
    Path root;

    private static TtsSettings snapshot(Path root, String voiceId, String edgeVoice) {
        var base = TestAppProperties.withDirsUnder(root);
        var props = new com.tuan.docreel.common.AppProperties(
                base.dirs(),
                base.tools(),
                new com.tuan.docreel.common.AppProperties.Tts(
                        TtsProviderName.ELEVENLABS,
                        TtsProviderName.EDGE,
                        new com.tuan.docreel.common.AppProperties.ElevenLabs(
                                "", URI.create("http://localhost:1"), "m", voiceId, 1.0),
                        new com.tuan.docreel.common.AppProperties.Edge("edge-tts", edgeVoice, "+0%", 0)),
                base.selfCheck());
        return new YamlTtsSettingsProvider(props).current();
    }

    @BeforeEach
    void clean() {
        jdbc.update("delete from job_attempt");
        jdbc.update("delete from video_job");
    }

    @Test
    void usesVoiceSnapshotTakenWhenJobWasCreated() throws Exception {
        CURRENT.set(snapshot(root, "voice-OLD", "vi-VN-HoaiMyNeural"));
        VideoScript script = VoiceFixtures.script(VoiceFixtures.title("s01", "Một câu."));
        SubmitResult submitted = jobs.submit(script, null);

        CURRENT.set(snapshot(root, "voice-NEW", "vi-VN-NamMinhNeural")); // sửa yml sau khi submit

        List<String> seenVoices = new ArrayList<>();
        TtsServiceTest.StubProvider eleven =
                new TtsServiceTest.StubProvider(TtsProviderName.ELEVENLABS, true, 1_000_000) {
                    @Override
                    public byte[] synthesize(String s, String p, String n, VoiceSettings settings) {
                        seenVoices.add(settings.elevenVoiceId());
                        return super.synthesize(s, p, n, settings);
                    }
                };
        TtsServiceTest.StubProvider edge = new TtsServiceTest.StubProvider(TtsProviderName.EDGE, true, Long.MAX_VALUE);
        TtsService tts = new TtsService(
                List.of(eleven, edge),
                new Lexicon(root.resolve("none.yaml"), false),
                new TtsCache(TestAppProperties.withDirsUnder(root)),
                new FakeAudioTool(),
                (provider, chars) -> {});

        Path work = Files.createDirectories(root.resolve("work/job"));
        var usage = new com.tuan.docreel.pipeline.UsageMeter();
        var result = new VoiceStep(tts, repository)
                .execute(new StepContext(submitted.jobId(), JobState.RENDERED, script, work, 1, usage));

        assertThat(seenVoices).containsExactly("voice-OLD");
        assertThat(result.ttsProvider()).isEqualTo("ELEVENLABS"); // P5 ghi vào job.tts_provider
        assertThat(usage.ttsChars()).isEqualTo("Một câu.".length()); // P13b: worker ghi vào job.tts_chars
        assertThat(result.audioMs()).isPositive();
    }
}
