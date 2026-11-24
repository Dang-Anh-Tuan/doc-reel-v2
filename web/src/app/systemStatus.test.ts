import { describe, expect, it } from "vitest";
import type { SystemCheck } from "@/domain/system";
import { ApiError } from "@/lib/api/client";
import { summarizeSystem } from "./systemStatus";

const check = (status: SystemCheck["status"]): SystemCheck => ({
  status,
  checkedAt: "2026-11-24T08:00:00Z",
  items: [
    { name: "DB", status: "OK", detail: "", fix: null },
    { name: "TTS", status: status, detail: "", fix: status === "OK" ? null : "sửa" },
  ],
});

describe("summarizeSystem", () => {
  it("OK → xanh", () => {
    expect(summarizeSystem(check("OK"), undefined).tone).toBe("ok");
  });

  it("WARN → vàng, nêu mục cần xem", () => {
    const s = summarizeSystem(check("WARN"), undefined);
    expect(s.tone).toBe("warn");
    expect(s.hint).toContain("TTS");
  });

  it("CRITICAL → đỏ", () => {
    expect(summarizeSystem(check("CRITICAL"), undefined).tone).toBe("bad");
  });

  it("lỗi mạng → đỏ 'Mất kết nối'", () => {
    const error = new ApiError({ kind: "network", status: null, title: "Không kết nối được máy chủ", detail: "x" });
    const s = summarizeSystem(check("OK"), error);
    expect(s.tone).toBe("bad");
    expect(s.label).toBe("Mất kết nối");
  });

  it("chưa có dữ liệu → trung tính", () => {
    expect(summarizeSystem(undefined, undefined).tone).toBe("neutral");
  });
});
