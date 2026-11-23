import { useEffect, useEffectEvent, useRef } from "react";
import type { TemplateModel } from "@/lib/thymeleaf/render";
import { useRenderedSlide } from "../hooks";
import type { Slide, SlideStatus } from "../model";
import styles from "./SlideFrame.module.css";

export const SLIDE_WIDTH = 1080;
export const SLIDE_HEIGHT = 1920;
const SAFE_TOP = 220;
const SAFE_BOTTOM = 1500;
const GUTTER = 72;
const READY_TIMEOUT_MS = 8000;

interface SlideFrameProps {
  slide: Slide;
  model: TemplateModel;
  scale: number;
  showSafeZone: boolean;
  reloadToken: number;
  onStatus: (key: string, status: SlideStatus) => void;
}

/**
 * Render template vào iframe 1080×1920 (thu nhỏ bằng transform), chờ slide.js đặt
 * `body[data-ready]` rồi báo `data-problems` lên trên.
 */
export function SlideFrame({ slide, model, scale, showSafeZone, reloadToken, onStatus }: SlideFrameProps) {
  const rendered = useRenderedSlide(slide.type, model, reloadToken);
  const iframeRef = useRef<HTMLIFrameElement>(null);
  const pollRef = useRef(0);
  const report = useEffectEvent((status: SlideStatus) => onStatus(slide.key, status));

  const html = rendered.status === "done" ? rendered.data.html : "";
  const warnings = rendered.status === "done" ? rendered.data.warnings : null;
  const error = rendered.status === "error" ? rendered.error : null;

  useEffect(() => {
    if (error) report({ state: "error", problems: [], warnings: [], error });
    else report({ state: "rendering", problems: [], warnings: warnings ?? [] });
  }, [html, error, warnings]);

  useEffect(() => () => window.clearTimeout(pollRef.current), []);

  const handleLoad = () => {
    window.clearTimeout(pollRef.current);
    const startedAt = Date.now();
    const poll = () => {
      const body = iframeRef.current?.contentDocument?.body;
      if (body?.dataset.ready === "1") {
        const problems = (body.dataset.problems ?? "").split(",").filter(Boolean);
        onStatus(slide.key, { state: "ready", problems, warnings: warnings ?? [] });
      } else if (Date.now() - startedAt < READY_TIMEOUT_MS) {
        pollRef.current = window.setTimeout(poll, 60);
      } else {
        onStatus(slide.key, { state: "ready", problems: ["timeout: slide.js không đặt data-ready"], warnings: warnings ?? [] });
      }
    };
    poll();
  };

  const px = (v: number) => `${v * scale}px`;

  return (
    <div className={styles.frame} style={{ width: px(SLIDE_WIDTH), height: px(SLIDE_HEIGHT) }}>
      {error ? (
        <p className={styles.error}>{error}</p>
      ) : (
        html && (
          <iframe
            key={reloadToken}
            ref={iframeRef}
            className={styles.iframe}
            title={slide.key}
            srcDoc={html}
            width={SLIDE_WIDTH}
            height={SLIDE_HEIGHT}
            style={{ transform: `scale(${scale})` }}
            onLoad={handleLoad}
          />
        )
      )}
      {showSafeZone && (
        <div className={styles.safe} aria-hidden="true">
          <div className={styles.band} style={{ top: 0, height: px(SAFE_TOP) }}>
            <span>chrome · 0–{SAFE_TOP}</span>
          </div>
          <div className={`${styles.band} ${styles.bandBottom}`} style={{ top: px(SAFE_BOTTOM), height: px(SLIDE_HEIGHT - SAFE_BOTTOM) }}>
            <span>phụ đề + UI Shorts · {SAFE_BOTTOM}–{SLIDE_HEIGHT}</span>
          </div>
          <div className={styles.hLine} style={{ top: px(SAFE_TOP) }} />
          <div className={styles.hLine} style={{ top: px(SAFE_BOTTOM) }} />
          <div className={styles.vLine} style={{ left: px(GUTTER) }} />
          <div className={styles.vLine} style={{ left: px(SLIDE_WIDTH - GUTTER) }} />
        </div>
      )}
    </div>
  );
}
