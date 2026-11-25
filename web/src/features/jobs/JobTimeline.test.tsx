import { act } from "react";
import { createRoot } from "react-dom/client";
import { beforeEach, describe, expect, it } from "vitest";
import type { JobAttempt } from "@/domain/job";
import { JobTimeline } from "./JobTimeline";
import { errorHeadline, formatDuration, sortAttempts } from "./model";

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

  it("lỗi dài nhiều dòng: mặc định thu gọn 1 dòng, bấm → mở, bấm lại → thu", () => {
    const traceback = [
      "Traceback (most recent call last):",
      ...Array.from({ length: 40 }, (_, i) => `  File "edge_tts/communicate.py", line ${i}, in stream`),
      "edge_tts.exceptions.NoAudioReceived: No audio was received.",
    ].join("\n");
    const el = render(<JobTimeline attempts={[attempt({ step: "VOICE", outcome: "FAILED", error: traceback })]} />);
    const button = el.querySelector<HTMLButtonElement>("button");
    expect(button?.textContent).toBe("Xem chi tiết");
    expect(button?.getAttribute("aria-expanded")).toBe("false");
    // thu gọn: chỉ dòng có ý nghĩa (dòng cuối của traceback), không có toàn văn
    const note = el.querySelector("[role=note]");
    expect(note?.textContent).toContain("NoAudioReceived: No audio was received.");
    expect(note?.textContent).not.toContain("line 12");
    expect(el.querySelector("pre")).toBeNull();

    act(() => button?.click());
    expect(button?.textContent).toBe("Thu gọn");
    expect(button?.getAttribute("aria-expanded")).toBe("true");
    const pre = el.querySelector("pre");
    expect(pre?.textContent).toBe(traceback);
    expect(pre?.id).toBe(button?.getAttribute("aria-controls"));

    act(() => button?.click());
    expect(button?.textContent).toBe("Xem chi tiết");
    expect(el.querySelector("pre")).toBeNull();
  });

  it("lỗi một dòng dài: thu gọn bằng dòng đầu, có nút mở", () => {
    const long = `ElevenLabs từ chối: HTTP 400 ${"x".repeat(300)}`;
    const el = render(<JobTimeline attempts={[attempt({ outcome: "FAILED", error: long })]} />);
    expect(el.querySelector("button")?.textContent).toBe("Xem chi tiết");
    expect(el.querySelector("[role=note]")?.textContent).toContain("ElevenLabs từ chối: HTTP 400");
  });

  it("errorHeadline: dòng đầu, hoặc dòng cuối nếu là traceback / stack", () => {
    expect(errorHeadline("Lỗi A\nchi tiết")).toBe("Lỗi A");
    expect(errorHeadline("\n\n  Lỗi B  \n")).toBe("Lỗi B");
    expect(errorHeadline("Traceback (most recent call last):\n  x\nValueError: hỏng")).toBe("ValueError: hỏng");
    expect(errorHeadline("")).toBe("");
  });

  it("formatDuration", () => {
    expect(formatDuration(null)).toBe("—");
    expect(formatDuration(-1)).toBe("—");
    expect(formatDuration(999)).toBe("999 ms");
    expect(formatDuration(12_300)).toBe("12,3 s");
    expect(formatDuration(120_000)).toBe("2 phút");
  });
});
