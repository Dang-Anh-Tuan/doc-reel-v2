/** Khớp `com.tuan.docreel.voice.TtsUsageView` — `GET /api/tts/usage` (quota ElevenLabs tháng này, cache 60 s). */
export interface TtsUsage {
  /** false = thiếu key / lỗi gọi ElevenLabs → chỉ có `reason`. */
  available: boolean;
  provider: string;
  /** Credit đã dùng trong chu kỳ (`character_count`). */
  used?: number;
  /** `character_limit`. */
  limit?: number;
  remaining?: number;
  /** ISO-8601; lúc quota được đặt lại. */
  resetAt?: string;
  reason?: string;
  /** Đơn giá cấu hình (credit / ký tự) — đổi "ký tự TB / video" sang credit; thiếu (server cũ) → 1. */
  creditsPerChar?: number;
}

export const TTS_USAGE_PATH = "/api/tts/usage";
