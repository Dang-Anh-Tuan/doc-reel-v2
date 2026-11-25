# P8b · Giọng ElevenLabs tiếng Việt tự nhiên, từ tiếng Anh đọc đúng

## Quyết định chính
- **Ép ngôn ngữ**: gửi `language_code: "vi"` → Flash v2.5 không tự đoán ngôn ngữ từng câu (nguyên nhân giọng "lơ lớ"). `apply_text_normalization: "on"` vì Flash mặc định tắt (số "3", "2024" đọc kém).
- **Biểu cảm**: `voice_settings` đủ 5 field — `stability 0.4` (thấp hơn mặc định 0.5 → có nhấn nhá), `similarity_boost 0.8`, `style 0.2`, `use_speaker_boost true`, `speed`.
- **Lexicon chỉ cho provider cần**: `TtsProvider.appliesLexicon(settings)` (mặc định true → edge giữ hành vi cũ); ElevenLabs theo `apply-lexicon` (mặc định false). ElevenLabs đọc "React", "useEffect" xen trong câu Việt tốt; chữ phiên âm kiểu edge ("Rì-ách") mới làm ngọng. `TtsService` giữ câu gốc trong `Unit`, áp lexicon lúc render theo provider → fallback giữa chừng sang edge vẫn có phiên âm.
- **Chỉ gửi field có giá trị**: null/rỗng → bỏ khỏi JSON; `send-context=false` bỏ `previous_text/next_text`. Đổi sang model không hỗ trợ field (vd v3 với `language_code`) chỉ cần sửa yml, không 400.
- **Ảnh chụp job**: `tts_settings.elevenlabs` thêm `languageCode, stability, similarityBoost, style, speakerBoost, textNormalization, sendContext, applyLexicon`; `ElevenLabsProvider` dùng cả `model` đã chụp. Job cũ thiếu khóa → `ElevenTuning.DEFAULTS`; khóa có mặt nhưng null/"" → không gửi (phân biệt "vắng" với "tắt").
- **Cache**: `voiceKey` = model|voice|speed|lang|stab|sim|style|boost|norm|ctx|lex → đổi bất kỳ tham số nào là cache miss, không dùng lại audio sai cấu hình.
- **Đếm ký tự** (P13b) = text thực gửi: ElevenLabs = câu gốc, edge = sau lexicon. `needed` để kiểm quota tính theo cách provider chính sẽ gửi.
- Mặc định đặt ở `AppProperties.ElevenLabs.DEFAULT_*` (chuỗi hằng dùng cho `@DefaultValue`), `ElevenTuning.DEFAULTS` đọc lại cùng hằng → một nguồn.

## Trade-off
- **Flash v2.5 vs v3/v4**: Flash rẻ (~0,5 credit/ký tự), trễ thấp, hỗ trợ `language_code`; v3/v4 biểu cảm hơn nhưng tốn credit hơn, chậm hơn, không SSML break, chưa rõ hỗ trợ `previous_text`. Giữ Flash, cho cấu hình để thử (đổi model + tắt field không hỗ trợ).
- **Từng câu vs cả scene**: đọc từng câu → phụ đề khớp tuyệt đối, cache theo câu (retry rẻ), nhưng ngữ điệu giữa các câu bị "reset" dù có previous/next. Đọc cả scene liền mạch hơn nhưng phải căn phụ đề bằng mốc thời gian (`/with-timestamps`) và cache thô hơn.
- `style > 0` tăng biểu cảm nhưng tốn tài nguyên/độ trễ hơn; `stability` quá thấp có thể làm giọng thất thường giữa các câu → 0.4 là điểm giữa, chỉnh bằng tai.
- Đổi cấu hình (kể cả lần đầu sau P8b) làm cache ElevenLabs cũ không dùng được → video đầu sau khi đổi tốn quota đầy đủ.

## 3 câu hỏi phỏng vấn
1. *Vì sao không xóa hẳn lexicon?* — edge-tts vi-VN đọc tiếng Anh rất kém, vẫn cần phiên âm; quyết định thuộc về provider (`appliesLexicon`), `TtsService` không phải `if (provider == EDGE)` → thêm provider mới không sửa service.
2. *Làm sao retry không đổi giọng khi đã sửa yml?* — Mọi tham số ảnh hưởng audio được chụp vào `video_job.tts_settings` lúc tạo job; retry đọc ảnh chụp. Job cũ thiếu khóa → mặc định trong code, đọc JSON thiếu field không lỗi.
3. *Vì sao `voiceKey` phải gồm cả `style`, `normalization`, `applyLexicon`?* — Cache theo `sha256(provider + voiceKey + câu)`; bỏ sót một tham số thì đổi cấu hình vẫn trúng cache, phát lại audio cũ sai cấu hình mà không ai biết.
