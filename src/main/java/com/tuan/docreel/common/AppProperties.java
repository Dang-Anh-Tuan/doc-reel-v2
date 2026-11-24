package com.tuan.docreel.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Cấu hình {@code docreel.*} (SPEC mục 6, 8). Sai / thiếu → app không khởi động. */
@Validated
@ConfigurationProperties("docreel")
public record AppProperties(
        @Valid @NotNull Dirs dirs,
        @Valid @NotNull Tools tools,
        @Valid @NotNull Tts tts,
        @Valid @DefaultValue SelfCheck selfCheck) {

    /** Thư mục làm việc: inbox → work → done / failed / duplicate; requests cho chủ đề tự do (bản 2). */
    public record Dirs(
            @NotNull Path inbox,
            @NotNull Path requests,
            @NotNull Path work,
            @NotNull Path done,
            @NotNull Path failed,
            @NotNull Path duplicate) {

        /** Thư mục mà app phải đọc/ghi được (mục "Thư mục" của self-check). */
        public List<Path> all() {
            return List.of(inbox, requests, work, done, failed, duplicate);
        }
    }

    /** Lệnh ngoài: tên trong PATH hoặc đường dẫn đầy đủ. */
    public record Tools(@NotBlank String ffmpeg, @NotBlank String ffprobe) {}

    public enum TtsProviderName {
        ELEVENLABS,
        EDGE
    }

    public record Tts(
            @NotNull TtsProviderName primary,
            @NotNull TtsProviderName fallback,
            @Valid @NotNull ElevenLabs elevenlabs,
            @Valid @NotNull Edge edge) {}

    /** ElevenLabs. {@code apiKey} rỗng = chưa cấu hình → luôn dùng fallback. */
    public record ElevenLabs(
            String apiKey,
            @NotNull URI baseUrl,
            @NotBlank String model,
            @NotBlank String voiceId,
            @DecimalMin("0.7") @DecimalMax("1.2") double speed) {

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        /** Không bao giờ in key ra log. */
        @Override
        public String toString() {
            return "ElevenLabs[apiKey=" + (hasApiKey() ? "***" : "<empty>") + ", baseUrl=" + baseUrl + ", model="
                    + model + ", voiceId=" + voiceId + ", speed=" + speed + "]";
        }
    }

    public record Edge(
            @NotBlank String command,
            @NotBlank String voice,
            @NotBlank @Pattern(regexp = "^[+-]\\d{1,2}%$") String rate,
            @PositiveOrZero long pauseBetweenCallsMs) {}

    public record SelfCheck(@DefaultValue("PT15M") @NotNull Duration interval) {}
}
