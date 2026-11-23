# Prompt: tạo 10 template slide cho DocReel (story 7)

> Dán phần bên dưới vào Claude Code ở thư mục gốc repo.
> Prompt này bám theo `SPEC.md` mục 7 (field, giới hạn, đường dẫn) và dùng các file mẫu trong `samples/`.

---

Đọc `SPEC.md` mục 7 và mục 9 (Input JSON) trước. Hãy làm **story 7: tạo 10 template scene** cho video dọc 1080×1920. Template render bằng **Thymeleaf**, sau đó Playwright chụp thành PNG.

## 1. Phong cách
- Lấy cảm hứng từ các video infographic lập trình kiểu "AlgoInsight": bố cục thẻ (card) rõ ràng, icon nét mảnh, từ khóa của tiêu đề nằm trên một khối màu (pill), nhãn code dạng viên nhỏ font mono, chữ ít nhưng to. Chỉ **lấy cảm hứng**: không dùng tên, logo hay bố cục y hệt của kênh khác, không vẽ logo framework.
- Thêm chất **vintage**: nền xanh đậm (không đen tuyệt đối), chữ màu kem (không trắng tinh), màu nhấn tông đất đã giảm độ đậm; viền mảnh 1–2px, bóng đổ mềm; glow chỉ dùng rất nhẹ cho phần tử đang nhấn mạnh; lớp nhiễu giấy (grain SVG) phủ toàn màn hình, opacity 0.05; bo góc 8–14px.
- Font (đặt local trong `static/vendor/fonts/`, không gọi CDN):
  - Nội dung: **Be Vietnam Pro** (Medium, Bold)
  - Code: **JetBrains Mono**
  - Tiêu đề lớn (`title`, `definition`): **Fraunces** (serif, có tiếng Việt). Fallback: Be Vietnam Pro.

## 2. Màu: biến CSS + theme
Mọi màu trong `base.css` chỉ lấy từ biến CSS. MVP dùng **1 theme mặc định** (`midnight-brass`). 9 theme còn lại chỉ là file khai báo biến, tạo luôn để sau này đổi qua trường `"theme"` trong JSON.

Biến bắt buộc: `--bg --card --primary --secondary --accent --text --muted`, cộng màu theo `kind`: `--bad --good --warn --info --tip` (tông dịu: đỏ đất, xanh xô thơm, vàng mù tạt, xanh dương bạc, tím phấn).

| file `static/css/themes/` | bg | card | primary | secondary | accent | text | muted |
|---|---|---|---|---|---|---|---|
| midnight-brass.css (mặc định) | #1B2433 | #243044 | #C9A66B | #B86F52 | #8FA68E | #EDE6D8 | #94A0B2 |
| petrol-sage.css | #172A2E | #20373C | #8FB8A8 | #D4A373 | #C97C5D | #E8E4D8 | #8FA3A3 |
| indigo-dust.css | #1E2140 | #282C52 | #A99BC9 | #D9A0A0 | #E2C07A | #ECE8F2 | #9A9BB8 |
| navy-terracotta.css | #1A2535 | #243247 | #C47A57 | #6FA8A0 | #E0C27A | #F0E8DC | #94A2B5 |
| slate-mustard.css | #222B36 | #2C3744 | #C9A94E | #8EA8B5 | #B86F52 | #ECE7DA | #98A3AF |
| deep-ocean-coral.css | #14253A | #1C3150 | #E0907A | #7FB2C9 | #D8C08E | #EAF0F4 | #8DA2B8 |
| blueprint-cream.css | #1A3150 | #22406A | #EAD9B0 | #7FB2D9 | #D98E6A | #F2EEE4 | #A0B4CC |
| storm-rose.css | #232A3D | #2E364D | #D49A9A | #A3B18A | #D8B26A | #EFE9E6 | #9CA3B6 |
| teal-navy-copper.css | #162C35 | #1F3B47 | #C88B6E | #6FA8A0 | #E2C07A | #ECE6DA | #8EA5AC |
| prussian-olive.css | #1C2A3A | #26374B | #A3B18A | #C9A66B | #C6735B | #EEE8DA | #93A2B3 |

