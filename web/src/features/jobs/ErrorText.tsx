import { useId, useState } from "react";
import styles from "./ErrorText.module.css";
import { errorHeadline, isLongError } from "./model";

interface ErrorTextProps {
  text: string;
  className?: string;
}

/**
 * Lỗi gọn: lỗi ngắn một dòng hiện nguyên; lỗi dài / nhiều dòng mặc định chỉ hiện dòng có ý nghĩa nhất (cắt bằng
 * ellipsis) + nút "Xem chi tiết" mở toàn văn (font mono, giữ xuống dòng).
 */
export function ErrorText({ text, className }: ErrorTextProps) {
  const [open, setOpen] = useState(false);
  const id = useId();
  const cls = className ? `${styles.box} ${className}` : styles.box;

  if (!isLongError(text)) {
    return (
      <pre className={`${cls} ${styles.full}`} role="note">
        {text}
      </pre>
    );
  }
  return (
    <div className={cls} role="note" data-open={open}>
      <div className={styles.row}>
        {!open && (
          <span className={styles.headline} title={errorHeadline(text)}>
            {errorHeadline(text)}
          </span>
        )}
        <button
          type="button"
          className={styles.toggle}
          aria-expanded={open}
          aria-controls={id}
          onClick={() => setOpen((v) => !v)}
        >
          {open ? "Thu gọn" : "Xem chi tiết"}
        </button>
      </div>
      {/* Luôn có trong DOM để aria-controls trỏ đúng phần tử; thu gọn = hidden */}
      <pre id={id} className={styles.full} hidden={!open}>
        {text}
      </pre>
    </div>
  );
}
