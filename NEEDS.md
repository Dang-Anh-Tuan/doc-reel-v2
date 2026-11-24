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

# NEEDS (P8)

1. `src/main/resources/application.yml` (tùy chọn): thêm
   ```yaml
   docreel:
     lexicon:
       path: config/lexicon.yaml   # mặc định trong code
       case-sensitive: false       # mặc định trong code
   ```
   Lý do: `Lexicon` đọc 2 khóa này bằng `@Value` với giá trị mặc định ngay trong code, nên chưa bắt buộc; chỉ cần nếu muốn chỉnh đường dẫn / phân biệt hoa thường.
2. `config/lexicon.yaml` (P7, phần của "Tôi"): chưa có file → `Lexicon` coi như rỗng (không lỗi). Định dạng phẳng `Thuật ngữ: "Cách đọc"`.
3. `PLAN.md` / `SPEC.md`: SPEC mục 8 ghi lệnh edge-tts `--rate …`; code dùng dạng một tham số `--rate=-10%` vì argparse coi `-10%` là cờ khi tách riêng. Nên sửa mô tả cho khớp.
4. Migration: không cần file mới (bảng `tts_usage` đã có ở `V1__init.sql`).
5. P5: sau khi `VoiceStep` xong, lưu `VoiceResult.provider()` vào `job.tts_provider` (voice không ghi thẳng entity). `VoiceStep` hiện chỉ đọc `job.tts_settings` và `job.tts_provider` (pinned); `execute` chưa trả provider ra ngoài, nên P5 cần mở rộng `StepResult`/`StepContext` hoặc gọi `TtsService.voice` trực tiếp.
6. Mã lỗi: `TtsUnavailableException` (message bắt đầu `TTS_UNAVAILABLE`) = lỗi edge-tts; `TtsTransientException` = mạng/5xx (retry); `TtsException` thường (vd 401 sai key) = không nên retry. P5 `RetryPolicy` cần phân loại theo các lớp này.

# NEEDS (P3)

1. Merge: P3 thêm một dòng vào `job/JobRepository` (`findByState(JobState, Pageable)`); nếu P4/P5 cũng sửa file này thì giữ cả hai. Không cần sửa file dùng chung nào.

# NEEDS (P5)

1. `application.yml` (orchestrator, tùy chọn; đều có mặc định trong code): `docreel.pipeline.poll-interval: PT10S`, `docreel.pipeline.retry-backoff: PT30S,PT2M`. `PipelineScheduler` bị tắt ở profile `test` (`@Profile("!test")`) để IT khác không bị worker nhặt job; profile khác chạy bình thường.
2. SPEC mục 9 / "bảng `video`" (orchestrator): MVP không có bảng `video` (thuộc bản 2, W6). P5 lưu `youtube_id` vào cột `video_job.youtube_id` (đọc `work/{jobId}/youtube-id.txt` lúc hoàn tất bước upload). `VideoUploadedEvent` vẫn được phát cho W6 / notifier, P5 không lắng nghe vì file id đã đủ và nằm trong cùng transaction với đổi state.
3. Giới hạn upload/ngày (quota YouTube ~5-6 video/ngày với `videos.insert` = 1600 unit/10.000): SPEC chưa quy định hành vi khi chạm trần. P5 KHÔNG tự đặt luật: lỗi 403 quotaExceeded của YouTube coi là không retry → job FAILED, retry tay hôm sau. Cần orchestrator quyết định nếu muốn hàng đợi theo ngày (vd cột `not_before` hoặc đếm upload trong ngày).
4. `PLAN.md` mục P5 ghi "ValidationException → FAILED ngay": thực tế phân loại bằng interface `pipeline.RetryAdvice` do từng exception tự khai báo (SlideOverflow, UnknownTheme, Tts* thường, YouTubeApi 4xx = không retry).
5. Retry tự động lưu thời điểm chờ trong RAM (không có cột DB). Restart app giữa lúc chờ backoff → job được thử lại ngay; số lần vẫn đếm đúng từ `job_attempt` (outcome `RETRY`). Nếu cần bền hơn: thêm cột `video_job.retry_not_before` (migration V9xx, orchestrator duyệt).
6. `JobApiService.retry` (P3) chưa phát `JobStateChangedEvent` (FAILED→RECEIVED); chỉ worker phát event. Thêm nếu muốn notifier thấy cả lần retry tay.
7. Job FAILED: file `work/{jobId}/input.json` ở lại `work/` (chỉ UPLOADED mới chuyển sang `done/{jobId}-input.json`).
