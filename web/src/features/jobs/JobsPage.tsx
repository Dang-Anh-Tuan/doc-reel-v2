import { useState } from "react";
import { PageHeader } from "@/app/PageHeader";
import { Button, Message, Panel, Select } from "@/components/ui";
import { JOB_STATES, type JobState } from "@/domain/job";
import { useQuery } from "@/lib/api/useQuery";
import { PAGE_SIZE, fetchJobs } from "./api";
import { JobsTable } from "./JobsTable";
import styles from "./JobsPage.module.css";
import { STATE_META } from "./model";

type Filter = JobState | "ALL";

const FILTERS: ReadonlyArray<{ value: Filter; label: string }> = [
  { value: "ALL", label: "Tất cả" },
  ...JOB_STATES.map((s) => ({ value: s, label: STATE_META[s].label })),
];

/** Trang Jobs `/jobs`: bảng job, lọc theo trạng thái, phân trang, tự làm mới 10 s. */
export function JobsPage() {
  const [filter, setFilter] = useState<Filter>("ALL");
  const [page, setPage] = useState(0);
  const state = filter === "ALL" ? undefined : filter;

  const { data, error, loading, refresh } = useQuery(
    `jobs:${filter}:${page}`,
    (signal) => fetchJobs({ state, page, size: PAGE_SIZE }, signal),
    { refreshSeconds: 10 },
  );

  const pages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1;

  return (
    <>
      <PageHeader
        title="Jobs"
        description="Job tạo video mới nhất trước — tự làm mới mỗi 10 giây."
        actions={
          <div className={styles.toolbar}>
            <Select
              label="Trạng thái"
              value={filter}
              options={FILTERS}
              onChange={(v) => {
                setFilter(v);
                setPage(0);
              }}
            />
            <Button onClick={refresh} disabled={loading}>
              {loading ? "Đang tải…" : "Tải lại"}
            </Button>
          </div>
        }
      />
      <div className={styles.wide}>
        {error && (
          <Message title={error.title} error>
            {error.detail && <p>{error.detail}</p>}
          </Message>
        )}
        <Panel
          title={data ? `${data.total} job` : "Danh sách job"}
          actions={
            data && data.total > data.size ? (
              <div className={styles.pager}>
                <Button variant="ghost" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
                  ← Trước
                </Button>
                <span>
                  Trang {page + 1} / {pages}
                </span>
                <Button variant="ghost" disabled={page + 1 >= pages} onClick={() => setPage((p) => p + 1)}>
                  Sau →
                </Button>
              </div>
            ) : undefined
          }
        >
          {!data && loading && <p className={styles.muted}>Đang tải…</p>}
          {data && data.items.length === 0 && (
            <p className={styles.muted}>
              {filter === "ALL" ? "Chưa có job nào." : `Không có job ở trạng thái “${STATE_META[filter].label}”.`}
            </p>
          )}
          {data && data.items.length > 0 && <JobsTable jobs={data.items} />}
        </Panel>
      </div>
    </>
  );
}
