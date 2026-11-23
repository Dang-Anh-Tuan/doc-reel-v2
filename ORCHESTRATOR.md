# Orchestrator — chạy toàn bộ PLAN bằng agent

> Dán phần "Prompt" vào **một phiên Claude Code chính** ở thư mục gốc repo. Phiên này không tự viết code feature:
> nó chia việc cho subagent `story-dev`, gọi `phase-reviewer`, merge, chạy kiểm tra, cập nhật `docs/STATUS.md`.

## Đợt chạy (tối đa 3 subagent cùng lúc)

| Đợt | Song song | Phụ thuộc | Ghi chú |
|---|---|---|---|
| W0 | **P0** | — | Chạy 1 mình. Thêm đủ dependency cho cả MVP (Playwright, Jackson, WireMock, Testcontainers…) + interface chung (`Step`, `StepContext`, record `VideoScript`/`Scene`) để các đợt sau không phải sửa `pom.xml` |
| W1 | **P1** · **P6** (renderer + capturer + `TemplateGalleryTest`, template đã có sẵn trong `src/main/resources`) · **P9** (ghép video, dùng PNG/MP3 mẫu) | W0 | 3 package khác nhau: `script/`, `render/`, `video/`. P6 **không viết lại template**, chỉ sửa nếu gallery test báo lỗi |
| W2 | **P2** · **P8** (TTS + phụ đề, provider mock; giọng lấy giá trị tạm trong config đến khi qua G2) · **P10** (YouTube, mock) | P2←P1 | |
| W3 | **P3** · **P4** | P2 | |
| W4 | **P5** | P3, P4 | Nối các bước thành pipeline |
| W5 | **P11** | tất cả | E2E + WinSW |

## Cổng cần người (orchestrator DỪNG và hỏi)
| Cổng | Khi nào | Người làm gì |
|---|---|---|
| G0 | Trước W0 | Đã có commit đầu tiên (worktree cần ít nhất 1 commit); Docker Desktop đang chạy |
| G1 | Sau P6 | Mở `build/gallery/contact-sheet.png`, duyệt hoặc góp ý template |
| G2 | P7 (spike giọng) | Agent tạo sẵn `tools/voice-samples/*.mp3` (2 provider × vài giọng × câu nhiều thuật ngữ); người nghe, chọn giọng, duyệt 20 mục lexicon nháp |
| G3 | Trước test thật P8/P10 | Người điền `.env`: ElevenLabs key, YouTube OAuth client; cài `pip install edge-tts` (không cần key) |
| G4 | Lần upload đầu | Người đăng nhập Google trong trình duyệt để cấp quyền OAuth |
| G5 | Cuối W5 | Người xem video thật trên điện thoại, nghiệm thu MVP |

## Prompt (dán vào phiên chính)
```text
Bạn là orchestrator của repo DocReel. Đọc CLAUDE.md, SPEC.md, PLAN.md, ORCHESTRATOR.md. Template slide đã có sẵn (templates/, static/), không tạo lại.
Mục tiêu: hoàn thành Giai đoạn A (P0–P11) bằng subagent. Tôi đã quyết định để agent làm cả các phase ghi "Tôi".

Vòng lặp cho mỗi đợt trong ORCHESTRATOR.md:
1. Kiểm tra phụ thuộc + cổng người. Cổng chưa qua → dừng, nói tôi cần làm gì, chờ tôi trả lời.
2. Giao mỗi phase của đợt cho 1 subagent story-dev (chạy song song, tối đa 3), kèm: tên phase, mục PLAN/SPEC cần đọc, file mẫu liên quan.
3. Khi một subagent xong: gọi phase-reviewer cho branch đó. FIX → gửi lại đúng danh sách sửa cho story-dev (tối đa 2 vòng, quá thì dừng hỏi tôi).
4. PASS → xử lý NEEDS.md (sửa file dùng chung, đánh lại số migration), merge branch vào nhánh chính từng cái một, chạy scripts/check.ps1 sau MỖI lần merge. Đỏ → sửa ngay hoặc revert merge đó.
5. Cập nhật docs/STATUS.md (phase, trạng thái, branch, kết quả kiểm tra, link docs/explain/Pn.md), commit "orchestrator: W<n> done".
6. Báo tôi 5 dòng tóm tắt đợt, rồi sang đợt tiếp.

Luật: không git push; không đọc .env; không hạ ngưỡng test; đổi yêu cầu so với SPEC → hỏi tôi trước.
Bắt đầu: kiểm tra cổng G0.
```
