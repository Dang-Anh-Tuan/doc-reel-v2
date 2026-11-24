/**
 * Khớp DTO Java của package `com.tuan.docreel.job`: `JobSummary`, `JobDetail` (+ `Attempt`, `Slide`), `JobState`,
 * `JobStep`. Thời gian là chuỗi ISO-8601 (Instant).
 */

/** Thứ tự = thứ tự pipeline (SPEC mục 5). */
export const JOB_STATES = ["RECEIVED", "RENDERED", "VOICED", "ASSEMBLED", "UPLOADED", "FAILED"] as const;
export type JobState = (typeof JOB_STATES)[number];

export const JOB_STEPS = ["RENDER", "VOICE", "ASSEMBLE", "UPLOAD"] as const;
export type JobStep = (typeof JOB_STEPS)[number];

export type VideoType = "SLIDES" | "CHEATSHEET";

/** `job_attempt.outcome` do `PipelineWorker` ghi. */
export type AttemptOutcome = "SUCCESS" | "SKIPPED" | "RETRY" | "FAILED";

/** `JobSummary` — một dòng của `GET /api/jobs`. */
export interface JobSummary {
  id: string;
  topicId: string;
  /** Tiêu đề video từ kịch bản; null nếu không đọc được. */
  title: string | null;
  scriptVersion: number;
  videoType: VideoType;
  state: JobState;
  /** Bước đang / sẽ chạy (FAILED: bước đã hỏng); null khi UPLOADED. */
  currentStep: JobStep | null;
  lastSuccessfulStep: JobStep | null;
  errorMessage: string | null;
  ttsProvider: string | null;
  youtubeId: string | null;
  /** `https://youtu.be/{youtubeId}` khi đã upload. */
  youtubeUrl: string | null;
  createdAt: string;
  updatedAt: string;
}

/** `JobDetail.Attempt`. */
export interface JobAttempt {
  step: JobStep;
  attemptNo: number;
  outcome: AttemptOutcome;
  durationMs: number | null;
  error: string | null;
  startedAt: string;
  /** startedAt + durationMs; null nếu không có thời lượng. */
  finishedAt: string | null;
}

/** `JobDetail.Slide` — PNG đã render. */
export interface JobSlide {
  /** Số thứ tự từ 1 theo timeline. */
  index: number;
  /** Tên file không đuôi, vd `s09-quiz-reveal`. */
  name: string;
  /** `/api/jobs/{id}/slides/{index}.png` */
  url: string;
}

/** `JobDetail` — `GET /api/jobs/{id}`. */
export interface JobDetail {
  id: string;
  topicId: string;
  title: string | null;
  scriptVersion: number;
  videoType: VideoType;
  state: JobState;
  currentStep: JobStep | null;
  lastSuccessfulStep: JobStep | null;
  errorMessage: string | null;
  youtubeId: string | null;
  youtubeUrl: string | null;
  ttsProvider: string | null;
  sourceChannel: string;
  sourceRef: string | null;
  createdAt: string;
  updatedAt: string;
  attempts: JobAttempt[];
  slides: JobSlide[];
}

/** Trang danh sách: body là mảng, tổng/trang/cỡ đọc từ header `X-Total-Count`, `X-Page`, `X-Page-Size`. */
export interface JobPage {
  items: JobSummary[];
  total: number;
  page: number;
  size: number;
}

/** Trạng thái pipeline còn đang chạy (cần tự làm mới). */
export function isRunning(state: JobState): boolean {
  return state !== "UPLOADED" && state !== "FAILED";
}

export const JOBS_PATH = "/api/jobs";
