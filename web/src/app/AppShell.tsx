import { NavLink, Outlet } from "react-router";
import { IconJobs, IconSlides } from "@/components/icons";
import styles from "./AppShell.module.css";

const NAV = [
  { to: "/preview", label: "Template", icon: IconSlides, tag: null },
  { to: "/jobs", label: "Jobs", icon: IconJobs, tag: "bản 2" },
] as const;

export function AppShell() {
  return (
    <div className={styles.shell}>
      <aside className={styles.nav}>
        <div className={styles.brand}>
          <span className={styles.logo} aria-hidden="true" />
          <span>
            DocReel <small>admin</small>
          </span>
        </div>
        <nav aria-label="Điều hướng chính">
          <ul className={styles.links}>
            {NAV.map(({ to, label, icon: Icon, tag }) => (
              <li key={to}>
                <NavLink to={to} className={({ isActive }) => (isActive ? `${styles.link} ${styles.active}` : styles.link)}>
                  <Icon />
                  <span>{label}</span>
                  {tag && <em className={styles.tag}>{tag}</em>}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <footer className={styles.footer}>
          <span className={styles.env} data-env={import.meta.env.DEV ? "dev" : "prod"}>
            {import.meta.env.DEV ? "dev" : "prod"}
          </span>
          <span>{import.meta.env.DEV ? "đọc template từ repo" : "phục vụ bởi Spring Boot"}</span>
        </footer>
      </aside>
      <main className={styles.main}>
        <Outlet />
      </main>
    </div>
  );
}
