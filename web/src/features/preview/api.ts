/**
 * Đọc mẫu + template từ dev server (web/dev/docreelDevServer.ts).
 * Chỉ dùng ở `npm run dev`; bản build không có các đường dẫn này.
 */
import { type Scene, type SceneType, type VideoMeta, SCENE_TYPES, frameCount, isSceneType } from "@/domain/script";
import { type RenderResult, type TemplateModel, renderTemplate } from "@/lib/thymeleaf/render";
import type { SampleSource, Slide } from "./model";

/** Event HMR do dev server gửi khi template/static/samples đổi (trùng với web/dev/docreelDevServer.ts). */
export const DEV_CHANGED_EVENT = "docreel:changed";

async function getJson<T>(url: string): Promise<T> {
  const res = await fetch(url, { cache: "no-store" });
  if (!res.ok) throw new Error(`${url} → HTTP ${res.status}`);
  return (await res.json()) as T;
}

// ------------------------------------------------------------------ nguồn mẫu

export async function fetchSources(): Promise<SampleSource[]> {
  const list = await getJson<{ scenes: string[]; full: string[] }>("/__dev/samples");
  const sources: SampleSource[] = [];
  if (list.scenes.some((f) => f.endsWith(".ok.json"))) sources.push({ id: "scenes:ok", label: "10 mẫu · ok" });
  if (list.scenes.some((f) => f.endsWith(".overflow.json")))
    sources.push({ id: "scenes:overflow", label: "10 mẫu · overflow (phải báo lỗi)" });
  for (const file of list.full) sources.push({ id: `full:${file}`, label: `Bài đầy đủ · ${file}` });
  return sources;
}

interface SceneSample {
  video: VideoMeta;
  scene: Scene;
}

interface FullSample extends VideoMeta {
  scenes: Scene[];
}

function expandFrames(slides: Omit<Slide, "key" | "frame">[]): Slide[] {
  return slides.flatMap((s) => {
    const frames = frameCount(s.type);
    return Array.from({ length: frames }, (_, i) => ({
      ...s,
      key: frames > 1 ? `${s.sceneKey}#${i + 1}` : s.sceneKey,
      frame: frames > 1 ? i + 1 : null,
    }));
  });
}

export async function fetchSlides(sourceId: string): Promise<Slide[]> {
  const [kind, name = ""] = sourceId.split(":");

  if (kind === "scenes") {
    const loaded = await Promise.all(
      SCENE_TYPES.map((type) =>
        getJson<SceneSample>(`/samples/scenes/${type}.${name}.json`)
          .then((sample) => ({ type, sample }))
          .catch(() => null),
      ),
    );
    const found = loaded.filter((x): x is { type: SceneType; sample: SceneSample } => x !== null);
    return expandFrames(
      found.map(({ type, sample }, i) => ({
        sceneKey: `${sourceId}/${type}`,
        type,
        video: sample.video,
        scene: sample.scene,
        index: i + 1,
        total: found.length,
      })),
    );
  }

  const { scenes = [], ...video } = await getJson<FullSample>(`/samples/full/${encodeURIComponent(name)}`);
  return expandFrames(
    scenes.map((scene, i) => ({
      sceneKey: `${sourceId}/${scene.id || i}`,
      type: isSceneType(scene.type) ? scene.type : (String(scene.type) as SceneType),
      video,
      scene,
      index: i + 1,
      total: scenes.length,
    })),
  );
}

// ------------------------------------------------------------------ template

const textCache = new Map<string, Promise<string>>();

function loadText(url: string): Promise<string> {
  let pending = textCache.get(url);
  if (!pending) {
    pending = fetch(url, { cache: "no-store" }).then((res) => {
      if (!res.ok) throw new Error(`Không tải được ${url} (HTTP ${res.status})`);
      return res.text();
    });
    pending.catch(() => textCache.delete(url));
    textCache.set(url, pending);
  }
  return pending;
}

/** Xóa cache template — gọi khi file template đổi. */
export function invalidateTemplates(): void {
  textCache.clear();
}

export async function renderSlide(type: SceneType, model: TemplateModel): Promise<RenderResult> {
  if (!isSceneType(type)) throw new Error(`type "${type}" không có template`);
  const [template, layout] = await Promise.all([
    loadText(`/templates/scenes/${type}.html`),
    loadText("/templates/layout.html"),
  ]);
  return renderTemplate(template, layout, model, { baseHref: `${location.origin}/` });
}
