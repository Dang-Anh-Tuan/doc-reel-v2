# Triển khai DocReel trên Windows (WinSW)

## Chuẩn bị (một lần)
1. Cài Java 21, ffmpeg (`winget install Gyan.FFmpeg`), Docker Desktop (Postgres: `docker compose up -d`), edge-tts (`pip install edge-tts`).
2. Cài Chromium cho đúng bản Playwright của dự án: `npx playwright@1.65.1 install chromium`
   (hoặc `java -cp <classpath> com.microsoft.playwright.CLI install chromium`). Thiếu bước này `--doctor` báo mục Chromium CRITICAL.
3. Tạo `.env` ở gốc repo (không commit): `DB_*`, `ELEVENLABS_API_KEY`, `YOUTUBE_CLIENT_ID`, `YOUTUBE_CLIENT_SECRET`, `YOUTUBE_ENCRYPTION_KEY`.
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
Script: copy jar, `.env`, `config\` vào `C:\docreel`, đăng ký service `docreel` (tự chạy khi khởi động máy, tự restart khi chết),
tạo tác vụ dọn log (giữ 14 ngày), chạy `--doctor`, rồi mới start. `.env` không bao giờ được in ra; app tự đọc `.env` trong thư mục chạy.
Gỡ: thêm `-Uninstall` (giữ nguyên dữ liệu).

## Kiểm tra
- `cd C:\docreel; java -jar docreel.jar --doctor` → bảng từng mục, exit 0 nếu không có CRITICAL (WARN vẫn là đạt, ví dụ chưa cấp YouTube).
- `GET http://localhost:8080/actuator/health`, `GET /api/system/check` (khi P12 có).
- Log: `C:\docreel\logs\docreel-service.YYYYMMDD.out.log` (mỗi ngày một file; `.err.log` cho stderr).
- Khởi động lại máy → service tự chạy (delayed auto start) → `--doctor` vẫn xanh.

## Thư mục dữ liệu
`C:\docreel\data-root\{inbox,work,done,failed,duplicate,requests}`: thả JSON vào `inbox\`.
