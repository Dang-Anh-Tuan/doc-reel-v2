import { useEffect, useEffectEvent, useRef, useState } from "react";
import type { SceneType } from "@/domain/script";
import type { RenderResult, TemplateModel } from "@/lib/thymeleaf/render";
import { DEV_CHANGED_EVENT, fetchSlides, fetchSources, renderSlide } from "./api";
import type { SampleSource, Slide } from "./model";

export type Async<T> = { status: "loading" } | { status: "error"; error: string } | { status: "done"; data: T };

/** Chạy hàm async mỗi khi `key` đổi; kết quả của key cũ bị bỏ qua. */
function useAsync<T>(key: string, run: () => Promise<T>): Async<T> {
  const [state, setState] = useState<{ key: string; value: Async<T> } | null>(null);
  const start = useEffectEvent(run);

  useEffect(() => {
    let alive = true;
    start()
      .then((data) => {
        if (alive) setState({ key, value: { status: "done", data } });
      })
      .catch((err: unknown) => {
        if (alive) setState({ key, value: { status: "error", error: (err as Error).message } });
      });
    return () => {
      alive = false;
    };
  }, [key]);

  return state && state.key === key ? state.value : { status: "loading" };
}

export function useSources(): Async<SampleSource[]> {
  return useAsync("sources", fetchSources);
}

export function useSlides(sourceId: string, reloadToken: number): Async<Slide[]> {
  return useAsync(`${sourceId}@${reloadToken}`, () => fetchSlides(sourceId));
}

export function useRenderedSlide(type: SceneType, model: TemplateModel, reloadToken: number): Async<RenderResult> {
  const modelJson = JSON.stringify(model);
  return useAsync(`${type}|${reloadToken}|${modelJson}`, () =>
    renderSlide(type, JSON.parse(modelJson) as TemplateModel),
  );
}

/** Gọi `onChange` khi dev server báo template/static/samples đổi (HMR). */
export function useDevFileChanges(onChange: (file: string) => void): void {
  const handle = useEffectEvent(onChange);
  useEffect(() => {
    const hot = import.meta.hot;
    if (!hot) return;
    const handler = (data: { file: string }) => handle(data.file);
    hot.on(DEV_CHANGED_EVENT, handler);
    return () => hot.off(DEV_CHANGED_EVENT, handler);
  }, []);
}

/** Kích thước phần tử (ResizeObserver). */
export function useElementSize<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [size, setSize] = useState({ width: 0, height: 0 });
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const observer = new ResizeObserver(([entry]) => {
      if (entry) setSize({ width: entry.contentRect.width, height: entry.contentRect.height });
    });
    observer.observe(el);
    return () => observer.disconnect();
  }, []);
  return [ref, size] as const;
}
