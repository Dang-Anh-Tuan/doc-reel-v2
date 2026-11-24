/**
 * Gọi REST của Spring Boot (SPEC mục 11). Dev đi qua proxy của Vite (/api → :8080), bản build cùng origin.
 * Lỗi luôn là {@link ApiError}: HTTP lỗi đọc từ ProblemDetail (RFC 9457), lỗi mạng → kind "network".
 * Hủy bằng AbortSignal thì ném lại nguyên AbortError (dùng {@link isAbortError} để bỏ qua).
 */

export type ApiErrorKind = "http" | "network" | "unexpected";

/** Lỗi theo trường, khớp `GlobalExceptionHandler.ApiFieldError` (Java). */
export interface ApiFieldError {
  field: string;
  message: string;
}

/** ProblemDetail của Spring (chỉ phần web dùng). */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  errors?: unknown;
  [extra: string]: unknown;
}

interface ApiErrorInit {
  kind: ApiErrorKind;
  status: number | null;
  title: string;
  detail: string;
  errors?: ApiFieldError[];
  problem?: ProblemDetail;
  cause?: unknown;
}

export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  /** Mã HTTP; null khi không có phản hồi (lỗi mạng). */
  readonly status: number | null;
  readonly title: string;
  readonly detail: string;
  readonly errors: ApiFieldError[];
  /** ProblemDetail gốc (có thể chứa trường thêm như `jobId` của 409). */
  readonly problem: ProblemDetail | undefined;

  constructor(init: ApiErrorInit) {
    super(init.detail ? `${init.title}: ${init.detail}` : init.title, { cause: init.cause });
    this.name = "ApiError";
    this.kind = init.kind;
    this.status = init.status;
    this.title = init.title;
    this.detail = init.detail;
    this.errors = init.errors ?? [];
    this.problem = init.problem;
  }
}

export function isAbortError(e: unknown): boolean {
  return e instanceof DOMException ? e.name === "AbortError" : e instanceof Error && e.name === "AbortError";
}

/** Đổi lỗi bất kỳ thành ApiError (giữ nguyên nếu đã là ApiError). */
export function toApiError(e: unknown): ApiError {
  if (e instanceof ApiError) return e;
  const detail = e instanceof Error ? e.message : String(e);
  return new ApiError({ kind: "unexpected", status: null, title: "Lỗi không lường trước", detail, cause: e });
}

export interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "DELETE";
  /** Đối tượng → gửi JSON; string/Blob/FormData → gửi nguyên. */
  body?: unknown;
  headers?: Record<string, string>;
  signal?: AbortSignal;
}

function isRawBody(body: unknown): body is BodyInit {
  return typeof body === "string" || body instanceof Blob || body instanceof FormData;
}

function isJson(res: Response): boolean {
  const type = res.headers.get("Content-Type") ?? "";
  return type.includes("application/json") || type.includes("+json");
}

function normalizeErrors(raw: unknown): ApiFieldError[] {
  if (!Array.isArray(raw)) return [];
  return raw.flatMap((item: unknown) => {
    if (typeof item !== "object" || item === null) return [];
    const { field, message } = item as Record<string, unknown>;
    return typeof field === "string" && typeof message === "string" ? [{ field, message }] : [];
  });
}

async function errorFromResponse(res: Response): Promise<ApiError> {
  let problem: ProblemDetail | undefined;
  let text = "";
  try {
    if (isJson(res)) {
      const parsed: unknown = await res.json();
      if (typeof parsed === "object" && parsed !== null) problem = parsed as ProblemDetail;
    } else {
      text = (await res.text()).trim();
    }
  } catch {
    // body hỏng → chỉ dựa vào mã HTTP
  }
  const fallbackTitle = `HTTP ${res.status}${res.statusText ? ` ${res.statusText}` : ""}`;
  return new ApiError({
    kind: "http",
    status: res.status,
    title: typeof problem?.title === "string" ? problem.title : fallbackTitle,
    detail: typeof problem?.detail === "string" ? problem.detail : text.slice(0, 500),
    errors: normalizeErrors(problem?.errors),
    problem,
  });
}

/** Dữ liệu kèm header phản hồi (vd phân trang `X-Total-Count`). */
export interface ApiResponse<T> {
  data: T;
  headers: Headers;
}

/** Như {@link apiFetch} nhưng trả thêm header của phản hồi. */
export async function apiFetchWithHeaders<T>(path: string, options: RequestOptions = {}): Promise<ApiResponse<T>> {
  const { method = "GET", body, signal } = options;
  const headers: Record<string, string> = { Accept: "application/json, application/problem+json", ...options.headers };
  let payload: BodyInit | undefined;
  if (body !== undefined) {
    if (isRawBody(body)) {
      payload = body;
    } else {
      payload = JSON.stringify(body);
      headers["Content-Type"] ??= "application/json";
    }
  }

  let res: Response;
  try {
    res = await fetch(path, { method, headers, body: payload, signal, cache: "no-store" });
  } catch (e) {
    if (isAbortError(e)) throw e;
    throw new ApiError({
      kind: "network",
      status: null,
      title: "Không kết nối được máy chủ",
      detail: e instanceof Error ? e.message : String(e),
      cause: e,
    });
  }

  if (!res.ok) throw await errorFromResponse(res);
  if (res.status === 204 || res.headers.get("Content-Length") === "0") return { data: undefined as T, headers: res.headers };
  if (!isJson(res)) return { data: (await res.text()) as T, headers: res.headers };
  try {
    return { data: (await res.json()) as T, headers: res.headers };
  } catch (e) {
    throw new ApiError({
      kind: "unexpected",
      status: res.status,
      title: "Phản hồi không đọc được",
      detail: e instanceof Error ? e.message : String(e),
      cause: e,
    });
  }
}

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  return (await apiFetchWithHeaders<T>(path, options)).data;
}

export const api = {
  get: <T>(path: string, options: Omit<RequestOptions, "method" | "body"> = {}) => apiFetch<T>(path, options),
  post: <T>(path: string, body?: unknown, options: Omit<RequestOptions, "method" | "body"> = {}) =>
    apiFetch<T>(path, { ...options, method: "POST", body }),
  put: <T>(path: string, body?: unknown, options: Omit<RequestOptions, "method" | "body"> = {}) =>
    apiFetch<T>(path, { ...options, method: "PUT", body }),
};
