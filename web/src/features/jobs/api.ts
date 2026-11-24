import { JOBS_PATH, type JobDetail, type JobPage, type JobState, type JobSummary } from "@/domain/job";
import { api, apiFetchWithHeaders } from "@/lib/api/client";

export const PAGE_SIZE = 20;

export interface JobsQuery {
  /** undefined = mọi trạng thái. */
  state?: JobState;
  /** Đếm từ 0. */
  page: number;
  size?: number;
}

function intHeader(headers: Headers, name: string, fallback: number): number {
  const n = Number.parseInt(headers.get(name) ?? "", 10);
  return Number.isFinite(n) && n >= 0 ? n : fallback;
}

/** `GET /api/jobs?state=&page=&size=` — tổng số job đọc từ header `X-Total-Count`. */
export async function fetchJobs(query: JobsQuery, signal?: AbortSignal): Promise<JobPage> {
  const size = query.size ?? PAGE_SIZE;
  const params = new URLSearchParams({ page: String(query.page), size: String(size) });
  if (query.state) params.set("state", query.state);
  const { data, headers } = await apiFetchWithHeaders<JobSummary[]>(`${JOBS_PATH}?${params}`, { signal });
  const items = Array.isArray(data) ? data : [];
  return {
    items,
    total: intHeader(headers, "X-Total-Count", items.length),
    page: intHeader(headers, "X-Page", query.page),
    size: intHeader(headers, "X-Page-Size", size),
  };
}

export function fetchJob(id: string, signal?: AbortSignal): Promise<JobDetail> {
  return api.get<JobDetail>(`${JOBS_PATH}/${encodeURIComponent(id)}`, { signal });
}

/** `POST /api/jobs/{id}/retry` → 202 + JobSummary; 409 nếu job không ở FAILED. */
export function retryJob(id: string): Promise<JobSummary> {
  return api.post<JobSummary>(`${JOBS_PATH}/${encodeURIComponent(id)}/retry`);
}
