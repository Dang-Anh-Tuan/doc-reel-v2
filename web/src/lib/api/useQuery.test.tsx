import { act } from "react";
import { type Root, createRoot } from "react-dom/client";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "./client";
import { type QueryOptions, type QueryResult, useQuery } from "./useQuery";

declare global {
  var IS_REACT_ACT_ENVIRONMENT: boolean | undefined;
}

/** renderHook tối giản (không cần @testing-library). */
function renderQuery<T>(
  key: string | null,
  fetcher: (signal: AbortSignal) => Promise<T>,
  options?: QueryOptions,
) {
  const latest: { current: QueryResult<T> | undefined } = { current: undefined };
  function Probe() {
    latest.current = useQuery(key, fetcher, options);
    return null;
  }
  const container = document.createElement("div");
  let root: Root | undefined;
  act(() => {
    root = createRoot(container);
    root.render(<Probe />);
  });
  return {
    result: latest,
    unmount: () => act(() => root?.unmount()),
  };
}

/** Chạy hết microtask (promise của fetcher) trong act. */
async function flush() {
  await act(async () => {
    await Promise.resolve();
  });
}

async function advance(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

beforeEach(() => {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true;
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
});

describe("useQuery", () => {
  it("tải dữ liệu: loading → data", async () => {
    const fetcher = vi.fn(async () => ({ ok: true }));
    const { result, unmount } = renderQuery("k", fetcher);

    expect(result.current?.loading).toBe(true);
    await flush();

    expect(result.current?.loading).toBe(false);
    expect(result.current?.data).toEqual({ ok: true });
    expect(result.current?.error).toBeUndefined();
    unmount();
  });

  it("lỗi → error là ApiError, giữ data cũ", async () => {
    const boom = new ApiError({ kind: "network", status: null, title: "Mất kết nối", detail: "x" });
    const fetcher = vi.fn<(s: AbortSignal) => Promise<number>>().mockResolvedValueOnce(1).mockRejectedValueOnce(boom);
    const { result, unmount } = renderQuery("k", fetcher, { refreshSeconds: 5 });
    await flush();
    expect(result.current?.data).toBe(1);

    await advance(5000);

    expect(result.current?.error).toBe(boom);
    expect(result.current?.data).toBe(1);
    expect(result.current?.loading).toBe(false);
    unmount();
  });

  it("làm mới mỗi N giây", async () => {
    let n = 0;
    const fetcher = vi.fn(async () => ++n);
    const { result, unmount } = renderQuery("k", fetcher, { refreshSeconds: 10 });
    await flush();
    expect(fetcher).toHaveBeenCalledTimes(1);

    await advance(9_999);
    expect(fetcher).toHaveBeenCalledTimes(1);

    await advance(1);
    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(result.current?.data).toBe(2);

    await advance(10_000);
    expect(fetcher).toHaveBeenCalledTimes(3);
    expect(result.current?.data).toBe(3);
    unmount();
  });

  it("dừng làm mới và hủy request khi unmount", async () => {
    const signals: AbortSignal[] = [];
    const fetcher = vi.fn(async (signal: AbortSignal) => {
      signals.push(signal);
      return "x";
    });
    const { unmount } = renderQuery("k", fetcher, { refreshSeconds: 3 });
    await flush();
    await advance(3000);
    expect(fetcher).toHaveBeenCalledTimes(2);

    unmount();
    await advance(60_000);

    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(signals.at(-1)?.aborted).toBe(true);
  });

  it("refresh() gọi lại ngay", async () => {
    const fetcher = vi.fn(async () => "x");
    const { result, unmount } = renderQuery("k", fetcher);
    await flush();

    act(() => result.current?.refresh());
    await flush();

    expect(fetcher).toHaveBeenCalledTimes(2);
    unmount();
  });

  it("key = null → không tải", async () => {
    const fetcher = vi.fn(async () => "x");
    const { result, unmount } = renderQuery(null, fetcher, { refreshSeconds: 1 });
    await advance(5000);

    expect(fetcher).not.toHaveBeenCalled();
    expect(result.current?.loading).toBe(false);
    unmount();
  });
});
