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

# NEEDS (P11)

1. `scripts/check.ps1` (orchestrator): cờ `-E2E` hiện chạy `./mvnw verify -Pe2e` — đã đủ để chạy `EndToEndIT` (profile e2e bỏ loại trừ tag `media,e2e`). Đề xuất: (a) khi `-E2E` thì chạy thêm `java -jar target\docreel-*.jar --doctor` sau verify (cần `package` trước); (b) in cảnh báo rõ nếu Chromium chưa cài (`ChromiumCheck` trong doctor đã báo; test media/e2e sẽ lỗi `Executable doesn't exist`). `-E2E` chạy ~4 phút (render 24 slide + ghép video thật).
2. Chromium Playwright (máy mới / máy này): Playwright 1.65.1 cần `chromium_headless_shell-1255`; máy có sẵn chỉ `chromium-1248` → doctor/ChromiumCheck báo CRITICAL. Cài: `java -cp <classpath> com.microsoft.playwright.CLI install chromium` (lệnh `./mvnw dependency:build-classpath` lấy classpath) hoặc `npx playwright@1.65.1 install chromium`. Máy dev hiện tại đã có `chromium-1255` + `chromium_headless_shell-1255`. Đề xuất ghi vào README / docs/DEPLOY.md (P11 đã ghi trong DEPLOY.md).
3. `application.yml` (orchestrator, tùy chọn): prod nên đặt `logging.file.name` hoặc để WinSW ghi stdout theo ngày (hiện chọn WinSW `roll-by-time`, không cần sửa yml). Nếu muốn log app ra file riêng có xoay vòng: `logging.logback.rollingpolicy.max-history: 14` + `logging.file.name: ${DOCREEL_HOME}/logs/docreel.log`.
4. `SystemSelfCheck` mục mới đã thêm (không sửa file dùng chung): `FfmpegCheck`, `DiskSpaceCheck` (package `system`), `ChromiumCheck` (`render`), `YouTubeTokenCheck` (`youtube`). Đặt ở `render`/`youtube` vì `system -> youtube -> pipeline -> system` sẽ tạo vòng ArchUnit. Chưa có mục: edge-tts / quota ElevenLabs (P8), render thử 1 template mẫu (P6) — để phase đó hoặc orchestrator thêm bean `SelfCheckItem`.
5. `deploy/` mới (không thuộc danh sách file dùng chung): `docreel-service.xml`, `install-service.ps1`. WinSW không tự xóa log cũ → script đăng ký tác vụ Windows `DocReel-LogCleanup` (03:00 hằng ngày, xóa log > 14 ngày).
6. `application.yml` (orchestrator, NÊN làm cho tiêu chí "log theo jobId"): pattern mặc định của Spring Boot KHÔNG in MDC, nên `jobId` chỉ có trong sự kiện log (EndToEndIT kiểm bằng ListAppender) chứ không hiện trên dòng log. Đề xuất:
   ```yaml
   logging:
     pattern:
       level: "%5p [jobId=%X{jobId:-}]"
   ```
   Hiện P11 chỉ đặt tạm qua biến `LOGGING_PATTERN_LEVEL` trong `deploy/docreel-service.xml` (service). Khi yml có rồi thì xóa dòng env đó.
7. `scripts/check.ps1` (orchestrator, tùy chọn): `-Media -E2E` chạy test media 2 lần (`verify -Pmedia` rồi `verify -Pe2e` vốn đã gồm media). Khi có `-E2E` có thể bỏ bước `-Pmedia` để tiết kiệm ~4 phút.
9. Môi trường máy mới (orchestrator / người): `web: test` trong `check.ps1` lỗi "Failed to start forks worker … undici/lib/interceptor/decompress.js: Cannot read properties of undefined (reading 'bind')" vì máy có Node v20.19.5 còn `undici@8.13.1` (qua jsdom 31) cần Node >= 22.19. Không liên quan P11 (không đổi `web/`). Sửa: cài Node 22 LTS (>= 22.19) hoặc khai báo `engines.node` trong `web/package.json` + ghi vào `scripts/setup`.
10. `--doctor` trên console Windows: thêm `-Dstdout.encoding=UTF-8` (đã ghi trong DEPLOY.md và XML service) để tiếng Việt không thành `?`. `docs/RUN.md` / `scripts/run*` (orchestrator) nên dùng cùng cờ.
11. Sửa ngoài phạm vi package (báo orchestrator): `pipeline/PipelineWorker.java` khôi phục MDC `jobId` sau `step.execute` (các Step `RenderStep`/`VoiceStep`/`AssembleStep`/`YouTubeUploadStep` tự `MDC.remove` trong `finally` làm log lỗi của worker mất jobId). Có test `PipelineWorkerIT.errorLogAfterStepKeepsJobIdEvenIfStepClearsMdc`. Gọn hơn về lâu dài: Step không tự quản MDC (worker đã đặt).
8. Service chạy LocalSystem: `install-service.ps1` ghi đường dẫn tuyệt đối `java`, `FFMPEG_PATH`, `FFPROBE_PATH`, `DOCREEL_TTS_EDGE_COMMAND`, `PLAYWRIGHT_BROWSERS_PATH` vào XML lúc cài (công cụ cài theo user không nằm trong PATH của SYSTEM). Không cần sửa file dùng chung.

