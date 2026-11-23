# DocReel web

React 19 + TypeScript + Vite 8. Hiện có **Template preview** (dev); bản 2 thêm **trang quản trị job** (gọi REST của Spring Boot).

## Lệnh

```bash
cd web
npm install          # lần đầu
npm run dev          # http://localhost:5173  (preview template, tự tải lại khi sửa template/CSS/JS/mẫu)
npm test             # Vitest: bộ render Thymeleaf + 10 template thật với mẫu ok
npm run lint         # ESLint (typescript-eslint, react-hooks)
npm run typecheck    # tsc -b
npm run build        # → ../src/main/resources/static/admin (Spring phục vụ ở /admin/)
```

Cần Node ≥ 22.22.

## Cấu trúc

```
web/
├── dev/docreelDevServer.ts      plugin Vite (chỉ dev): phục vụ /css /js /vendor /templates /samples
│                                giống Spring + /__dev/samples + báo HMR khi file đổi
├── src/
│   ├── app/                     router, AppShell (thanh điều hướng), PageHeader
│   ├── components/              UI dùng chung (Button, Select, Segmented, Toggle, Badge, Panel, Message), icon
│   ├── domain/script.ts         kiểu kịch bản JSON (SPEC 9.1) — dùng chung preview + quản trị
│   ├── lib/thymeleaf/           render template Thymeleaf trong trình duyệt (+ test)
│   ├── features/preview/        trang Template preview
│   ├── features/jobs/           trang Jobs (bản 2)
│   └── styles/global.css        token giao diện
└── vite.config.ts               alias @ → src, proxy /api → :8080, build → static/admin
```

## Template preview

- Render **chính file Thymeleaf** trong `src/main/resources/templates/` bằng `lib/thymeleaf` — chỉ hiểu đúng
  tập `th:*` được phép (SPEC mục 7); thuộc tính khác như `th:utext` → cảnh báo.
- Trạng thái nằm trên URL (`?source=…&theme=…&view=single&slide=…&safe=1`) → reload/gửi link giữ nguyên.
- Badge OK / `tràn: …` lấy từ `body[data-problems]` do `slide.js` ghi.
- Chỉ chạy ở `npm run dev`; bản build hiện thông báo.
