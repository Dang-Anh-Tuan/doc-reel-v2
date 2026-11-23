import { useCallback } from "react";
import { useSearchParams } from "react-router";
import { type ThemeName, isThemeName } from "@/domain/script";
import type { ViewMode } from "./model";

export const DEFAULT_SOURCE = "scenes:ok";
export const GRID_SCALE = { min: 0.12, max: 0.4, step: 0.02, initial: 0.2 } as const;

export interface PreviewParams {
  source: string;
  /** null = dùng theme trong JSON. */
  theme: ThemeName | null;
  view: ViewMode;
  slide: string | null;
  safeZone: boolean;
  gridScale: number;
}

type Patch = Partial<PreviewParams>;

const clamp = (v: number, min: number, max: number) => Math.min(max, Math.max(min, v));

/** Trạng thái trang preview nằm trên URL → reload hay gửi link vẫn giữ nguyên. */
export function usePreviewParams(): [PreviewParams, (patch: Patch) => void] {
  const [search, setSearch] = useSearchParams();

  const theme = search.get("theme");
  const scale = Number(search.get("scale"));
  const params: PreviewParams = {
    source: search.get("source") ?? DEFAULT_SOURCE,
    theme: isThemeName(theme) ? theme : null,
    view: search.get("view") === "single" ? "single" : "grid",
    slide: search.get("slide"),
    safeZone: search.get("safe") === "1",
    gridScale: scale ? clamp(scale, GRID_SCALE.min, GRID_SCALE.max) : GRID_SCALE.initial,
  };

  const update = useCallback(
    (patch: Patch) => {
      setSearch(
        (prev) => {
          const next = new URLSearchParams(prev);
          const set = (key: string, value: string | null) => (value === null ? next.delete(key) : next.set(key, value));
          if ("source" in patch) set("source", patch.source === DEFAULT_SOURCE ? null : (patch.source ?? null));
          if ("theme" in patch) set("theme", patch.theme ?? null);
          if ("view" in patch) set("view", patch.view === "single" ? "single" : null);
          if ("slide" in patch) set("slide", patch.slide ?? null);
          if ("safeZone" in patch) set("safe", patch.safeZone ? "1" : null);
          if ("gridScale" in patch) set("scale", patch.gridScale ? String(patch.gridScale) : null);
          return next;
        },
        { replace: true },
      );
    },
    [setSearch],
  );

  return [params, update];
}
