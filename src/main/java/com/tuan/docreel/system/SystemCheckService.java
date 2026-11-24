package com.tuan.docreel.system;

import org.springframework.stereotype.Service;

/** Cho REST đọc kết quả {@link SystemSelfCheck} (PLAN 2.6) mà không để controller chạm vào các mục kiểm. */
@Service
public class SystemCheckService {

    private final SystemSelfCheck selfCheck;

    public SystemCheckService(SystemSelfCheck selfCheck) {
        this.selfCheck = selfCheck;
    }

    /**
     * @param refresh {@code true} → chạy lại toàn bộ mục ngay (chậm: gọi ffmpeg, Chromium...); {@code false} → kết
     *     quả gần nhất (chạy lúc khởi động + mỗi 15 phút), đủ rẻ để web gọi định kỳ
     */
    public SystemCheckView check(boolean refresh) {
        return SystemCheckView.of(refresh ? selfCheck.runNow() : selfCheck.lastReport());
    }
}
