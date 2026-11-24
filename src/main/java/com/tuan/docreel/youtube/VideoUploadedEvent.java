package com.tuan.docreel.youtube;

import java.util.UUID;

/** Phát sau khi upload xong; P12 (bảng {@code video}) và notifier lắng nghe. */
public record VideoUploadedEvent(
        UUID jobId, String youtubeId, String topicId, String track, String title, String privacyStatus) {}
