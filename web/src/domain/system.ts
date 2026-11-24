/** Khớp `com.tuan.docreel.system.SystemCheckView` / `CheckResult` (Java) — `GET /api/system/check`. */

export type CheckStatus = "OK" | "WARN" | "CRITICAL";

export interface CheckResult {
  name: string;
  status: CheckStatus;
  detail: string;
  /** Hướng dẫn sửa khi không OK; null khi OK. */
  fix: string | null;
}

export interface SystemCheck {
  /** Mức xấu nhất trong các mục. */
  status: CheckStatus;
  checkedAt: string;
  items: CheckResult[];
}

export const SYSTEM_CHECK_PATH = "/api/system/check";
