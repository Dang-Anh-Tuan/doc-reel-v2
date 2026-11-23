import { useEffect } from "react";
import { Button, Panel } from "@/components/ui";
import type { Scene, ThemeName } from "@/domain/script";
import { renderSlide } from "../api";
import { useElementSize } from "../hooks";
import { type Slide, type SlideStatus, buildModel, slideLabel } from "../model";
import { SceneJsonEditor } from "./SceneJsonEditor";
import { SLIDE_HEIGHT, SLIDE_WIDTH, SlideFrame } from "./SlideFrame";
import { StatusBadge } from "./StatusBadge";
import styles from "./SlideWorkspace.module.css";

interface SlideWorkspaceProps {
  slides: Slide[];
  currentKey: string | null;
  onSelect: (key: string) => void;
  theme: ThemeName | null;
  overrides: Record<string, Scene>;
  onOverride: (sceneKey: string, scene: Scene | null) => void;
  showSafeZone: boolean;
  reloadToken: number;
  statuses: Record<string, SlideStatus>;
  onStatus: (key: string, status: SlideStatus) => void;
}

export function SlideWorkspace(props: SlideWorkspaceProps) {
  const { slides, currentKey, onSelect, theme, overrides, onOverride, showSafeZone, reloadToken, statuses, onStatus } = props;
  const index = Math.max(0, slides.findIndex((s) => s.key === currentKey));
  const slide = slides[index];
  const [viewerRef, viewer] = useElementSize<HTMLDivElement>();
  const scale = Math.max(0.15, Math.min(1, (viewer.height - 32) / SLIDE_HEIGHT, (viewer.width - 32) / SLIDE_WIDTH));

  const prev = slides[index - 1];
  const next = slides[index + 1];

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement;
      if (["TEXTAREA", "INPUT", "SELECT"].includes(target.tagName)) return;
      if (e.key === "ArrowLeft" && prev) onSelect(prev.key);
      if (e.key === "ArrowRight" && next) onSelect(next.key);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [prev, next, onSelect]);

  if (!slide) return null;
  const model = buildModel(slide, theme, overrides[slide.sceneKey]);
  const status = statuses[slide.key];

  const openFullSize = async () => {
    const { html } = await renderSlide(slide.type, model);
    window.open(URL.createObjectURL(new Blob([html], { type: "text/html" })), "_blank", "noopener");
  };

  return (
    <div className={styles.workspace}>
      <nav className={styles.list} aria-label="Danh sách slide">
        {slides.map((s) => (
          <button
            key={s.key}
            type="button"
            className={styles.listItem}
            aria-current={s.key === slide.key ? "true" : undefined}
            onClick={() => onSelect(s.key)}
          >
            <span>{slideLabel(s)}</span>
            <StatusBadge status={statuses[s.key]} />
          </button>
        ))}
      </nav>

      <div ref={viewerRef} className={styles.viewer}>
        {viewer.height > 0 && (
          <SlideFrame
            key={slide.key}
            slide={slide}
            model={model}
            scale={scale}
            showSafeZone={showSafeZone}
            reloadToken={reloadToken}
            onStatus={onStatus}
          />
        )}
      </div>

      <aside className={styles.side}>
        <Panel title={slideLabel(slide)} actions={<StatusBadge status={status} />}>
          <div className={styles.row}>
            <Button disabled={!prev} onClick={() => prev && onSelect(prev.key)}>
              ← Trước
            </Button>
            <Button disabled={!next} onClick={() => next && onSelect(next.key)}>
              Sau →
            </Button>
            <Button variant="ghost" onClick={openFullSize}>
              Mở 1080×1920 ↗
            </Button>
          </div>
          <dl className={styles.meta}>
            <dt>Template</dt>
            <dd>
              <code>templates/scenes/{slide.type}.html</code>
            </dd>
            <dt>Scene id</dt>
            <dd>
              <code>{slide.scene.id}</code>
            </dd>
            <dt>Tỉ lệ</dt>
            <dd>
              {Math.round(scale * 100)}% · <kbd>←</kbd> <kbd>→</kbd> để chuyển slide
            </dd>
          </dl>
          {status && status.problems.length > 0 && (
            <div className={styles.issues} data-tone="bad">
              <strong>data-problems</strong>
              <ul>
                {status.problems.map((p) => (
                  <li key={p}>{p}</li>
                ))}
              </ul>
            </div>
          )}
          {status && status.warnings.length > 0 && (
            <div className={styles.issues} data-tone="warn">
              <strong>Cảnh báo template</strong>
              <ul>
                {status.warnings.map((w) => (
                  <li key={w}>{w}</li>
                ))}
              </ul>
            </div>
          )}
          {status?.error && (
            <div className={styles.issues} data-tone="bad">
              <strong>Lỗi render</strong>
              <p>{status.error}</p>
            </div>
          )}
        </Panel>

        <SceneJsonEditor
          key={slide.sceneKey}
          original={slide.scene}
          override={overrides[slide.sceneKey]}
          onChange={(scene) => onOverride(slide.sceneKey, scene)}
        />
      </aside>
    </div>
  );
}
