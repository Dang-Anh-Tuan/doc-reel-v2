import type { MouseEvent } from "react";
import { Link, useNavigate } from "react-router";
import type { JobSummary } from "@/domain/job";
import { JobStateBadge } from "./JobStateBadge";
import styles from "./JobsTable.module.css";
import { formatTime, formatUsd, stepLabel } from "./model";
import { RetryButton } from "./RetryButton";

interface JobsTableProps {
  jobs: readonly JobSummary[];
  /** Gọi sau khi Retry một job FAILED ngay trên bảng — trang cha tải lại danh sách. */
  onRetried?: () => void;
}

/**
 * Bảng job: topic, tiêu đề (link chi tiết), trạng thái (+ Retry khi FAILED), bước hiện tại, giọng, ≈ USD, thời gian,
 * YouTube.
 * Bấm vào bất kỳ chỗ nào trên dòng → mở chi tiết (trừ khi bấm vào link / nút / đang bôi chọn chữ).
 */
export function JobsTable({ jobs, onRetried }: JobsTableProps) {
  const navigate = useNavigate();
  const openRow = (e: MouseEvent<HTMLTableRowElement>, id: string) => {
    if ((e.target as HTMLElement).closest("a, button")) return;
    if (window.getSelection()?.toString()) return;
    void navigate(`/jobs/${id}`);
  };

  return (
    <div className={styles.scroll}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th scope="col">Topic</th>
            <th scope="col">Tiêu đề</th>
            <th scope="col">Trạng thái</th>
            <th scope="col">Bước hiện tại</th>
            <th scope="col">Giọng</th>
            <th scope="col" className={styles.num} title="Chi phí TTS ước tính">
              ≈ USD
            </th>
            <th scope="col">Tạo lúc</th>
            <th scope="col">Cập nhật</th>
            <th scope="col">YouTube</th>
          </tr>
        </thead>
        <tbody>
          {jobs.map((j) => (
            <tr key={j.id} data-state={j.state} className={styles.row} onClick={(e) => openRow(e, j.id)}>
              <td className={styles.mono}>
                {j.topicId}
                <span className={styles.faint}> v{j.scriptVersion}</span>
              </td>
              <td className={styles.titleCell}>
                <Link to={`/jobs/${j.id}`} className={styles.title}>
                  {j.title ?? <em>(không có tiêu đề)</em>}
                </Link>
                {j.state === "FAILED" && j.errorMessage && (
                  <span className={styles.error} title={j.errorMessage}>
                    {j.errorMessage}
                  </span>
                )}
              </td>
              <td className={styles.stateCell}>
                <JobStateBadge state={j.state} />
                <RetryButton jobId={j.id} state={j.state} onRetried={onRetried} />
              </td>
              <td>{j.state === "UPLOADED" ? "Xong" : stepLabel(j.currentStep)}</td>
              <td className={styles.mono}>{j.ttsProvider ?? "—"}</td>
              <td className={styles.num} title={`${j.ttsChars ?? 0} ký tự gửi TTS`}>
                {j.ttsChars ? formatUsd(j.estimatedUsd).replace(" USD", "") : <span className={styles.faint}>—</span>}
              </td>
              <td className={styles.time}>
                <time dateTime={j.createdAt}>{formatTime(j.createdAt)}</time>
              </td>
              <td className={styles.time}>
                <time dateTime={j.updatedAt}>{formatTime(j.updatedAt)}</time>
              </td>
              <td>
                {j.youtubeUrl ? (
                  <a href={j.youtubeUrl} target="_blank" rel="noopener noreferrer" className={styles.yt}>
                    Mở ↗
                  </a>
                ) : (
                  <span className={styles.faint}>—</span>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
