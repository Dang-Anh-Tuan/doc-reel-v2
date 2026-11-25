# Trạng thái

| Đợt | Phase | Trạng thái | Branch | Kiểm tra phase | check.ps1 | Giải thích |
|---|---|---|---|---|---|---|
| — | Template slide (story 7) | ✅ có sẵn | | | | |
| W0 | P0 | ✅ merged | phase/p0 | ĐẠT | ĐẠT | docs/explain/P0.md |
| W1 | P1 | ✅ merged | phase/p1 | ĐẠT | ĐẠT | docs/explain/P1.md |
| W1 | P6 render + gallery (G1) | ✅ merged (G1 duyệt) | phase/p6 | ĐẠT | ĐẠT | docs/explain/P6.md |
| W1 | P9 | ✅ merged | phase/p9 | ĐẠT | ĐẠT | docs/explain/P9.md |
| W2 | P2 | ✅ merged | phase/p2 | ĐẠT | ĐẠT | docs/explain/P2.md |
| W2 | P8 | ✅ merged | phase/p8 | ĐẠT | ĐẠT | docs/explain/P8.md |
| W2 | P10 | ✅ merged | phase/p10 | ĐẠT | ĐẠT | docs/explain/P10.md |
| — | P7 spike giọng (G2, người làm) | ✅ giọng + lexicon | | | | |
| W3 | P3 | ✅ merged | phase/p3 | ĐẠT | ĐẠT | docs/explain/P3.md |
| W3 | P4 | ✅ merged | phase/p4 | ĐẠT | ĐẠT | docs/explain/P4.md |
| W4 | P5 | ✅ merged | phase/p5 | ĐẠT | ĐẠT | docs/explain/P5.md |
| W5 | P11 | ✅ merged (chờ chạy thật G5) | phase/p11 | ĐẠT (-Media -E2E) | ĐẠT | docs/explain/P11.md |
| B-W1 | P12 nền web quản trị | ✅ merged | phase/p12 | ĐẠT | ĐẠT | docs/explain/P12.md |
| B-W2 | P13 jobs qua web | ✅ merged | phase/p13 | ĐẠT | ĐẠT | docs/explain/P13.md |
| B-W2 | P13b chi phí mỗi video + lỗi thu gọn | ✅ merged | phase/p13b | ĐẠT | ĐẠT | docs/explain/P13b.md |
| B-W2 | P8b giọng ElevenLabs tự nhiên (vi) | ⏳ | | | | |

Cổng: G0 ✅ · G1 ✅ · G2 ✅ · G3 ✅ · G4 ✅ (token YouTube 2026-11-24) · G5 ⏳

Ghi chú môi trường: web cần Node ≥ 22.22 (`check.ps1` tự dùng `%LOCALAPPDATA%\nvm\v22.22.0`; `mvnw package` tự tải Node vào `target/node`).
