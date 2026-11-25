-- P13b: chi phí ước tính mỗi video. Số version tạm (V901) — orchestrator đánh số lại khi merge.
-- tts_chars: ký tự thực gửi provider TTS (sau lexicon, không tính câu lấy từ cache), cộng dồn khi voice lại.
-- audio_ms: tổng độ dài audio lần voice gần nhất.
ALTER TABLE video_job ADD COLUMN tts_chars BIGINT NOT NULL DEFAULT 0;
ALTER TABLE video_job ADD COLUMN audio_ms BIGINT;
ALTER TABLE video_job ADD CONSTRAINT ck_video_job_tts_chars CHECK (tts_chars >= 0);
