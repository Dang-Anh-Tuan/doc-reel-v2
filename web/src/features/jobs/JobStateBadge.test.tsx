import { act } from "react";
import { createRoot } from "react-dom/client";
import { beforeEach, describe, expect, it } from "vitest";
import { JOB_STATES, type JobState } from "@/domain/job";
import { JobStateBadge } from "./JobStateBadge";
import { STATE_META } from "./model";

declare global {
  var IS_REACT_ACT_ENVIRONMENT: boolean | undefined;
}

beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
});

function render(state: JobState): HTMLElement {
  const container = document.createElement("div");
  act(() => createRoot(container).render(<JobStateBadge state={state} />));
  const el = container.firstElementChild;
  if (!(el instanceof HTMLElement)) throw new Error("không render được badge");
  return el;
}

const EXPECTED: Record<JobState, { label: string; tone: string }> = {
  RECEIVED: { label: "Đã nhận", tone: "queued" },
  RENDERED: { label: "Đã render", tone: "running" },
  VOICED: { label: "Đã lồng tiếng", tone: "running" },
  ASSEMBLED: { label: "Đã ghép video", tone: "running" },
  UPLOADED: { label: "Đã đăng", tone: "ok" },
  FAILED: { label: "Lỗi", tone: "bad" },
};

describe("JobStateBadge", () => {
  it.each(JOB_STATES)("%s → đúng nhãn và màu", (state) => {
    const el = render(state);
    expect(el.textContent).toBe(EXPECTED[state].label);
    expect(el.dataset.tone).toBe(EXPECTED[state].tone);
    expect(el.dataset.state).toBe(state);
    expect(el.title).toBe(state);
  });

  it("mọi state của Java đều có nhãn/màu (không sót)", () => {
    expect(Object.keys(STATE_META).sort()).toEqual([...JOB_STATES].sort());
  });

  it("chỉ UPLOADED xanh, chỉ FAILED đỏ", () => {
    const tones = JOB_STATES.map((s) => STATE_META[s].tone);
    expect(tones.filter((t) => t === "ok")).toHaveLength(1);
    expect(tones.filter((t) => t === "bad")).toHaveLength(1);
  });

  it("state lạ (server mới hơn web) → hiện nguyên chữ, màu xám", () => {
    const el = render("PAUSED" as JobState);
    expect(el.textContent).toBe("PAUSED");
    expect(el.dataset.tone).toBe("queued");
  });
});
