import { useState } from "react";
import { Button, Panel } from "@/components/ui";
import type { Scene } from "@/domain/script";
import styles from "./SlideWorkspace.module.css";

interface SceneJsonEditorProps {
  original: Scene;
  override: Scene | undefined;
  onChange: (scene: Scene | null) => void;
}

const pretty = (value: unknown) => JSON.stringify(value, null, 2);

/** Sửa JSON của scene ngay trong trình duyệt để thử giới hạn chữ (không ghi file). Remount theo scene bằng `key`. */
export function SceneJsonEditor({ original, override, onChange }: SceneJsonEditorProps) {
  const [text, setText] = useState(() => pretty(override ?? original));
  const [error, setError] = useState<string | null>(null);

  const handleChange = (value: string) => {
    setText(value);
    try {
      const parsed: unknown = JSON.parse(value);
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("scene phải là object");
      setError(null);
      onChange(parsed as Scene);
    } catch (err) {
      setError((err as Error).message);
    }
  };

  const reset = () => {
    setText(pretty(original));
    setError(null);
    onChange(null);
  };

  return (
    <Panel
      title="scene JSON"
      className={styles.editorPanel}
      bodyClassName={styles.editorBody}
      actions={
        <Button variant="ghost" disabled={!override} onClick={reset}>
          Khôi phục
        </Button>
      }
    >
      <textarea
        className={styles.textarea}
        spellCheck={false}
        value={text}
        onChange={(e) => handleChange(e.target.value)}
        aria-label="JSON của scene"
        aria-invalid={!!error}
      />
      {error && <p className={styles.editorError}>JSON lỗi: {error}</p>}
      <p className={styles.hint}>Sửa trực tiếp để thử giới hạn chữ. Chỉ đổi trong trình duyệt, không ghi file.</p>
    </Panel>
  );
}
