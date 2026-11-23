import { useCallback, useMemo, useState } from "react";
import { Message } from "@/components/ui";
import type { Scene } from "@/domain/script";
import { PageHeader } from "@/app/PageHeader";
import { invalidateTemplates } from "./api";
import { PreviewToolbar } from "./components/PreviewToolbar";
import { SlideGrid } from "./components/SlideGrid";
import { SlideWorkspace } from "./components/SlideWorkspace";
import { useDevFileChanges, useSlides, useSources } from "./hooks";
import { type SlideStatus, hasIssue } from "./model";
import { usePreviewParams } from "./usePreviewParams";
import styles from "./PreviewPage.module.css";

const sameStatus = (a: SlideStatus | undefined, b: SlideStatus) =>
  !!a &&
  a.state === b.state &&
  a.error === b.error &&
  a.problems.join() === b.problems.join() &&
  a.warnings.join() === b.warnings.join();

export function PreviewPage() {
  if (!import.meta.env.DEV) {
    return (
      <>
        <PageHeader title="Template preview" />
        <Message title="Chỉ có khi chạy dev">
          <p>
            Trang này đọc template và mẫu trực tiếp từ repo qua dev server. Chạy <code>npm run dev</code> trong thư mục{" "}
            <code>web/</code>.
          </p>
        </Message>
      </>
    );
  }
  return <PreviewWorkbench />;
}

function PreviewWorkbench() {
  const [params, update] = usePreviewParams();
  const [reloadToken, setReloadToken] = useState(0);
  const [overrides, setOverrides] = useState<Record<string, Scene>>({});
  const [statuses, setStatuses] = useState<Record<string, SlideStatus>>({});

  const sources = useSources();
  const slides = useSlides(params.source, reloadToken);
  const slideList = useMemo(() => (slides.status === "done" ? slides.data : []), [slides]);

  const reload = useCallback(() => {
    invalidateTemplates();
    setReloadToken((t) => t + 1);
  }, []);
  useDevFileChanges(reload);

  const onStatus = useCallback((key: string, status: SlideStatus) => {
    setStatuses((prev) => (sameStatus(prev[key], status) ? prev : { ...prev, [key]: status }));
  }, []);

  const onOverride = useCallback((sceneKey: string, scene: Scene | null) => {
    setOverrides((prev) => {
      const next = { ...prev };
      if (scene) next[sceneKey] = scene;
      else delete next[sceneKey];
      return next;
    });
  }, []);

  const onSelect = useCallback((key: string) => update({ slide: key }), [update]);

  const summary = useMemo(() => {
    const done = slideList.map((s) => statuses[s.key]).filter((s) => s && s.state !== "rendering");
    return { checked: done.length, failing: done.filter(hasIssue).length, total: slideList.length };
  }, [slideList, statuses]);

  return (
    <div className={styles.page}>
      <PageHeader
        title="Template preview"
        description="Render 10 template Thymeleaf với file mẫu trong samples/ — tự tải lại khi sửa template, CSS, JS."
      />
      <PreviewToolbar
        params={params}
        sources={sources.status === "done" ? sources.data : []}
        summary={summary}
        onChange={update}
        onReload={reload}
      />
      <div className={styles.content}>
        {sources.status === "error" && <Message error title="Không đọc được danh sách mẫu">{sources.error}</Message>}
        {slides.status === "error" && <Message error title="Không đọc được mẫu">{slides.error}</Message>}
        {slides.status === "loading" && <Message title="Đang tải mẫu…" />}
        {slides.status === "done" && slideList.length === 0 && <Message title="Nguồn này không có slide nào" />}
        {slideList.length > 0 &&
          (params.view === "grid" ? (
            <SlideGrid
              slides={slideList}
              theme={params.theme}
              overrides={overrides}
              scale={params.gridScale}
              showSafeZone={params.safeZone}
              reloadToken={reloadToken}
              statuses={statuses}
              onStatus={onStatus}
              onOpen={(key) => update({ view: "single", slide: key })}
            />
          ) : (
            <SlideWorkspace
              slides={slideList}
              currentKey={params.slide}
              onSelect={onSelect}
              theme={params.theme}
              overrides={overrides}
              onOverride={onOverride}
              showSafeZone={params.safeZone}
              reloadToken={reloadToken}
              statuses={statuses}
              onStatus={onStatus}
            />
          ))}
      </div>
    </div>
  );
}
