package com.tuan.docreel.youtube;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Cấu hình {@code docreel.youtube.*}. Mọi giá trị đều có mặc định để app khởi động được khi chưa cấp quyền;
 * thiếu client / key chỉ lộ ra khi thật sự upload hoặc chạy {@code --youtube-auth}.
 *
 * @param apiBaseUrl gốc của Data API và upload (test trỏ về WireMock)
 * @param encryptionKey passphrase mã hóa refresh token trên đĩa (đặt qua biến môi trường, không ghi vào code)
 */
@ConfigurationProperties("docreel.youtube")
public record YouTubeProperties(
        @DefaultValue("https://www.googleapis.com") String apiBaseUrl,
        @DefaultValue("https://oauth2.googleapis.com/token") String tokenUrl,

        @DefaultValue("https://accounts.google.com/o/oauth2/v2/auth")
        String authUrl,

        String clientId,
        String clientSecret,
        String encryptionKey,
        @DefaultValue("data/youtube-token.enc") Path tokenFile,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("PT2S") Duration retryBackoff) {

    /** Không in secret ra log. */
    @Override
    public String toString() {
        return "YouTubeProperties[apiBaseUrl=" + apiBaseUrl + ", clientId=" + (clientId == null ? "<empty>" : "***")
                + ", clientSecret=***, encryptionKey=***, tokenFile=" + tokenFile + "]";
    }
}
