import { Link, useParams } from "react-router";
import { PageHeader } from "@/app/PageHeader";
import { Button, Message, Panel } from "@/components/ui";
import { type JobDetail, isRunning } from "@/domain/job";
import { useQuery } from "@/lib/api/useQuery";
import { fetchJob } from "./api";
import { CostPanel } from "./CostPanel";
import { ErrorText } from "./ErrorText";
import { JobStateBadge } from "./JobStateBadge";
import { JobTimeline } from "./JobTimeline";
import styles from "./JobsPage.module.css";
import { formatTime, stepLabel } from "./model";
import { RetryButton } from "./RetryButton";

/** Job đang chạy làm mới nhanh hơn trang danh sách để thấy bước nhảy. */
const RUNNING_REFRESH_SECONDS = 5;

/** Chi tiết job `/jobs/:id`: tổng quan, link YouTube, dòng thời gian attempt, slide PNG, Retry khi FAILED. */
export function JobDetailPage() {
  const { id = "" } = useParams();
  const { data: job, error, loading, refresh } = useQuery(
    id ? `job:${id}` : null,
    (signal) => fetchJob(id, signal),
    // Đang chạy → tự làm mới; xong / lỗi / chưa tải được → dừng (Retry và nút Tải lại gọi refresh)
    { refreshSeconds: (j) => (j && isRunning(j.state) ? RUNNING_REFRESH_SECONDS : 0) },
  );

  return (
    <>
      <PageHeader
        title={job?.title ?? "Chi tiết job"}
        description={job ? `${job.topicId} v${job.scriptVersion} · ${job.id}` : id}
        actions={
          <div className={styles.toolbar}>
            {job && <RetryButton jobId={job.id} state={job.state} onRetried={refresh} />}
            <Button onClick={refresh} disabled={loading}>
              {loading ? "Đang tải…" : "Tải lại"}
            </Button>
          </div>
        }
      />
      <div className={styles.wide}>
        <p className={styles.back}>
          <Link to="/jobs">← Danh sách job</Link>
        </p>
        {error && (
          <Message title={error.title} error>
            {error.detail && <p>{error.detail}</p>}
          </Message>
        )}
        {!job && loading && <p className={styles.muted}>Đang tải…</p>}
        {job && <JobBody job={job} />}
      </div>
    </>
  );
}

function JobBody({ job }: { job: JobDetail }) {
  return (
    <div className={styles.detailGrid}>
      {job.youtubeUrl && (
        <a className={styles.youtube} href={job.youtubeUrl} target="_blank" rel="noopener noreferrer">
          <span className={styles.youtubeLabel}>Đã đăng lên YouTube</span>
          <span className={styles.youtubeUrl}>{job.youtubeUrl} ↗</span>
        </a>
      )}

      <Panel title="Tổng quan">
        <dl className={styles.facts}>
          <dt>Trạng thái</dt>
          <dd>
            <JobStateBadge state={job.state} />
          </dd>
          <dt>{job.state === "FAILED" ? "Bước lỗi" : "Bước hiện tại"}</dt>
          <dd>{job.state === "UPLOADED" ? "Xong" : stepLabel(job.currentStep)}</dd>
          <dt>Giọng đọc</dt>
          <dd>{job.ttsProvider ?? "—"}</dd>
          <dt>Nguồn</dt>
          <dd>
            {job.sourceChannel}
            {job.sourceRef ? ` · ${job.sourceRef}` : ""}
          </dd>
          <dt>Tạo lúc</dt>
          <dd>{formatTime(job.createdAt)}</dd>
          <dt>Cập nhật</dt>
          <dd>{formatTime(job.updatedAt)}</dd>
        </dl>
        {job.state === "FAILED" && job.errorMessage && (
          <Message title="Lỗi" error>
            <ErrorText text={job.errorMessage} />
          </Message>
        )}
      </Panel>

      <Panel title="Dòng thời gian">
        <JobTimeline attempts={job.attempts} runningStep={isRunning(job.state) ? job.currentStep : null} />
      </Panel>

      <CostPanel cost={job.cost} done={job.state === "UPLOADED"} />

      <Panel title={`Slide đã render (${job.slides.length})`} className={styles.full}>
        {job.slides.length === 0 ? (
          <p className={styles.muted}>Chưa có slide nào (bước render chưa chạy hoặc file đã bị dọn).</p>
        ) : (
          <ul className={styles.slides}>
            {job.slides.map((s) => (
              <li key={s.index}>
                <a href={s.url} target="_blank" rel="noopener noreferrer" className={styles.slide}>
                  <img src={s.url} alt={`Slide ${s.index}: ${s.name}`} loading="lazy" />
                  <span>
                    {s.index}. {s.name}
                  </span>
                </a>
              </li>
            ))}
          </ul>
        )}
      </Panel>
    </div>
  );
}
