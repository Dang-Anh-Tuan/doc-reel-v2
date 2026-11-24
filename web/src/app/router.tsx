import { Link, Navigate, createBrowserRouter } from "react-router";
import { Message } from "@/components/ui";
import { JobDetailPage } from "@/features/jobs/JobDetailPage";
import { JobsPage } from "@/features/jobs/JobsPage";
import { PreviewPage } from "@/features/preview/PreviewPage";
import { AppShell } from "./AppShell";

export const router = createBrowserRouter(
  [
    {
      path: "/",
      element: <AppShell />,
      children: [
        // Dev: mở preview template; bản build (Spring phục vụ /admin) không có preview → vào Jobs
        { index: true, element: <Navigate to={import.meta.env.DEV ? "/preview" : "/jobs"} replace /> },
        { path: "preview", element: <PreviewPage /> },
        { path: "jobs", element: <JobsPage /> },
        { path: "jobs/:id", element: <JobDetailPage /> },
        {
          path: "*",
          element: (
            <Message title="Không có trang này">
              <Link to="/jobs">Về trang Jobs</Link>
            </Message>
          ),
        },
      ],
    },
  ],
  // Dev: "/", build: "/admin" (Spring Boot phục vụ static/admin)
  { basename: import.meta.env.BASE_URL.replace(/\/$/, "") || "/" },
);
