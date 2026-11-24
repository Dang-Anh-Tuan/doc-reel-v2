import { act } from "react";
import { createRoot } from "react-dom/client";
import { beforeEach, describe, expect, it } from "vitest";
import type { JobAttempt } from "@/domain/job";
import { JobTimeline } from "./JobTimeline";
import { formatDuration, sortAttempts } from "./model";

declare global {
  var IS_REACT_ACT_ENVIRONMENT: boolean | undefined;
}

beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
});

function render(ui: React.ReactNode): HTMLElement {
  const container = document.createElement("div");
  act(() => createRoot(container).render(ui));
  return container;
}

const attempt = (over: Partial<JobAttempt>): JobAttempt => ({
  step: "RENDER",
  attemptNo: 1,
  outcome: "SUCCESS",
  durationMs: 1200,
  error: null,
  startedAt: "2026-11-24T08:00:00Z",
  finishedAt: "2026-11-24T08:00:01.200Z",
  ...over,
});

// Cố ý không theo thứ tự thời gian
const ATTEMPTS: JobAttempt[] = [
  attempt({ step: "VOICE", attemptNo: 2, outcome: "FAILED", startedAt: "2026-11-24T08:05:00Z", error: "Hết quota ElevenLabs", durationMs: 125_000 }),
  attempt({ step: "RENDER", attemptNo: 1, outcome: "SUCCESS", startedAt: "2026-11-24T08:00:00Z", durationMs: 850 }),
  attempt({ step: "VOICE", attemptNo: 1, outcome: "RETRY", startedAt: "2026-11-24T08:01:00Z", error: "timeout", durationMs: 30_000 }),
];

describe("JobTimeline", () => {
  it("sắp attempt theo thời gian bắt đầu (cũ → mới)", () => {
    const el = render(<JobTimeline attempts={ATTEMPTS} />);
    const items = [...el.querySelectorAll<HTMLElement>("[data-testid=attempt]")];
    expect(items.map((li) => li.dataset.outcome)).toEqual(["SUCCESS", "RETRY", "FAILED"]);
    expect(items[0]?.textContent).toContain("Render slide");
    expect(items[1]?.textContent).toContain("Lồng tiếng");
    expect(items[1]?.textContent).toContain("lần 1");
    expect(items[2]?.textContent).toContain("lần 2");
  });

  it("hiện lỗi và thời lượng của từng attempt", () => {
    const el = render(<JobTimeline attempts={ATTEMPTS} />);
    const items = [...el.querySelectorAll<HTMLElement>("[data-testid=attempt]")];
    const errors = [...el.querySelectorAll("[role=note]")].map((n) => n.textContent);
    expect(errors).toEqual(["timeout", "Hết quota ElevenLabs"]);
    expect(items[0]?.querySelector("[role=note]")).toBeNull();
    expect(items[0]?.textContent).toContain("850 ms");
    expect(items[2]?.textContent).toContain("2 phút 5 s");
  });

  it("không có attempt → thông báo trống", () => {
    const el = render(<JobTimeline attempts={[]} />);
    expect(el.textContent).toContain("Chưa có bước nào chạy");
  });

  it("job đang chạy → thêm mục bước hiện tại ở cuối", () => {
    const el = render(<JobTimeline attempts={[ATTEMPTS[1]!]} runningStep="VOICE" />);
    const items = [...el.querySelectorAll<HTMLElement>("li")];
    expect(items).toHaveLength(2);
    expect(items[1]?.dataset.outcome).toBe("RUNNING");
    expect(items[1]?.textContent).toContain("Lồng tiếng");
  });

  it("sortAttempts không đổi mảng gốc; cùng thời điểm → theo lần thử", () => {
    const same = [attempt({ attemptNo: 2 }), attempt({ attemptNo: 1 })];
    expect(sortAttempts(same).map((a) => a.attemptNo)).toEqual([1, 2]);
    expect(same.map((a) => a.attemptNo)).toEqual([2, 1]);
  });

  it("formatDuration", () => {
    expect(formatDuration(null)).toBe("—");
    expect(formatDuration(-1)).toBe("—");
    expect(formatDuration(999)).toBe("999 ms");
    expect(formatDuration(12_300)).toBe("12,3 s");
    expect(formatDuration(120_000)).toBe("2 phút");
  });
});
