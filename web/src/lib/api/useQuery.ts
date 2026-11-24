import { useCallback, useEffect, useRef, useState } from "react";
import { type ApiError, isAbortError, toApiError } from "./client";

export interface QueryOptions {
  /** Làm mới định kỳ: gọi lại sau N giây kể từ lần trước xong (không chồng request). 0/undefined → tắt. */
  refreshSeconds?: number;
}

export interface QueryResult<T> {
  data: T | undefined;
  error: ApiError | undefined;
  /** Đang có request chạy (kể cả lần làm mới; dữ liệu cũ vẫn giữ). */
  loading: boolean;
  /** Gọi lại ngay. */
  refresh: () => void;
}

interface State<T> {
  data: T | undefined;
  error: ApiError | undefined;
  loading: boolean;
}

/**
 * Hook tải dữ liệu nhỏ: `key` đổi → tải lại; `key = null` → không tải. Unmount hoặc đổi key → hủy request
 * (AbortSignal) và dừng hẹn giờ làm mới.
 */
export function useQuery<T>(
  key: string | null,
  fetcher: (signal: AbortSignal) => Promise<T>,
  options: QueryOptions = {},
): QueryResult<T> {
  const [state, setState] = useState<State<T>>({ data: undefined, error: undefined, loading: key !== null });
  const [tick, setTick] = useState(0);
  const fetcherRef = useRef(fetcher);
  const { refreshSeconds } = options;

  useEffect(() => {
    fetcherRef.current = fetcher;
  });

  useEffect(() => {
    if (key === null) return;
    let active = true;
    let controller: AbortController | undefined;
    let timer: ReturnType<typeof setTimeout> | undefined;

    const run = async () => {
      controller = new AbortController();
      setState((s) => (s.loading ? s : { ...s, loading: true }));
      try {
        const data = await fetcherRef.current(controller.signal);
        if (active) setState({ data, error: undefined, loading: false });
      } catch (e) {
        if (!active || isAbortError(e)) return;
        setState((s) => ({ data: s.data, error: toApiError(e), loading: false }));
      }
      if (active && refreshSeconds && refreshSeconds > 0) {
        timer = setTimeout(() => void run(), refreshSeconds * 1000);
      }
    };

    void run();
    return () => {
      active = false;
      controller?.abort();
      clearTimeout(timer);
    };
  }, [key, refreshSeconds, tick]);

  const refresh = useCallback(() => setTick((t) => t + 1), []);
  return { ...state, refresh };
}
