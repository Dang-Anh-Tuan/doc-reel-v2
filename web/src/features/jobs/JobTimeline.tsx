import { Badge } from "@/components/ui";
import type { JobAttempt, JobStep } from "@/domain/job";
import styles from "./JobTimeline.module.css";
import { OUTCOME_META, formatDuration, formatTime, sortAttempts, stepLabel } from "./model";

interface JobTimelineProps {
  attempts: readonly JobAttempt[];
  /** Bước đang chạy (job chưa xong, chưa lỗi) → thêm mục "đang chạy" ở cuối. */
  runningStep?: JobStep | null;
}

/** Dòng thời gian các lần chạy bước, cũ → mới; lỗi hiện nguyên văn. */
export function JobTimeline({ attempts, runningStep }: JobTimelineProps) {
  const sorted = sortAttempts(attempts);
  if (sorted.length === 0 && !runningStep) {
    return <p className={styles.empty}>Chưa có bước nào chạy.</p>;
  }
  return (
    <ol className={styles.timeline} aria-label="Dòng thời gian các bước">
      {sorted.map((a) => {
        const meta = OUTCOME_META[a.outcome] ?? { label: a.outcome, tone: "neutral" as const };
        return (
          <li
            key={`${a.step}-${a.attemptNo}-${a.startedAt}`}
            className={styles.item}
            data-outcome={a.outcome}
            data-testid="attempt"
          >
            <span className={styles.dot} aria-hidden="true" />
            <div className={styles.head}>
              <strong className={styles.step}>{stepLabel(a.step)}</strong>
              <span className={styles.no}>lần {a.attemptNo}</span>
              <Badge tone={meta.tone}>{meta.label}</Badge>
            </div>
            <div className={styles.meta}>
              <time dateTime={a.startedAt}>{formatTime(a.startedAt)}</time>
              <span>· {formatDuration(a.durationMs)}</span>
            </div>
            {a.error && (
              <pre className={styles.error} role="note">
                {a.error}
              </pre>
            )}
          </li>
        );
      })}
      {runningStep && (
        <li className={styles.item} data-outcome="RUNNING">
          <span className={styles.dot} aria-hidden="true" />
          <div className={styles.head}>
            <strong className={styles.step}>{stepLabel(runningStep)}</strong>
            <Badge>đang chờ / đang chạy</Badge>
          </div>
        </li>
      )}
    </ol>
  );
}
