import { act } from "react";
import { createRoot } from "react-dom/client";
import { beforeEach, describe, expect, it } from "vitest";
import { TtsUsageCard } from "./TtsUsageCard";

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

describe("TtsUsageCard", () => {
  it("hiện used / limit, thanh tiến độ và số video còn lại", () => {
    const el = render(
      <TtsUsageCard
        usage={{ available: true, provider: "ELEVENLABS", used: 4200, limit: 30000, remaining: 25800 }}
        recentJobs={[{ state: "UPLOADED", ttsChars: 1290 }]}
      />,
    );
    expect(el.textContent).toContain("ElevenLabs tháng này");
    expect(el.textContent).toContain("4.200 / 30.000 credit");
    const bar = el.querySelector("[role=progressbar]");
    expect(bar?.getAttribute("aria-valuenow")).toBe("14");
    expect(el.textContent).toContain("25.800");
    expect(el.textContent).toContain("≈ 20 video"); // 25800 / 1290
  });

  it("tính theo credit / ký tự từ server", () => {
    const el = render(
      <TtsUsageCard
        usage={{ available: true, provider: "ELEVENLABS", used: 4200, limit: 30000, remaining: 25800, creditsPerChar: 0.5 }}
        recentJobs={[{ state: "UPLOADED", ttsChars: 1290, ttsProvider: "ELEVENLABS" }]}
      />,
    );
    expect(el.textContent).toContain("≈ 40 video"); // 25800 / (1290 × 0,5)
    expect(el.textContent).toContain("0,5 credit / ký tự");
  });

  it("chưa có job UPLOADED → dùng 1.200 ký tự / video", () => {
    const el = render(
      <TtsUsageCard
        usage={{ available: true, provider: "ELEVENLABS", used: 0, limit: 30000, remaining: 30000 }}
        recentJobs={[]}
      />,
    );
    expect(el.textContent).toContain("≈ 25 video");
    expect(el.textContent).toContain("1.200");
  });

  it("không khả dụng → hiện lý do, không có thanh tiến độ", () => {
    const el = render(
      <TtsUsageCard
        usage={{ available: false, provider: "ELEVENLABS", reason: "Chưa cấu hình API key ElevenLabs" }}
        recentJobs={[]}
      />,
    );
    expect(el.textContent).toContain("Không lấy được quota");
    expect(el.textContent).toContain("Chưa cấu hình API key ElevenLabs");
    expect(el.querySelector("[role=progressbar]")).toBeNull();
  });

  it("đang tải lần đầu", () => {
    const el = render(<TtsUsageCard usage={null} recentJobs={[]} />);
    expect(el.textContent).toContain("Đang tải");
  });
});
