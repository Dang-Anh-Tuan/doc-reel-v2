import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError, api, apiFetch, isAbortError } from "./client";

function jsonResponse(body: unknown, status = 200, type = "application/json"): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": type } });
}

function mockFetch(impl: (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>) {
  const fn = vi.fn(impl);
  vi.stubGlobal("fetch", fn);
  return fn;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("client", () => {
  it("2xx → trả dữ liệu JSON", async () => {
    const fetchMock = mockFetch(async () => jsonResponse({ jobId: "abc" }, 201));

    const data = await api.post<{ jobId: string }>("/api/jobs", { topicId: "t1" });

    expect(data).toEqual({ jobId: "abc" });
    const [url, init] = fetchMock.mock.calls[0]!;
    expect(url).toBe("/api/jobs");
    expect(init?.method).toBe("POST");
    expect(init?.body).toBe(JSON.stringify({ topicId: "t1" }));
    expect((init?.headers as Record<string, string>)["Content-Type"]).toBe("application/json");
  });

  it("204 → undefined", async () => {
    mockFetch(async () => new Response(null, { status: 204 }));
    await expect(api.get("/api/x")).resolves.toBeUndefined();
  });

  it("4xx ProblemDetail → ApiError có title, detail, errors[]", async () => {
    mockFetch(async () =>
      jsonResponse(
        {
          type: "about:blank",
          title: "Kịch bản không hợp lệ",
          status: 400,
          detail: "2 lỗi",
          errors: [
            { field: "scenes[1].id", message: "trùng id" },
            { field: "title", message: "quá dài" },
            { bad: true },
          ],
        },
        400,
        "application/problem+json",
      ),
    );

    const error = await apiFetch("/api/jobs", { method: "POST", body: "{}" }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    const e = error as ApiError;
    expect(e.kind).toBe("http");
    expect(e.status).toBe(400);
    expect(e.title).toBe("Kịch bản không hợp lệ");
    expect(e.detail).toBe("2 lỗi");
    expect(e.errors).toEqual([
      { field: "scenes[1].id", message: "trùng id" },
      { field: "title", message: "quá dài" },
    ]);
  });

  it("409 giữ trường thêm của ProblemDetail", async () => {
    mockFetch(async () =>
      jsonResponse({ title: "Kịch bản đã được gửi", status: 409, detail: "trùng", jobId: "j1" }, 409, "application/problem+json"),
    );

    const e = (await api.post("/api/jobs", {}).catch((err: unknown) => err)) as ApiError;

    expect(e.status).toBe(409);
    expect(e.errors).toEqual([]);
    expect(e.problem?.jobId).toBe("j1");
  });

  it("lỗi không phải ProblemDetail → title theo mã HTTP", async () => {
    mockFetch(async () => new Response("Bad Gateway", { status: 502, statusText: "Bad Gateway" }));

    const e = (await api.get("/api/x").catch((err: unknown) => err)) as ApiError;

    expect(e.kind).toBe("http");
    expect(e.title).toBe("HTTP 502 Bad Gateway");
    expect(e.detail).toBe("Bad Gateway");
  });

  it("lỗi mạng → ApiError loại network", async () => {
    mockFetch(async () => {
      throw new TypeError("Failed to fetch");
    });

    const e = (await api.get("/api/system/check").catch((err: unknown) => err)) as ApiError;

    expect(e).toBeInstanceOf(ApiError);
    expect(e.kind).toBe("network");
    expect(e.status).toBeNull();
    expect(e.detail).toBe("Failed to fetch");
  });

  it("hủy bằng AbortSignal → ném lại AbortError, không phải ApiError", async () => {
    mockFetch(async () => {
      throw new DOMException("aborted", "AbortError");
    });

    const e = await api.get("/api/x", { signal: new AbortController().signal }).catch((err: unknown) => err);

    expect(e).not.toBeInstanceOf(ApiError);
    expect(isAbortError(e)).toBe(true);
  });

  it("2xx nhưng JSON hỏng → ApiError unexpected", async () => {
    mockFetch(async () => new Response("{oops", { status: 200, headers: { "Content-Type": "application/json" } }));

    const e = (await api.get("/api/x").catch((err: unknown) => err)) as ApiError;

    expect(e.kind).toBe("unexpected");
  });
});
