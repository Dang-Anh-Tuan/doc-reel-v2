import { Badge, Button, type Option, Segmented, Select, Toggle } from "@/components/ui";
import { THEMES } from "@/domain/script";
import type { SampleSource, ViewMode } from "../model";
import { GRID_SCALE, type PreviewParams } from "../usePreviewParams";
import styles from "./PreviewToolbar.module.css";

interface Summary {
  checked: number;
  failing: number;
  total: number;
}

interface PreviewToolbarProps {
  params: PreviewParams;
  sources: SampleSource[];
  summary: Summary;
  onChange: (patch: Partial<PreviewParams>) => void;
  onReload: () => void;
}

const VIEW_OPTIONS: ReadonlyArray<Option<ViewMode>> = [
  { value: "grid", label: "Lưới" },
  { value: "single", label: "Từng slide" },
];

const THEME_OPTIONS: ReadonlyArray<Option<string>> = [
  { value: "", label: "Theo JSON" },
  ...THEMES.map((t) => ({ value: t, label: t })),
];

export function PreviewToolbar({ params, sources, summary, onChange, onReload }: PreviewToolbarProps) {
  const sourceOptions = sources.map((s) => ({ value: s.id, label: s.label }));

  return (
    <div className={styles.toolbar}>
      <Select label="Nguồn" value={params.source} options={sourceOptions} onChange={(source) => onChange({ source, slide: null })} />
      <Select
        label="Theme"
        value={params.theme ?? ""}
        options={THEME_OPTIONS}
        onChange={(theme) => onChange({ theme: (theme || null) as PreviewParams["theme"] })}
      />
      <Segmented label="Chế độ xem" value={params.view} options={VIEW_OPTIONS} onChange={(view) => onChange({ view })} />
      {params.view === "grid" && (
        <label className={styles.range}>
          Cỡ
          <input
            type="range"
            min={GRID_SCALE.min}
            max={GRID_SCALE.max}
            step={GRID_SCALE.step}
            value={params.gridScale}
            onChange={(e) => onChange({ gridScale: Number(e.target.value) })}
          />
        </label>
      )}
      <Toggle label="Vùng an toàn" checked={params.safeZone} onChange={(safeZone) => onChange({ safeZone })} />
      <Button variant="ghost" onClick={onReload} title="Đọc lại template, CSS, JS và mẫu (tự chạy khi file đổi)">
        ↻ Tải lại
      </Button>

      <div className={styles.summary}>
        {summary.checked < summary.total ? (
          <Badge>
            Đã kiểm {summary.checked}/{summary.total}
            {summary.failing > 0 && ` · ${summary.failing} lỗi`}
          </Badge>
        ) : summary.failing > 0 ? (
          <Badge tone="bad">
            {summary.failing}/{summary.total} slide có lỗi
          </Badge>
        ) : (
          <Badge tone="ok">
            {summary.total}/{summary.total} OK
          </Badge>
        )}
      </div>
    </div>
  );
}
