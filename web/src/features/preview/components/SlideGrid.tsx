import type { Scene, ThemeName } from "@/domain/script";
import { type Slide, type SlideStatus, buildModel, slideLabel } from "../model";
import { SLIDE_WIDTH, SlideFrame } from "./SlideFrame";
import { StatusBadge } from "./StatusBadge";
import styles from "./SlideGrid.module.css";

interface SlideGridProps {
  slides: Slide[];
  theme: ThemeName | null;
  overrides: Record<string, Scene>;
  scale: number;
  showSafeZone: boolean;
  reloadToken: number;
  statuses: Record<string, SlideStatus>;
  onStatus: (key: string, status: SlideStatus) => void;
  onOpen: (key: string) => void;
}

export function SlideGrid({ slides, theme, overrides, scale, showSafeZone, reloadToken, statuses, onStatus, onOpen }: SlideGridProps) {
  return (
    <div className={styles.grid} style={{ gridTemplateColumns: `repeat(auto-fill, minmax(${SLIDE_WIDTH * scale + 16}px, 1fr))` }}>
      {slides.map((slide) => (
        <figure key={slide.key} className={styles.tile}>
          <button type="button" className={styles.hit} onClick={() => onOpen(slide.key)} title="Mở slide này">
            <SlideFrame
              slide={slide}
              model={buildModel(slide, theme, overrides[slide.sceneKey])}
              scale={scale}
              showSafeZone={showSafeZone}
              reloadToken={reloadToken}
              onStatus={onStatus}
            />
          </button>
          <figcaption className={styles.caption}>
            <span>{slideLabel(slide)}</span>
            <StatusBadge status={statuses[slide.key]} />
          </figcaption>
        </figure>
      ))}
    </div>
  );
}
