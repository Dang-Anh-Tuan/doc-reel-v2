import { act } from "react";
import { type Root, createRoot } from "react-dom/client";
import { MemoryRouter, Route, Routes, useLocation } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { JobState, JobSummary } from "@/domain/job";
import { JobsTable } from "./JobsTable";

declare global {
  var IS_REACT_ACT_ENVIRONMENT: boolean | undefined;
}

let root: Root | undefined;

beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
});

afterEach(() => {
  act(() => root?.unmount());
  root = undefined;
  vi.unstubAllGlobals();
});

function job(id: string, state: JobState): JobSummary {
  return {
    id,
    topicId: "R-09",
    title: `Job ${id}`,
    scriptVersion: 1,
    videoType: "SLIDES",
    state,
    currentStep: state === "FAILED" ? "VOICE" : null,
    lastSuccessfulStep: "RENDER",
    errorMessage: state === "FAILED" ? "ElevenLabs từ chối: HTTP 401" : null,
    ttsProvider: "ELEVENLABS",
    youtubeId: null,
    youtubeUrl: null,
    createdAt: "2026-11-24T15:57:47Z",
    updatedAt: "2026-11-24T15:57:58Z",
    ttsChars: 1200,
    estimatedUsd: 0.24,
  };
}

function Where() {
  return <output data-testid="path">{useLocation().pathname}</output>;
}

function render(jobs: JobSummary[], onRetried?: () => void): HTMLElement {
  const container = document.createElement("div");
  document.body.appendChild(container);
  act(() => {
    root = createRoot(container);
    root.render(
      <MemoryRouter initialEntries={["/jobs"]}>
        <Routes>
          <Route path="/jobs" element={<JobsTable jobs={jobs} onRetried={onRetried} />} />
          <Route path="/jobs/:id" element={<Where />} />
        </Routes>
      </MemoryRouter>,
    );
  });
  return container;
}

function rows(el: HTMLElement): [HTMLElement, ...HTMLElement[]] {
  const list = [...el.querySelectorAll<HTMLElement>("tbody tr")];
  if (list.length === 0) throw new Error("bảng không có dòng nào");
  return list as [HTMLElement, ...HTMLElement[]];
}

describe("JobsTable", () => {
  it("cột ≈ USD", () => {
    const el = render([job("a", "UPLOADED")]);
    expect(el.querySelector("thead")?.textContent).toContain("≈ USD");
    expect(rows(el)[0].textContent).toContain("0,24");
  });

  it("nút Retry chỉ có trên dòng FAILED", () => {
    const el = render([job("a", "FAILED"), job("b", "UPLOADED"), job("c", "RENDERED")]);
    const buttons = rows(el).map((r) => r.querySelector("button")?.textContent ?? null);
    expect(buttons).toEqual(["Retry", null, null]);
  });

  it("bấm vào ô bất kỳ trên dòng → mở chi tiết job", () => {
    const el = render([job("a", "UPLOADED")]);
    const timeCell = rows(el)[0].querySelector("time") as HTMLElement;

    act(() => timeCell.click());

    expect(el.querySelector("[data-testid=path]")?.textContent).toBe("/jobs/a");
  });

  it("bấm Retry trên dòng → gọi API, không chuyển trang, xong → onRetried", async () => {
    const fetch = vi.fn(() =>
      Promise.resolve(
        new Response(JSON.stringify({ ...job("a", "RECEIVED") }), {
          status: 202,
          headers: { "Content-Type": "application/json" },
        }),
      ),
    );
    vi.stubGlobal("fetch", fetch);
    const onRetried = vi.fn();
    const el = render([job("a", "FAILED")], onRetried);

    await act(async () => rows(el)[0].querySelector("button")?.click());

    expect(fetch).toHaveBeenCalledTimes(1);
    expect((fetch.mock.calls[0] as unknown as [string])[0]).toBe("/api/jobs/a/retry");
    expect(onRetried).toHaveBeenCalledTimes(1);
    expect(el.querySelector("[data-testid=path]")).toBeNull();
    expect(el.querySelector("table")).not.toBeNull();
  });
});
