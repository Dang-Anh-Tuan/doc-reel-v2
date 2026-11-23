import type { Scene, SceneType, ThemeName, VideoMeta } from "@/domain/script";
import type { TemplateModel } from "@/lib/thymeleaf/render";

/** Một khung hình cần render: 1 scene × 1 frame. */
export interface Slide {
  /** Duy nhất trong nguồn đang xem (scene + frame). */
  key: string;
  /** Khóa của scene (2 frame của quiz dùng chung) — để lưu JSON đã sửa. */
  sceneKey: string;
  type: SceneType;
  video: VideoMeta;
  scene: Scene;
  index: number;
  total: number;
  frame: number | null;
}

export type SlideState = "rendering" | "ready" | "error";

export interface SlideStatus {
  state: SlideState;
  /** Giá trị `body[data-problems]` do slide.js ghi. */
  problems: string[];
  /** Cảnh báo của bộ render Thymeleaf (thuộc tính không hỗ trợ, biểu thức lỗi...). */
  warnings: string[];
  error?: string;
}

export interface SampleSource {
  id: string;
  label: string;
}

export type ViewMode = "grid" | "single";

export function buildModel(slide: Slide, themeOverride: ThemeName | null, sceneOverride?: Scene): TemplateModel {
  return {
    video: { ...slide.video, ...(themeOverride ? { theme: themeOverride } : {}) },
    scene: sceneOverride ?? slide.scene,
    index: slide.index,
    total: slide.total,
    frame: slide.frame,
  };
}

export function slideLabel(slide: Slide): string {
  const no = String(slide.index).padStart(2, "0");
  return `${no} · ${slide.type}${slide.frame ? ` · frame ${slide.frame}` : ""}`;
}

export function hasIssue(status: SlideStatus | undefined): boolean {
  return !!status && (status.state === "error" || status.problems.length > 0);
}
