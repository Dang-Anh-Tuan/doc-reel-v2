import { PageHeader } from "@/app/PageHeader";
import { Panel } from "@/components/ui";
import styles from "./JobsPage.module.css";

/** Trang quản trị job — bản 2 (SPEC mục 11 + backlog). Hiện chỉ mô tả phạm vi. */
export function JobsPage() {
  return (
    <>
      <PageHeader title="Jobs" description="Theo dõi và chạy lại job tạo video — làm ở bản 2, sau khi có REST API (story 4)." />
      <div className={styles.body}>
        <Panel title="Sẽ có">
          <ul className={styles.list}>
            <li>
              Danh sách job, lọc theo trạng thái — <code>GET /api/jobs?state=FAILED</code>
            </li>
            <li>
              Chi tiết job: bước hiện tại, lịch sử attempt, lỗi, link YouTube — <code>GET /api/jobs/{"{id}"}</code>
            </li>
            <li>
              Chạy lại job lỗi từ bước hỏng — <code>POST /api/jobs/{"{id}"}/retry</code>
            </li>
            <li>
              Gửi kịch bản JSON thủ công (kiểm tra trước bằng preview template) — <code>POST /api/jobs</code>
            </li>
            <li>Xem quota TTS trong tháng (ElevenLabs / Azure)</li>
          </ul>
        </Panel>
      </div>
    </>
  );
}
