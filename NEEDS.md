# NEEDS (P6)

1. `scripts/check.ps1` / doctor (orchestrator): thêm kiểm tra Chromium của Playwright đã cài (`ms-playwright/chromium-*` khớp phiên bản Playwright 1.65.1 = chromium-1255). Lý do: `SlideCapturer` cố ý đặt `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` để không tự tải firefox/webkit lúc chạy; máy mới phải chạy `playwright install chromium` trước.
2. `pom.xml` (tùy chọn): các test `@Tag("media")` chạy chậm (~200-260 s cho 24 case). Có thể cân nhắc chạy surefire không kèm media khi `-Dit.test` (hiện `-Pmedia` làm TemplateGalleryTest chạy 2 lần: surefire + failsafe).

# NEEDS (P10)

1. `application.yml` (orchestrator): thêm khối cấu hình (đều có mặc định nên app vẫn chạy khi thiếu):
   `docreel.youtube.client-id`, `client-secret`, `encryption-key` (cả 3 lấy từ biến môi trường, KHÔNG ghi giá trị vào file), `token-file` (mặc định `data/youtube-token.enc`), `max-attempts` (3), `retry-backoff` (PT2S). Lý do: cấp quyền OAuth và mã hóa refresh token.
2. `.gitignore` / `src/.env.example` (orchestrator): thêm `data/youtube-token.enc` vào ignore và 3 tên biến môi trường YouTube vào `.env.example`.
3. `SystemSelfCheck` (P11/orchestrator): thêm mục "token YouTube" dùng `OAuthTokenStore.exists()`.
4. Ghép pipeline (P5/P2): `YouTubeUploadStep` mong MP4 ở `work/{jobId}/video.mp4` (hằng `VIDEO_FILE`) và ghi id vào `work/{jobId}/youtube-id.txt`; P5 lưu `youtube_id` vào `video_job` và lắng nghe `VideoUploadedEvent` (jobId, youtubeId, topicId, track, title, privacyStatus).

# NEEDS (P4)

1. `application.yml` (orchestrator, tùy chọn): thêm `docreel.inbox.poll-interval: PT10S` (hiện đã có mặc định trong `@Scheduled`, test đặt PT1H để tránh tranh chấp).
2. Ghép pipeline (P5): file hợp lệ nằm ở `work/{jobId}/input.json`; khi job UPLOADED P5 cần chuyển file này sang `done/` (đặt tên `{jobId}-input.json` hoặc tên gốc). P4 không làm bước này vì chưa có vòng đời job.
