import { Link, Navigate, createBrowserRouter } from "react-router";
import { Message } from "@/components/ui";
import { JobsPage } from "@/features/jobs/JobsPage";
import { PreviewPage } from "@/features/preview/PreviewPage";
import { AppShell } from "./AppShell";

export const router = createBrowserRouter(
  [
    {
      path: "/",
      element: <AppShell />,
      children: [
        { index: true, element: <Navigate to="/preview" replace /> },
        { path: "preview", element: <PreviewPage /> },
        { path: "jobs", element: <JobsPage /> },
        {
          path: "*",
          element: (
            <Message title="Không có trang này">
              <Link to="/preview">Về trang Template</Link>
            </Message>
          ),
        },
      ],
    },
  ],
  // Dev: "/", build: "/admin" (Spring Boot phục vụ static/admin)
  { basename: import.meta.env.BASE_URL.replace(/\/$/, "") || "/" },
);
