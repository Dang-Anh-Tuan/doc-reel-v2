import { Panel } from "@/components/ui";
import type { TtsUsage } from "@/domain/tts";
import styles from "./Cost.module.css";
import { averageCharsPerVideo, formatNumber, formatTime, remainingVideos } from "./model";

interface TtsUsageCardProps {
  /** null = đang tải lần đầu (hoặc lỗi mạng tới server). */
  usage: TtsUsage | null;
  /** Job gần đây (mới nhất trước) để lấy trung bình ký tự / video. */
  recentJobs: ReadonlyArray<{ state: string; ttsChars?: number; ttsProvider?: string | null }>;
  className?: string;
}

/** Thẻ **ElevenLabs tháng này** ở trang Jobs: đã dùng / hạn mức, thanh tiến độ, còn làm được ≈ N video. */
export function TtsUsageCard({ usage, recentJobs, className }: TtsUsageCardProps) {
  return (
    <Panel title="ElevenLabs tháng này" className={className}>
      <UsageBody usage={usage} recentJobs={recentJobs} />
    </Panel>
  );
}

function UsageBody({ usage, recentJobs }: Omit<TtsUsageCardProps, "className">) {
  if (!usage) return <p className={styles.muted}>Đang tải…</p>;
  if (!usage.available || usage.used == null || usage.limit == null) {
    return (
      <p className={styles.muted}>
        Không lấy được quota{usage.reason ? `: ${usage.reason}` : "."}
      </p>
    );
  }
  const { used, limit } = usage;
  const remaining = usage.remaining ?? Math.max(0, limit - used);
  const percent = limit > 0 ? Math.min(100, Math.round((used / limit) * 100)) : 100;
  const tone = percent >= 95 ? "bad" : percent >= 80 ? "warn" : "ok";
  const avg = averageCharsPerVideo(recentJobs);
  const rate = usage.creditsPerChar ?? 1;

  return (
    <div className={styles.usage}>
      <p className={styles.usageLine}>
        <strong>
          {formatNumber(used)} / {formatNumber(limit)} credit
        </strong>
        <span className={styles.muted}>{percent}%</span>
      </p>
      <div
        className={styles.bar}
        data-tone={tone}
        role="progressbar"
        aria-label="Credit ElevenLabs đã dùng"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent}
      >
        <span style={{ width: `${percent}%` }} />
      </div>
      <p className={styles.usageLine}>
        <span>
          Còn {formatNumber(remaining)} credit ≈ {formatNumber(remainingVideos(remaining, avg, rate))} video
        </span>
        <span className={styles.muted}>
          TB {formatNumber(avg)} ký tự × {formatNumber(rate, 2)} credit / ký tự
        </span>
      </p>
      {usage.resetAt && <p className={styles.note}>Đặt lại quota: {formatTime(usage.resetAt)}</p>}
    </div>
  );
}
