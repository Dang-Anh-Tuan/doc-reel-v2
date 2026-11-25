import type { AttemptOutcome, JobAttempt, JobState, JobStep } from "@/domain/job";

/** Màu của trạng thái: chờ (xám), đang chạy (vàng đồng), xong (xanh), lỗi (đỏ). */
export type StateTone = "queued" | "running" | "ok" | "bad";

export const STATE_META: Record<JobState, { label: string; tone: StateTone }> = {
  RECEIVED: { label: "Đã nhận", tone: "queued" },
  RENDERED: { label: "Đã render", tone: "running" },
  VOICED: { label: "Đã lồng tiếng", tone: "running" },
  ASSEMBLED: { label: "Đã ghép video", tone: "running" },
  UPLOADED: { label: "Đã đăng", tone: "ok" },
  FAILED: { label: "Lỗi", tone: "bad" },
};

export const STEP_LABEL: Record<JobStep, string> = {
  RENDER: "Render slide",
  VOICE: "Lồng tiếng",
  ASSEMBLE: "Ghép video",
  UPLOAD: "Đăng YouTube",
};

export const OUTCOME_META: Record<AttemptOutcome, { label: string; tone: "ok" | "warn" | "bad" | "neutral" }> = {
  SUCCESS: { label: "Thành công", tone: "ok" },
  SKIPPED: { label: "Bỏ qua (đã có)", tone: "neutral" },
  RETRY: { label: "Lỗi tạm, sẽ thử lại", tone: "warn" },
  FAILED: { label: "Lỗi", tone: "bad" },
};

/** Nhãn bước; giá trị lạ (DB cũ) giữ nguyên. */
export function stepLabel(step: string | null | undefined): string {
  if (!step) return "—";
  return STEP_LABEL[step as JobStep] ?? step;
}

/** Attempt theo thời gian bắt đầu tăng dần; cùng thời điểm → theo lần thử. Không đổi mảng gốc. */
export function sortAttempts(attempts: readonly JobAttempt[]): JobAttempt[] {
  return [...attempts].sort((a, b) => {
    const t = Date.parse(a.startedAt) - Date.parse(b.startedAt);
    return t !== 0 && Number.isFinite(t) ? t : a.attemptNo - b.attemptNo;
  });
}

/** 850 → "850 ms", 12_300 → "12,3 s", 125_000 → "2 phút 5 s". */
export function formatDuration(ms: number | null | undefined): string {
  if (ms == null || !Number.isFinite(ms) || ms < 0) return "—";
  if (ms < 1000) return `${Math.round(ms)} ms`;
  const s = ms / 1000;
  if (s < 60) return `${s.toLocaleString("vi-VN", { maximumFractionDigits: 1 })} s`;
  const min = Math.floor(s / 60);
  const rest = Math.round(s - min * 60);
  return rest === 0 ? `${min} phút` : `${min} phút ${rest} s`;
}

/** ISO → "24/11/2026 08:05:03" theo giờ máy; chuỗi hỏng → giữ nguyên. */
export function formatTime(iso: string | null | undefined): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("vi-VN", { hour12: false });
}

// ------------------------------------------------------------------ Chi phí (P13b)

/** 1200 → "1.200" (vi-VN). */
export function formatNumber(n: number | null | undefined, maxFractionDigits = 0): string {
  if (n == null || !Number.isFinite(n)) return "—";
  return n.toLocaleString("vi-VN", { maximumFractionDigits: maxFractionDigits });
}

/** 0.24 → "0,24 USD"; số rất nhỏ giữ 4 chữ số (0.0048 → "0,0048 USD"); 0 → "0 USD". */
export function formatUsd(n: number | null | undefined): string {
  if (n == null || !Number.isFinite(n)) return "—";
  if (n === 0) return "0 USD";
  const digits = Math.abs(n) < 0.01 ? 4 : 2;
  return `${n.toLocaleString("vi-VN", { minimumFractionDigits: 2, maximumFractionDigits: digits })} USD`;
}

/** Số ký tự mặc định của một video khi chưa có job UPLOADED nào để lấy trung bình. */
export const DEFAULT_CHARS_PER_VIDEO = 1200;
/** Lấy trung bình trên tối đa ngần này job UPLOADED gần nhất. */
const AVERAGE_WINDOW = 10;

/**
 * Trung bình ttsChars của các job ElevenLabs UPLOADED gần đây (có ký tự > 0; job edge-tts không tính); không có →
 * {@link DEFAULT_CHARS_PER_VIDEO}.
 */
export function averageCharsPerVideo(
  jobs: ReadonlyArray<{ state: string; ttsChars?: number; ttsProvider?: string | null }>,
): number {
  const recent = jobs
    .filter((j) => j.state === "UPLOADED" && (j.ttsChars ?? 0) > 0 && j.ttsProvider?.toUpperCase() !== "EDGE")
    .slice(0, AVERAGE_WINDOW)
    .map((j) => j.ttsChars ?? 0);
  if (recent.length === 0) return DEFAULT_CHARS_PER_VIDEO;
  return Math.round(recent.reduce((a, b) => a + b, 0) / recent.length);
}

/**
 * Còn làm được khoảng bao nhiêu video: credit còn lại / (ký tự TB × credit mỗi ký tự).
 *
 * @param creditsPerChar đơn giá cấu hình ở server; thiếu / không hợp lệ → 1
 */
export function remainingVideos(remaining: number, avgChars: number, creditsPerChar = 1): number {
  const rate = Number.isFinite(creditsPerChar) && creditsPerChar > 0 ? creditsPerChar : 1;
  const perVideo = avgChars * rate;
  if (!Number.isFinite(remaining) || remaining <= 0 || perVideo <= 0) return 0;
  return Math.floor(remaining / perVideo);
}

// ------------------------------------------------------------------ Lỗi gọn (P13b)

/** Lỗi ngắn hơn thế này và chỉ 1 dòng → hiện nguyên, không cần nút mở rộng. */
export const SHORT_ERROR_CHARS = 160;

/**
 * Dòng có ý nghĩa nhất của một lỗi nhiều dòng: thường là dòng đầu; traceback Python / stack Java (dòng đầu là
 * "Traceback…" hoặc "at …") → dòng cuối (nơi có tên exception + thông điệp).
 */
export function errorHeadline(text: string): string {
  const lines = text
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter((l) => l.length > 0);
  const first = lines[0] ?? "";
  if (lines.length > 1 && /^(Traceback\b|at\s|File\s")/.test(first)) return lines.at(-1) ?? first;
  return first;
}

export function isLongError(text: string): boolean {
  return text.length > SHORT_ERROR_CHARS || /\r?\n/.test(text.trim());
}

/** Ghi chú theo provider: edge-tts miễn phí; provider trả phí → nói rõ cách ước tính. */
export function costNote(provider: string | null): string {
  if (provider?.toUpperCase() === "EDGE") {
    return "edge-tts miễn phí — chi phí 0. Số ký tự chỉ để tham khảo.";
  }
  const name = provider ?? "provider TTS";
  return (
    `Số liệu ước tính: ký tự thực gửi ${name} (không tính câu lấy từ cache) × đơn giá cấu hình. ` +
    `Credit thật do ${name} tính có thể khác.`
  );
}
