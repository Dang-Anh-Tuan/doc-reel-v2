import { Badge } from "@/components/ui";
import type { SlideStatus } from "../model";

export function StatusBadge({ status }: { status: SlideStatus | undefined }) {
  if (!status) return <Badge>chưa render</Badge>;
  if (status.state === "error") return <Badge tone="bad" title={status.error}>lỗi render</Badge>;
  if (status.state === "rendering") return <Badge>đang render</Badge>;
  if (status.problems.length > 0) {
    const text = status.problems.join(", ");
    return (
      <Badge tone="bad" title={text}>
        tràn: {text}
      </Badge>
    );
  }
  if (status.warnings.length > 0) {
    return (
      <Badge tone="warn" title={status.warnings.join("\n")}>
        {status.warnings.length} cảnh báo
      </Badge>
    );
  }
  return <Badge tone="ok">OK</Badge>;
}
