# Triển khai DocReel trên Windows (WinSW)

## Chuẩn bị (một lần)
1. Cài Java 21, ffmpeg (`winget install Gyan.FFmpeg`), Docker Desktop (Postgres: `docker compose up -d`), edge-tts (`pip install edge-tts`).
2. Cài Chromium cho đúng bản Playwright của dự án: `npx playwright@1.65.1 install chromium`
   (hoặc `java -cp <classpath> com.microsoft.playwright.CLI install chromium`). Thiếu bước này `--doctor` báo mục Chromium CRITICAL.
3. Tạo `.env` ở gốc repo (không commit): `DB_*`, `ELEVENLABS_API_KEY`, `YOUTUBE_CLIENT_ID`, `YOUTUBE_CLIENT_SECRET`, `YOUTUBE_TOKEN_ENCRYPTION_KEY` (tên cũ `YOUTUBE_ENCRYPTION_KEY` chỉ còn là dự phòng).
4. Tải `WinSW-x64.exe` từ trang release của WinSW.
5. Build: `.\mvnw.cmd package` (jar nằm ở `target\docreel-*.jar`).

## Cấp quyền YouTube (một lần, bằng tay)
`java -jar target\docreel-*.jar --youtube-auth` → mở URL in ra, đồng ý; refresh token được mã hóa vào `data\youtube-token.enc`.
Với service, chạy lệnh này trong thư mục cài đặt (`C:\docreel`) để token nằm cạnh `.env`.

## Cài service
PowerShell quyền Administrator:
```
powershell -NoProfile -ExecutionPolicy Bypass -File deploy\install-service.ps1 -WinSW C:\tools\WinSW-x64.exe
```
Script: copy jar, `.env`, `config\` vào `C:\docreel`, chạy `--doctor` TRƯỚC (có mục CRITICAL → dừng, không đăng ký service),
rồi tạo tác vụ dọn log (chỉ xóa `*.log` > 14 ngày), đăng ký service `docreel` (tự chạy khi khởi động máy, tự restart khi chết) và start. `.env` không bao giờ được in ra; app tự đọc `.env` trong thư mục chạy.
Service chạy bằng LocalSystem (không có PATH / `%LOCALAPPDATA%` của người cài) nên script ghi vào XML đường dẫn tuyệt đối
của `java`, `FFMPEG_PATH`, `FFPROBE_PATH`, `DOCREEL_TTS_EDGE_COMMAND` (edge-tts) và `PLAYWRIGHT_BROWSERS_PATH`
(mặc định `%LOCALAPPDATA%\ms-playwright` của người cài). Cài thêm/đổi công cụ → chạy lại script.
Log mỗi dòng có `[jobId=...]` (biến `LOGGING_PATTERN_LEVEL` trong XML).
Gỡ: thêm `-Uninstall` (giữ nguyên dữ liệu).

## Kiểm tra
- `cd C:\docreel; java "-Dstdout.encoding=UTF-8" -jar docreel.jar --doctor` → bảng từng mục, exit 0 nếu không có CRITICAL (WARN vẫn là đạt, ví dụ chưa cấp YouTube).
  Thiếu `-Dstdout.encoding=UTF-8` thì tiếng Việt hiện thành `?` trên console Windows (chỉ hiển thị, kết quả không đổi).
- `GET http://localhost:8080/actuator/health`, `GET /api/system/check` (khi P12 có).
- Log: `C:\docreel\logs\docreel-service.YYYYMMDD.out.log` (mỗi ngày một file; `.err.log` cho stderr).
- Khởi động lại máy → service tự chạy (delayed auto start) → `--doctor` vẫn xanh.

## Thư mục dữ liệu
`C:\docreel\data-root\{inbox,work,done,failed,duplicate,requests}`: thả JSON vào `inbox\`.
