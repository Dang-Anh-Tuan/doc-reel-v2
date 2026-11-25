import { Panel } from "@/components/ui";
import type { JobCost } from "@/domain/job";
import styles from "./Cost.module.css";
import { formatDuration, formatNumber, formatUsd } from "./model";

interface CostPanelProps {
  /** `JobDetail.cost`; thiếu khi server chưa có P13b. */
  cost: JobCost | null | undefined;
  /** Job đã UPLOADED → thời gian "nhận → xong"; chưa thì "nhận → hiện tại". */
  done: boolean;
  className?: string;
}

/** Panel **Chi phí (ước tính)** ở trang chi tiết job. */
export function CostPanel({ cost, done, className }: CostPanelProps) {
  return (
    <Panel title="Chi phí (ước tính)" className={className}>
      {!cost ? (
        <p className={styles.muted}>Chưa có dữ liệu chi phí.</p>
      ) : (
        <>
          <dl className={styles.facts}>
            <dt>Giọng</dt>
            <dd>{cost.ttsProvider ?? "—"}</dd>
            <dt>Ký tự gửi TTS</dt>
            <dd>{formatNumber(cost.ttsChars)}</dd>
            <dt>Credit</dt>
            <dd>≈ {formatNumber(cost.estimatedCredits, 1)}</dd>
            <dt>Chi phí</dt>
            <dd className={styles.money}>≈ {formatUsd(cost.estimatedUsd)}</dd>
            <dt>Độ dài audio</dt>
            <dd>{formatDuration(cost.audioMs)}</dd>
            <dt>Thời gian xử lý</dt>
            <dd>{formatDuration(cost.processingMs)}</dd>
            <dt>{done ? "Nhận → xong" : "Nhận → hiện tại"}</dt>
            <dd>{formatDuration(cost.wallMs)}</dd>
          </dl>
          <p className={styles.note}>
            Số liệu ước tính: ký tự thực gửi ElevenLabs (sau lexicon, không tính câu lấy từ cache) × đơn giá cấu hình.
            Credit thật do ElevenLabs tính có thể khác.
          </p>
        </>
      )}
    </Panel>
  );
}
