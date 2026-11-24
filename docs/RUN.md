# Chạy DocReel

## 1. Cài đặt một lệnh (máy Windows mới)
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\setup.ps1
```
Script chạy lại nhiều lần vẫn an toàn, bước nào có rồi thì bỏ qua:
Java 21, ffmpeg, Node, Python + `edge-tts`, Chromium cho Playwright 1.65.1, Docker Desktop → tạo `.env` từ `.env.example` nếu chưa có
→ `docker compose up -d` (Postgres, cổng 55432) → build jar → `--doctor`.

Sau lần đầu:
1. Điền `.env` (ElevenLabs key, YouTube OAuth client, `YOUTUBE_TOKEN_ENCRYPTION_KEY`). Không commit file này.
2. Cấp quyền YouTube (một lần): `java -jar target\docreel-0.0.1-SNAPSHOT.jar --youtube-auth`, mở URL in trong log,
   đồng ý → token mã hóa ở `data\youtube-token.enc`. Xong thì Ctrl+C.
3. **Mở lại terminal / VS Code** nếu script vừa cài Java hoặc ffmpeg (PATH mới).

## 2. Chạy server (dev)
```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run.ps1          # dùng jar có sẵn
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\run.ps1 -Build   # build lại trước
```
- Server: `http://localhost:8080` · health: `/actuator/health` · web quản trị: `/admin` (từ P12).
- Thả file JSON kịch bản vào `inbox\` (hoặc `POST /api/jobs`). Kết quả: `work\{jobId}\` khi đang chạy,
  `done\` khi upload xong, `failed\` / `duplicate\` khi lỗi / trùng. Video lên YouTube ở chế độ **private**.
- Dừng: Ctrl+C. Postgres vẫn chạy; tắt bằng `docker compose down` (dữ liệu giữ trong volume).
- Dev web: `cd web; npm run dev` (Vite, proxy `/api` sang 8080).

## 3. Kiểm tra
```powershell
scripts\check.ps1                 # spotless + verify + web
scripts\check.ps1 -Media -E2E     # thêm render Chromium, ffmpeg, end-to-end (~4 phút)
java -jar target\docreel-0.0.1-SNAPSHOT.jar --doctor   # tự kiểm tra môi trường
```

## 4. Triển khai
**Hiện tại — laptop/PC Windows chạy như service** (chi tiết: `docs/DEPLOY.md`, có sau P11):
`scripts\setup.ps1` → PowerShell Administrator:
`deploy\install-service.ps1 -WinSW C:\tools\WinSW-x64.exe` → service `docreel` tự chạy khi bật máy,
log theo ngày ở `C:\docreel\logs` (giữ 14 ngày), dữ liệu ở `C:\docreel\data-root`.

**Sau này — VPS Linux bằng Docker** (chưa làm, dự kiến cùng P18):
- `Dockerfile` một image: JRE 21 + ffmpeg + `pip install edge-tts` + `playwright install --with-deps chromium` + jar.
- `compose.yaml` thêm service `app` (profile `prod`) cạnh `postgres`; mount volume cho `data/` (token YouTube) và
  thư mục `inbox/work/done/...`; `.env` truyền qua `env_file`.
- Lệnh duy nhất trên VPS: `docker compose --profile prod up -d --build`.
- Cấp quyền YouTube: chạy `--youtube-auth` trên máy cá nhân rồi copy `data/youtube-token.enc` lên VPS
  (cùng `YOUTUBE_TOKEN_ENCRYPTION_KEY`), vì VPS không có trình duyệt.
