import { Link } from "react-router";
import type { JobSummary } from "@/domain/job";
import { JobStateBadge } from "./JobStateBadge";
import styles from "./JobsTable.module.css";
import { formatTime, stepLabel } from "./model";

/** Bảng job: topic, tiêu đề (link chi tiết), trạng thái, bước hiện tại, giọng, thời gian, YouTube. */
export function JobsTable({ jobs }: { jobs: readonly JobSummary[] }) {
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
            <th scope="col">Tạo lúc</th>
            <th scope="col">Cập nhật</th>
            <th scope="col">YouTube</th>
          </tr>
        </thead>
        <tbody>
          {jobs.map((j) => (
            <tr key={j.id} data-state={j.state}>
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
              <td>
                <JobStateBadge state={j.state} />
              </td>
              <td>{j.state === "UPLOADED" ? "Xong" : stepLabel(j.currentStep)}</td>
              <td className={styles.mono}>{j.ttsProvider ?? "—"}</td>
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
