import type { ButtonHTMLAttributes, ReactNode } from "react";
import styles from "./ui.module.css";

const cx = (...names: Array<string | false | null | undefined>) => names.filter(Boolean).join(" ");

// ------------------------------------------------------------------ Button

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "default" | "ghost" | "primary";
}

export function Button({ variant = "default", className, type = "button", ...rest }: ButtonProps) {
  return (
    <button
      type={type}
      className={cx(styles.button, variant === "ghost" && styles.ghost, variant === "primary" && styles.primary, className)}
      {...rest}
    />
  );
}

// ------------------------------------------------------------------ Select

export interface Option<T extends string> {
  value: T;
  label: string;
}

interface SelectProps<T extends string> {
  label: string;
  value: T;
  options: ReadonlyArray<Option<T>>;
  onChange: (value: T) => void;
}

export function Select<T extends string>({ label, value, options, onChange }: SelectProps<T>) {
  return (
    <label className={styles.field}>
      {label}
      <select className={styles.select} value={value} onChange={(e) => onChange(e.target.value as T)}>
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </label>
  );
}

// ------------------------------------------------------------------ Segmented

interface SegmentedProps<T extends string> {
  label: string;
  value: T;
  options: ReadonlyArray<Option<T>>;
  onChange: (value: T) => void;
}

export function Segmented<T extends string>({ label, value, options, onChange }: SegmentedProps<T>) {
  return (
    <div className={styles.segmented} role="group" aria-label={label}>
      {options.map((o) => (
        <button
          key={o.value}
          type="button"
          className={styles.segment}
          aria-pressed={o.value === value}
          onClick={() => onChange(o.value)}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

// ------------------------------------------------------------------ Toggle

interface ToggleProps {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
}

export function Toggle({ label, checked, onChange }: ToggleProps) {
  return (
    <label className={styles.toggle}>
      <input type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
      {label}
    </label>
  );
}

// ------------------------------------------------------------------ Badge

export type Tone = "neutral" | "ok" | "bad" | "warn";

export function Badge({ tone = "neutral", title, children }: { tone?: Tone; title?: string; children: ReactNode }) {
  return (
    <span className={cx(styles.badge, tone !== "neutral" && styles[tone])} title={title}>
      {children}
    </span>
  );
}

// ------------------------------------------------------------------ Panel

interface PanelProps {
  title: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
  bodyClassName?: string;
}

export function Panel({ title, actions, children, className, bodyClassName }: PanelProps) {
  return (
    <section className={cx(styles.panel, className)}>
      <header className={styles.panelHeader}>
        <h2 className={styles.panelTitle}>{title}</h2>
        {actions}
      </header>
      <div className={cx(styles.panelBody, bodyClassName)}>{children}</div>
    </section>
  );
}

// ------------------------------------------------------------------ Message

export function Message({ title, children, error }: { title: string; children?: ReactNode; error?: boolean }) {
  return (
    <div className={cx(styles.message, error && styles.messageError)} role={error ? "alert" : "status"}>
      <p className={styles.messageTitle}>{title}</p>
      {children}
    </div>
  );
}
