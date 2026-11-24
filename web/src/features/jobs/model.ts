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
