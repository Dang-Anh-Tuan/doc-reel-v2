import { act } from "react";
import { createRoot } from "react-dom/client";
import { beforeEach, describe, expect, it } from "vitest";
import type { JobCost } from "@/domain/job";
import { CostPanel } from "./CostPanel";
import { averageCharsPerVideo, formatNumber, formatUsd, remainingVideos } from "./model";

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

const COST: JobCost = {
  ttsProvider: "ELEVENLABS",
  ttsChars: 1234,
  estimatedCredits: 1234,
  estimatedUsd: 0.2468,
  audioMs: 83_000,
  processingMs: 125_000,
  wallMs: 1_500_000,
};

/** Giá trị (dd) theo nhãn (dt). */
function facts(el: HTMLElement): Record<string, string> {
  const out: Record<string, string> = {};
  el.querySelectorAll("dt").forEach((dt) => {
    out[dt.textContent ?? ""] = dt.nextElementSibling?.textContent ?? "";
  });
  return out;
}

describe("CostPanel", () => {
  it("format số / tiền theo vi-VN và ghi rõ là ước tính", () => {
    const el = render(<CostPanel cost={COST} done />);
    const f = facts(el);
    expect(f["Giọng"]).toBe("ELEVENLABS");
    expect(f["Ký tự gửi TTS"]).toBe("1.234");
    expect(f["Credit"]).toBe("≈ 1.234");
    expect(f["Chi phí"]).toBe("≈ 0,25 USD");
    expect(f["Độ dài audio"]).toBe("1 phút 23 s");
    expect(f["Thời gian xử lý"]).toBe("2 phút 5 s");
    expect(f["Nhận → xong"]).toBe("25 phút");
    expect(el.textContent).toContain("ước tính");
  });

  it("job chưa xong: nhãn 'Nhận → hiện tại'; chưa voice → '—'", () => {
    const el = render(
      <CostPanel
        cost={{ ...COST, ttsChars: 0, estimatedCredits: 0, estimatedUsd: 0, audioMs: null }}
        done={false}
      />,
    );
    const f = facts(el);
    expect(f["Chi phí"]).toBe("≈ 0 USD");
    expect(f["Độ dài audio"]).toBe("—");
    expect(f["Nhận → hiện tại"]).toBe("25 phút");
  });

  it("ghi chú theo provider: edge miễn phí, không nói 'gửi ElevenLabs'", () => {
    const edge = render(<CostPanel cost={{ ...COST, ttsProvider: "EDGE", estimatedUsd: 0, estimatedCredits: 0 }} done />);
    expect(edge.textContent).toContain("edge-tts miễn phí");
    expect(edge.textContent).not.toContain("gửi ElevenLabs");
    const eleven = render(<CostPanel cost={COST} done />);
    expect(eleven.textContent).toContain("ký tự thực gửi ELEVENLABS");
  });

  it("server cũ không có cost → thông báo", () => {
    const el = render(<CostPanel cost={undefined} done />);
    expect(el.textContent).toContain("Chưa có dữ liệu chi phí");
  });

  it("formatUsd / formatNumber", () => {
    expect(formatUsd(0.24)).toBe("0,24 USD");
    expect(formatUsd(1.5)).toBe("1,50 USD");
    expect(formatUsd(0.0048)).toBe("0,0048 USD");
    expect(formatUsd(0)).toBe("0 USD");
    expect(formatUsd(null)).toBe("—");
    expect(formatNumber(30000)).toBe("30.000");
  });

  it("trung bình ký tự / video và số video còn lại", () => {
    expect(averageCharsPerVideo([])).toBe(1200);
    expect(
      averageCharsPerVideo([
        { state: "UPLOADED", ttsChars: 1000 },
        { state: "FAILED", ttsChars: 9999 },
        { state: "UPLOADED", ttsChars: 0 },
        { state: "UPLOADED", ttsChars: 2000 },
      ]),
    ).toBe(1500);
    expect(remainingVideos(25_800, 1200)).toBe(21);
    expect(remainingVideos(0, 1200)).toBe(0);
    // credit / ký tự: Flash v2.5 = 0,5 → gấp đôi số video
    expect(remainingVideos(25_800, 1200, 0.5)).toBe(43);
    expect(remainingVideos(25_800, 1200, 0)).toBe(21); // giá trị hỏng → 1
    // job edge-tts không vào trung bình
    expect(averageCharsPerVideo([{ state: "UPLOADED", ttsChars: 5000, ttsProvider: "EDGE" }])).toBe(1200);
  });
});