Chữ nằm trên nền `--primary` dùng `--bg` (vì primary đều sáng).

## 3. Khung chung (`templates/layout.html`)
- `html, body` cố định 1080×1920, `overflow: hidden`.
- **Vùng an toàn** đúng SPEC: nội dung trong `.stage` từ y = 220 đến y = 1500, lề trái/phải 72px. 420px dưới cùng để trống cho phụ đề và UI của Shorts/Reels/TikTok.
- Phần chung: thanh tiến độ `scene i/n` (vạch ngang, i vạch đầu tô `--primary`), chip `track`, mã `topicId` mờ ở góc trên.
- Nền: `--bg` + lưới mảnh 72px opacity rất thấp + 1 vầng sáng rất mờ màu `--primary` + lớp grain.
- Tiêu đề chung (mọi type trừ `definition`, `quiz`): `accent` trong pill `--primary`, `titleRest` màu `--text`, tối đa 2 dòng.

## 4. Hợp đồng với server (bắt buộc)
- Mỗi file là **Thymeleaf natural template**: mở thẳng file `.html` trên trình duyệt vẫn thấy nội dung mẫu đẹp. Chỉ dùng `th:text`, `th:each`, `th:if`, `th:classappend`, `th:attr`. **Không dùng `th:utext`.**
- Model: `video` (topicId, track, title, theme), `scene` (object scene đúng như trong JSON), `index`, `total`, `frame` (chỉ `quiz`: 1 = câu hỏi, 2 = đáp án).
- `static/js/slide.js` dùng chung:
  1. Tô màu code bằng highlight.js local (`static/vendor/highlight/`), highlight từng dòng; dòng thuộc `highlight[]` (đánh số từ 1) có nền và vạch trái `--primary`.
  2. Chờ `document.fonts.ready` → phần tử có `data-fit` bị tràn (`scrollHeight > clientHeight + 2` hoặc `scrollWidth > clientWidth + 2`) và `.stage` bị tràn → ghi vào `body[data-problems]`, cách nhau bởi dấu phẩy.
  3. Đặt `body[data-ready="1"]`.
- `quiz` có `body[data-frames="2"]`; Playwright mở `?frame=1` và `?frame=2`.
- Bản này **chưa có animation**, nhưng gắn sẵn class `.anim-in` + `style="--order:n"` cho từng khối theo thứ tự xuất hiện, để bản sau thêm animation.
- Cỡ chữ phải được thiết kế để **nội dung dài đúng bằng giới hạn tối đa vẫn vừa khung** (kiểm bằng `samples/full/test-max-limits.json`).

## 5. 10 template (`templates/scenes/{type}.html`)

