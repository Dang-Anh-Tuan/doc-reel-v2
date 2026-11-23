# DocReel — luật cho mọi agent

## Nguồn sự thật
- `SPEC.md`: yêu cầu, schema JSON, giới hạn, API. `PLAN.md`: phase, test bắt buộc, lệnh tự kiểm tra.
- `ORCHESTRATOR.md`: thứ tự đợt chạy song song + các cổng cần người duyệt. `docs/STATUS.md`: bảng tiến độ.
- Mâu thuẫn giữa các file → dừng và hỏi, không tự đoán.

## Môi trường
- Windows + PowerShell. Java 21, Maven wrapper (`./mvnw`), Spring Boot 4.x, PostgreSQL qua Docker Compose.
- Lệnh kiểm tra toàn bộ: `scripts/check.ps1` (thêm `-Media`, `-E2E` khi phase yêu cầu).

## Cách làm 1 phase
1. Đọc đúng mục phase trong `PLAN.md` + mục SPEC liên quan.
2. Viết test trước cho logic, rồi code cho test qua (bảng test của phase là bắt buộc).
3. Chạy lệnh tự kiểm tra của phase → xanh, rồi `scripts/check.ps1` → xanh.
4. Viết `docs/explain/Pn.md` (≤ 40 dòng, tiếng Việt): quyết định chính, trade-off, 3 câu hỏi phỏng vấn có thể bị hỏi về phần này + gợi ý trả lời.

## Luật code
- Package theo feature (SPEC mục 6). Không phá luật ArchUnit.
- Log có `jobId` (MDC). Không secret trong code, log, test.
- Không dùng `th:utext`. Không gọi CDN (font, highlight.js để local).

## File dùng chung — chỉ orchestrator được sửa
`pom.xml`, `application*.yml`, `compose.yaml`, `V*__*.sql` đã merge, `scripts/`, `CLAUDE.md`, `PLAN.md`, `SPEC.md`, `docs/STATUS.md`.
Subagent cần thay đổi các file này → ghi đề xuất vào `NEEDS.md` (file nào, sửa gì, vì sao).
- Template slide đã có (`templates/layout.html`, `templates/scenes/*`, `static/css`, `static/js/slide.js`, `static/vendor`): không viết lại; chỉ phase P6 được sửa nhỏ khi `TemplateGalleryTest` báo lỗi, và phải ghi rõ trong báo cáo.
- Migration Flyway mới: tạo file với số version tạm `V9xx__...` để orchestrator đánh số lại khi merge.

## Git
- Không `git push`, không sửa lịch sử đã có, không commit file trong `.gitignore`.
- Subagent: commit trong branch worktree của mình, message `Pn: <tóm tắt>`.

## Bí mật
- Không đọc/sửa `.env`. Cần key thật → dùng mock/WireMock trong test và báo orchestrator.
