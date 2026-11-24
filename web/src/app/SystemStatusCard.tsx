import { type CheckStatus, SYSTEM_CHECK_PATH, type SystemCheck } from "@/domain/system";
import { api } from "@/lib/api/client";
import { useQuery } from "@/lib/api/useQuery";
import { summarizeSystem } from "./systemStatus";
import styles from "./SystemStatusCard.module.css";

/** Server tự kiểm mỗi 15 phút; web đọc kết quả gần nhất mỗi 60 giây (rẻ, không chạy lại các mục). */
const REFRESH_SECONDS = 60;

const ITEM_TONE: Record<CheckStatus, string> = { OK: "ok", WARN: "warn", CRITICAL: "bad" };

const fetchCheck = (signal: AbortSignal) => api.get<SystemCheck>(SYSTEM_CHECK_PATH, { signal });

/** Thẻ **Hệ thống** trên thanh điều hướng (PLAN P12, mục 2.6). */
export function SystemStatusCard() {
  const { data, error, loading, refresh } = useQuery("system-check", fetchCheck, { refreshSeconds: REFRESH_SECONDS });
  const summary = summarizeSystem(data, error);

  return (
    <details className={styles.card} data-tone={summary.tone}>
      <summary className={styles.summary} title={summary.hint}>
        <span className={styles.dot} aria-hidden="true" />
        <span className={styles.text}>
          <span className={styles.caption}>Hệ thống</span>
          <span className={styles.label}>{summary.label}</span>
        </span>
      </summary>
      <div className={styles.body}>
        <p className={styles.hint}>{summary.hint}</p>
        {data && (
          <ul className={styles.items}>
            {data.items.map((item) => (
              <li key={item.name} data-tone={ITEM_TONE[item.status]} title={item.fix ?? undefined}>
                <span className={styles.itemDot} aria-hidden="true" />
                <strong>{item.name}</strong>
                {item.detail && <span className={styles.detail}>{item.detail}</span>}
              </li>
            ))}
          </ul>
        )}
        <div className={styles.footer}>
          {data && <time dateTime={data.checkedAt}>Kiểm lúc {new Date(data.checkedAt).toLocaleTimeString("vi-VN")}</time>}
          <button type="button" className={styles.reload} onClick={refresh} disabled={loading}>
            {loading ? "Đang tải…" : "Tải lại"}
          </button>
        </div>
      </div>
    </details>
  );
}
