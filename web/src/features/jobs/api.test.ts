import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchJob, fetchJobs } from "./api";

afterEach(() => vi.unstubAllGlobals());

function stubFetch(body: unknown, headers: Record<string, string> = {}) {
  const fetch = vi.fn(async () =>
    new Response(JSON.stringify(body), { status: 200, headers: { "Content-Type": "application/json", ...headers } }),
  );
  vi.stubGlobal("fetch", fetch);
  return fetch;
}

describe("jobs api", () => {
  it("fetchJobs gửi state/page/size và đọc tổng từ header", async () => {
    const fetch = stubFetch([{ id: "a" }, { id: "b" }], { "X-Total-Count": "42", "X-Page": "1", "X-Page-Size": "2" });

    const page = await fetchJobs({ state: "FAILED", page: 1, size: 2 });

    const url = new URL(String((fetch.mock.calls[0] as unknown[])[0]), "http://x");
    expect(url.pathname).toBe("/api/jobs");
    expect(url.searchParams.get("state")).toBe("FAILED");
    expect(url.searchParams.get("page")).toBe("1");
    expect(url.searchParams.get("size")).toBe("2");
    expect(page).toEqual({ items: [{ id: "a" }, { id: "b" }], total: 42, page: 1, size: 2 });
  });

  it("fetchJobs không lọc → không gửi state; thiếu header → suy từ dữ liệu", async () => {
    const fetch = stubFetch([{ id: "a" }]);

    const page = await fetchJobs({ page: 0 });

    const url = new URL(String((fetch.mock.calls[0] as unknown[])[0]), "http://x");
    expect(url.searchParams.has("state")).toBe(false);
    expect(page.total).toBe(1);
    expect(page.page).toBe(0);
    expect(page.size).toBe(20);
  });

  it("fetchJob mã hóa id trong URL", async () => {
    const fetch = stubFetch({ id: "x" });
    await fetchJob("a/../b");
    expect((fetch.mock.calls[0] as unknown[])[0]).toBe("/api/jobs/a%2F..%2Fb");
  });
});
