# NEEDS (P6)

1. `scripts/check.ps1` / doctor (orchestrator): thêm kiểm tra Chromium của Playwright đã cài (`ms-playwright/chromium-*` khớp phiên bản Playwright 1.65.1 = chromium-1255). Lý do: `SlideCapturer` cố ý đặt `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1` để không tự tải firefox/webkit lúc chạy; máy mới phải chạy `playwright install chromium` trước.
2. `pom.xml` (tùy chọn): các test `@Tag("media")` chạy chậm (~200-260 s cho 24 case). Có thể cân nhắc chạy surefire không kèm media khi `-Dit.test` (hiện `-Pmedia` làm TemplateGalleryTest chạy 2 lần: surefire + failsafe).
