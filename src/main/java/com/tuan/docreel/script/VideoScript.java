package com.tuan.docreel.script;

import java.util.List;

/**
 * Kịch bản video (SPEC 9.1, cấp video). Chỉ là cấu trúc dữ liệu; kiểm tra ràng buộc là việc của P1
 * ({@code ScriptParser}, {@code ScriptValidator}).
 *
 * @param theme null = mặc định {@code midnight-brass}
 * @param origin kênh gửi (mục 10), không tính vào contentHash
 */
public record VideoScript(
        String topicId,
        String track,
        String title,
        Integer version,
        VideoType videoType,
        String theme,
        List<String> sources,
        List<Scene> scenes,
        Origin origin) {}
