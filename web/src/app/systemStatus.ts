import type { Tone } from "@/components/ui";
import type { SystemCheck } from "@/domain/system";
import type { ApiError } from "@/lib/api/client";

export interface SystemStatusSummary {
  tone: Tone;
  label: string;
  hint: string;
}

/** Thẻ Hệ thống: xanh (OK) / vàng (WARN) / đỏ (CRITICAL hoặc không gọi được server). */
export function summarizeSystem(data: SystemCheck | undefined, error: ApiError | undefined): SystemStatusSummary {
  if (error) {
    return {
      tone: "bad",
      label: error.kind === "network" ? "Mất kết nối" : "Không đọc được",
      hint: error.detail ? `${error.title}: ${error.detail}` : error.title,
    };
  }
  if (!data) return { tone: "neutral", label: "Đang kiểm…", hint: "Đang gọi /api/system/check" };
  const bad = data.items.filter((i) => i.status !== "OK").map((i) => i.name);
  switch (data.status) {
    case "OK":
      return { tone: "ok", label: "Ổn định", hint: `${data.items.length} mục đều OK` };
    case "WARN":
      return { tone: "warn", label: "Cảnh báo", hint: `Cần xem: ${bad.join(", ")}` };
    case "CRITICAL":
      return { tone: "bad", label: "Lỗi nghiêm trọng", hint: `Worker tạm dừng — lỗi: ${bad.join(", ")}` };
  }
}
