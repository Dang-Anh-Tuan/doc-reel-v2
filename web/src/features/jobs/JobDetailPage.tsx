import { Link, useParams } from "react-router";
import { PageHeader } from "@/app/PageHeader";
import { Panel } from "@/components/ui";
import styles from "./JobsPage.module.css";

/** Chi tiết job `/jobs/:id` — trang giữ chỗ, P13 làm nội dung (dòng thời gian, slide, Retry). */
export function JobDetailPage() {
  const { id = "" } = useParams();
  return (
    <>
      <PageHeader title="Chi tiết job" description={id} />
      <div className={styles.body}>
        <Panel title="Sẽ có (P13)">
          <ul className={styles.list}>
            <li>
              Dòng thời gian các bước + attempt — <code>GET /api/jobs/{"{id}"}</code>
            </li>
            <li>Slide PNG đã render, link YouTube</li>
            <li>
              Nút Retry khi FAILED — <code>POST /api/jobs/{"{id}"}/retry</code>
            </li>
          </ul>
          <p>
            <Link to="/jobs">← Danh sách job</Link>
          </p>
        </Panel>
      </div>
    </>
  );
}
