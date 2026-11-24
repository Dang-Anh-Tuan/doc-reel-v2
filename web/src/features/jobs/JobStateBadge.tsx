import type { JobState } from "@/domain/job";
import styles from "./JobStateBadge.module.css";
import { STATE_META } from "./model";

/** Nhãn trạng thái job, màu theo `STATE_META` (data-tone để CSS và test cùng đọc). */
export function JobStateBadge({ state }: { state: JobState }) {
  const meta = STATE_META[state] ?? { label: state, tone: "queued" as const };
  return (
    <span className={styles.badge} data-tone={meta.tone} data-state={state} title={state}>
      {meta.label}
    </span>
  );
}
