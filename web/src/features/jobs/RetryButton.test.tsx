import { act } from "react";
import { type Root, createRoot } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { JOB_STATES, type JobState } from "@/domain/job";
import { RetryButton } from "./RetryButton";

declare global {
  var IS_REACT_ACT_ENVIRONMENT: boolean | undefined;
}

const JOB_ID = "6f1c2c1e-0000-4000-8000-000000000001";
let root: Root | undefined;

beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
});

afterEach(() => {
  act(() => root?.unmount());
  root = undefined;
  vi.unstubAllGlobals();
});

function render(state: JobState, onRetried?: () => void): HTMLElement {
  const container = document.createElement("div");
  act(() => {
    root = createRoot(container);
    root.render(<RetryButton jobId={JOB_ID} state={state} onRetried={onRetried} />);
  });
  return container;
}

function json(status: number, body: unknown, type = "application/json"): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": type } });
}

/** fetch treo tới khi test gọi resolve. */
function deferredFetch() {
  let resolve: (r: Response) => void = () => {};
  const fetch = vi.fn(() => new Promise<Response>((r) => (resolve = r)));
  vi.stubGlobal("fetch", fetch);
  return { fetch, resolve: (r: Response) => resolve(r) };
}

const button = (el: HTMLElement) => el.querySelector("button");

describe("RetryButton", () => {
  it.each(JOB_STATES.filter((s) => s !== "FAILED"))("ẩn khi job %s", (state) => {
    expect(button(render(state))).toBeNull();
  });

  it("hiện khi FAILED", () => {
    expect(button(render("FAILED"))?.textContent).toBe("Retry");
  });

  it("bấm → POST /api/jobs/{id}/retry, khóa nút khi đang gửi, xong → onRetried", async () => {
    const { fetch, resolve } = deferredFetch();
    const onRetried = vi.fn();
    const el = render("FAILED", onRetried);

    act(() => button(el)?.click());

    expect(fetch).toHaveBeenCalledTimes(1);
    const [url, init] = fetch.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe(`/api/jobs/${JOB_ID}/retry`);
    expect(init.method).toBe("POST");
    expect(button(el)?.disabled).toBe(true);
    expect(button(el)?.textContent).toBe("Đang gửi…");

    // Bấm lại khi đang gửi không gửi thêm
    act(() => button(el)?.click());
    expect(fetch).toHaveBeenCalledTimes(1);

    await act(async () => resolve(json(202, { id: JOB_ID, state: "RECEIVED" })));

    expect(onRetried).toHaveBeenCalledWith(expect.objectContaining({ id: JOB_ID, state: "RECEIVED" }));
    expect(button(el)?.disabled).toBe(false);
    expect(el.querySelector("[role=alert]")).toBeNull();
  });

  it("409 → hiện title + detail của ProblemDetail, mở khóa nút", async () => {
    const { resolve } = deferredFetch();
    const onRetried = vi.fn();
    const el = render("FAILED", onRetried);

    act(() => button(el)?.click());
    await act(async () =>
      resolve(
        json(
          409,
          { status: 409, title: "Không thể retry", detail: "Job đang ở RECEIVED, chỉ retry được job FAILED" },
          "application/problem+json",
        ),
      ),
    );

    const alert = el.querySelector("[role=alert]");
    expect(alert?.textContent).toContain("Không thể retry");
    expect(alert?.textContent).toContain("chỉ retry được job FAILED");
    expect(onRetried).not.toHaveBeenCalled();
    expect(button(el)?.disabled).toBe(false);
  });

  it("lỗi mạng → báo không kết nối được", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new TypeError("Failed to fetch"))));
    const el = render("FAILED");

    await act(async () => button(el)?.click());

    expect(el.querySelector("[role=alert]")?.textContent).toContain("Không kết nối được máy chủ");
  });
});
