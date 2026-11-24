import { useCallback, useEffect, useRef, useState } from "react";
import { type ApiError, isAbortError, toApiError } from "./client";

export interface QueryOptions<T = unknown> {
  /**
   * Làm mới định kỳ: gọi lại sau N giây kể từ lần trước xong (không chồng request). 0/undefined → tắt.
   * Dạng hàm: tính lại sau mỗi lần tải từ data mới nhất (vd chỉ làm mới khi job còn chạy).
   */
  refreshSeconds?: number | ((data: T | undefined) => number);
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
  /** Key mà data/error thuộc về: key đổi → không trả data của key cũ (tránh hiện thoáng job trước). */
  key: string | null;
  data: T | undefined;
  error: ApiError | undefined;
  loading: boolean;
}

/**
 * Hook tải dữ liệu nhỏ: `key` đổi → bỏ data/error của key cũ và tải lại; `key = null` → không tải. Unmount hoặc đổi key → hủy request
 * (AbortSignal) và dừng hẹn giờ làm mới.
 */
export function useQuery<T>(
  key: string | null,
  fetcher: (signal: AbortSignal) => Promise<T>,
  options: QueryOptions<T> = {},
): QueryResult<T> {
  const [state, setState] = useState<State<T>>({ key, data: undefined, error: undefined, loading: key !== null });
  const [tick, setTick] = useState(0);
  const fetcherRef = useRef(fetcher);
  const refreshRef = useRef(options.refreshSeconds);
  // Dạng hàm: không đưa vào deps (hàm mới mỗi render) — chỉ đọc qua ref lúc hẹn giờ
  const refreshDep = typeof options.refreshSeconds === "function" ? "fn" : options.refreshSeconds;

  useEffect(() => {
    fetcherRef.current = fetcher;
    refreshRef.current = options.refreshSeconds;
  });

  useEffect(() => {
    if (key === null) return;
    let active = true;
    let controller: AbortController | undefined;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let lastData: T | undefined;

    const run = async () => {
      controller = new AbortController();
      setState((s) =>
        s.key !== key ? { key, data: undefined, error: undefined, loading: true } : s.loading ? s : { ...s, loading: true },
      );
      try {
        const data = await fetcherRef.current(controller.signal);
        if (!active) return;
        lastData = data;
        setState({ key, data, error: undefined, loading: false });
      } catch (e) {
        if (!active || isAbortError(e)) return;
        const error = toApiError(e);
        setState((s) => ({ key, data: s.key === key ? s.data : undefined, error, loading: false }));
      }
      const rs = refreshRef.current;
      const seconds = typeof rs === "function" ? rs(lastData) : rs;
      if (active && seconds && seconds > 0) {
        timer = setTimeout(() => void run(), seconds * 1000);
      }
    };

    void run();
    return () => {
      active = false;
      controller?.abort();
      clearTimeout(timer);
    };
  }, [key, refreshDep, tick]);

  const refresh = useCallback(() => setTick((t) => t + 1), []);
  // Render ngay sau khi key đổi (trước khi effect chạy) vẫn còn state của key cũ → không trả ra
  if (state.key !== key) return { data: undefined, error: undefined, loading: key !== null, refresh };
  return { data: state.data, error: state.error, loading: state.loading, refresh };
}