| type | Field (đúng tên trong JSON) | Giới hạn | Thiết kế |
|---|---|---|---|
| `title` | `chip`, `accent`, `titleRest`, `hook` | tiêu đề ≤ 28, hook ≤ 60 | Chip viền mảnh ở trên; tiêu đề lớn nhất bộ (Fraunces); hook màu `--muted`; 1 hình trang trí trừu tượng bằng nét mảnh (vòng tròn, đường cong) |
| `points` | `accent`, `titleRest`, `points[] {heading, detail}` | 1–3 ý; heading ≤ 24, detail ≤ 70 | Thẻ có vạch trái `--secondary`, ô số thứ tự vuông, heading đậm, detail `--muted` |
| `code` | `accent`, `titleRest`, `file`, `lang`, `code`, `highlight[]`, `note`, `noteKind` (info/warn/tip) | ≤ 8 dòng × 40 ký tự | Khung code có tab tên file đè lên viền trên + 3 chấm cửa sổ; hộp note có vạch trái màu theo `noteKind` và nhãn nhỏ "Ghi nhớ" / "Lưu ý" / "Mẹo" |
| `codeCompare` | `accent`, `titleRest`, `lang`, `bad {label, code}`, `good {label, code}`, `note` | mỗi khối ≤ 5 dòng × 40 | 2 khung code xếp dọc; trên có nhãn ✗ + viền `--bad`, dưới có nhãn ✓ + viền `--good`; giữa là mũi tên xuống nét mảnh; note cuối |
| `compare` | `accent`, `titleRest`, `left/right {heading, kind: neutral/bad/good, items[]}`, `note?` | ≤ 3 item/cột, item ≤ 30 | 2 cột bằng nhau, chấm màu theo `kind` ở đầu cột, "vs" trong vòng tròn nhỏ ở giữa |
| `definition` | `term`, `expansion`, `definition`, `example` | term ≤ 20, định nghĩa ≤ 120, ví dụ ≤ 60 | `term` rất lớn (Fraunces), `expansion` font mono màu `--accent`; định nghĩa trong thẻ; ví dụ trong khối code nhỏ có nhãn "Ví dụ" |
| `steps` | `accent`, `titleRest`, `steps[]` | 3–5 bước, mỗi bước ≤ 40 | Bước xếp dọc, vòng tròn số thứ tự nối bằng đường kẻ dọc có mũi tên; bước cuối viền `--primary` |
| `table` | `accent`, `titleRest`, `columns[]`, `rows[][]` | 2–3 cột × ≤ 4 hàng, ô ≤ 18 | Hàng tiêu đề nền đậm hơn, chữ `--primary`; hàng xen kẽ nền nhẹ; cột đầu đậm; tự co theo 2 hoặc 3 cột |
| `quiz` | `question`, `options[]`, `answer` (index từ 0), `explain` | hỏi ≤ 80, lựa chọn ≤ 30, giải thích ≤ 80 | Frame 1: nhãn "Câu hỏi phỏng vấn", câu hỏi lớn, lựa chọn A–D dạng thẻ. Frame 2: cùng bố cục, đáp án đúng viền + nền `--good` kèm ✓, các lựa chọn khác opacity 0.45, hộp giải thích hiện bên dưới |
| `summary` | `accent`, `titleRest`, `bullets[]`, `outro` | ≤ 4 bullet, mỗi bullet ≤ 40 | Thẻ checklist, ô ✓ màu `--accent`; outro ở dưới cùng màu `--muted` |

## 6. Mẫu dữ liệu có sẵn (đừng sửa)
- `samples/scenes/{type}.ok.json` và `{type}.overflow.json`: dạng `{ "video": {...}, "scene": {...} }`, mỗi template một cặp. Bản `overflow` cố tình vượt giới hạn: phải bị `slide.js` báo trong `data-problems`.
- `samples/full/sample-all-scenes.json`: bài mẫu R-08 đủ 10 loại scene.
- `samples/full/test-max-limits.json`: mọi trường **đúng bằng giới hạn tối đa**, có tiếng Việt nhiều dấu và ký tự `<>&"'`. Mọi slide của file này phải **không** có lỗi.

## 7. Sản phẩm và kiểm tra
```
src/main/resources/
├── templates/layout.html
├── templates/scenes/{type}.html        10 file
├── static/css/base.css
├── static/css/themes/*.css             10 file
├── static/js/slide.js
└── static/vendor/                      fonts/, highlight/ (kèm LICENSE)
```
- `GET /dev/preview/{type}?sample=ok|overflow&theme=...&frame=...` (chỉ bật ở profile `dev`) đọc file trong `samples/scenes/` và render template.
- Test `TemplateGalleryTest`: render 10 mẫu `ok` → `build/gallery/*.png`, khẳng định `data-problems` rỗng; render 10 mẫu `overflow` → khẳng định `data-problems` **không** rỗng.
- Ghép `build/gallery/contact-sheet.png` (10 slide theme mặc định) để tôi duyệt bằng mắt.
- Không cài thêm package khi chưa hỏi tôi. Không commit khi chưa được duyệt.
