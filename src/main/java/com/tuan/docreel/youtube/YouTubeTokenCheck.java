package com.tuan.docreel.youtube;

import com.tuan.docreel.system.CheckResult;
import com.tuan.docreel.system.SelfCheckItem;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Mục "YouTube" (WARN): đã cấp quyền chưa (refresh token có trên đĩa và giải mã được). Không gọi Google nên không
 * tốn quota; token bị thu hồi chỉ lộ ra khi upload (401 → job FAILED, cấp quyền lại). Đặt trong {@code youtube} vì
 * {@code system} mà phụ thuộc {@code youtube} sẽ tạo vòng với {@code pipeline}.
 */
@Component
@Order(60)
public class YouTubeTokenCheck implements SelfCheckItem {

    static final String NAME = "YouTube";

    private final OAuthTokenStore store;

    public YouTubeTokenCheck(OAuthTokenStore store) {
        this.store = store;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public CheckResult check() {
        if (!store.exists()) {
            return CheckResult.warn(
                    NAME,
                    "chưa có refresh token dùng được (thiếu data/youtube-token.enc, hoặc sai YOUTUBE_TOKEN_ENCRYPTION_KEY, tên cũ YOUTUBE_ENCRYPTION_KEY vẫn được nhận)",
                    "Chạy `java -jar docreel.jar --youtube-auth` để cấp quyền; nếu đã cấp, kiểm YOUTUBE_TOKEN_ENCRYPTION_KEY");
        }
        return CheckResult.ok(NAME, "đã cấp quyền (refresh token có và giải mã được)");
    }
}
