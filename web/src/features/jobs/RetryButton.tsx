import { useState } from "react";
import { Button, Message } from "@/components/ui";
import type { JobState, JobSummary } from "@/domain/job";
import { type ApiError, toApiError } from "@/lib/api/client";
import { retryJob } from "./api";
import styles from "./RetryButton.module.css";

interface RetryButtonProps {
  jobId: string;
  state: JobState;
  /** Gọi sau khi server nhận retry (202) — trang cha tải lại job. */
  onRetried?: (job: JobSummary) => void;
}

/** Nút Retry: chỉ hiện khi job FAILED; khóa khi đang gửi; lỗi (vd 409 job vừa đổi) hiện title + detail. */
export function RetryButton({ jobId, state, onRetried }: RetryButtonProps) {
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<ApiError | undefined>();

  if (state !== "FAILED") return null;

  const submit = async () => {
    if (sending) return;
    setSending(true);
    setError(undefined);
    try {
      const job = await retryJob(jobId);
      onRetried?.(job);
    } catch (e) {
      setError(toApiError(e));
    } finally {
      setSending(false);
    }
  };

  return (
    <div className={styles.wrap}>
      <Button variant="primary" onClick={() => void submit()} disabled={sending} aria-busy={sending}>
        {sending ? "Đang gửi…" : "Retry"}
      </Button>
      {error && (
        <Message title={error.title} error>
          {error.detail && <p className={styles.detail}>{error.detail}</p>}
        </Message>
      )}
    </div>
  );
}