# NEEDS (P12)

1. `pom.xml` (orchestrator): thêm `frontend-maven-plugin` để `mvn package` tự build web vào `static/admin` (W1). Bỏ qua bằng `-Dskip.web`.
   Phase là `generate-resources` (KHÔNG phải `prepare-package`): Vite ghi vào `src/main/resources/static/admin`, phải xong trước `process-resources` thì jar mới có web.
   Plugin tự tải Node riêng vào `target/node` (đúng bản SPEC 7.1 yêu cầu, không phụ thuộc Node trên PATH).
   ```xml
   <!-- trong <properties> -->
   <frontend-plugin.version>1.15.1</frontend-plugin.version>
   <web.node.version>v22.22.0</web.node.version>
   <skip.web>false</skip.web>

   <!-- trong <build><plugins> -->
   <plugin>
       <groupId>com.github.eirslett</groupId>
       <artifactId>frontend-maven-plugin</artifactId>
       <version>${frontend-plugin.version}</version>
       <configuration>
           <workingDirectory>web</workingDirectory>
           <installDirectory>target/node</installDirectory>
           <nodeVersion>${web.node.version}</nodeVersion>
           <skip>${skip.web}</skip>
       </configuration>
       <executions>
           <execution>
               <id>install-node-and-npm</id>
               <phase>generate-resources</phase>
               <goals>
                   <goal>install-node-and-npm</goal>
               </goals>
           </execution>
           <execution>
               <id>npm-ci</id>
               <phase>generate-resources</phase>
               <goals>
                   <goal>npm</goal>
               </goals>
               <configuration>
                   <arguments>ci</arguments>
               </configuration>
           </execution>
           <execution>
               <id>npm-build</id>
               <phase>generate-resources</phase>
               <goals>
                   <goal>npm</goal>
               </goals>
               <configuration>
                   <arguments>run build</arguments>
               </configuration>
           </execution>
       </executions>
   </plugin>
   ```
   `AdminSpaIT` KHÔNG cần plugin: dùng fixture `src/test/resources/static/admin/index.html` (test-classes đứng trước classes trên classpath).
2. `scripts/check.ps1` (orchestrator): sau khi thêm plugin, bước 2 nên chạy `mvnw verify -Dskip.web` vì bước 3 đã lint/test/build web — tránh `npm ci` + build 2 lần.
3. Môi trường / `scripts/check.ps1`: Node mặc định của nvm trên máy là **v20.19.5**; với Node 20, `npm test` (vitest 5 + jsdom 31 + undici) **hỏng ngay khi khởi động worker** (`undici/lib/interceptor/decompress.js: Cannot read properties of undefined (reading 'bind')`), kể cả test cũ. SPEC 7.1 yêu cầu Node ≥ 22.22 (máy có sẵn `nvm` v22.22.0). Đề xuất: `nvm use 22.22.0`, và/hoặc check.ps1 kiểm `node -v` ≥ 22.22 trước bước web, báo rõ cách sửa. P12 chạy kiểm tra bằng cách thêm `%LOCALAPPDATA%\nvm\v22.22.0` vào đầu PATH của phiên, không đổi nvm toàn máy.
4. `PLAN.md`/`SPEC.md` (tùy chọn): ghi `GET /api/system/check?refresh=true` (chạy lại các mục ngay); mặc định trả kết quả gần nhất (rẻ, web gọi mỗi 60 s).
