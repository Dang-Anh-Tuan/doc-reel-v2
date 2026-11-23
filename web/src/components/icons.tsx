import type { SVGProps } from "react";

type IconProps = SVGProps<SVGSVGElement>;

const base = {
  width: 18,
  height: 18,
  viewBox: "0 0 24 24",
  fill: "none",
  stroke: "currentColor",
  strokeWidth: 1.7,
  strokeLinecap: "round",
  strokeLinejoin: "round",
  "aria-hidden": true,
} as const;

export function IconSlides(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <rect x="6" y="2.5" width="12" height="19" rx="2" />
      <path d="M9 7h6M9 10.5h4M9 15h6" />
    </svg>
  );
}

export function IconJobs(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M4 6h16M4 12h16M4 18h10" />
      <circle cx="19" cy="18" r="2" />
    </svg>
  );
}

export function IconExternal(props: IconProps) {
  return (
    <svg {...base} {...props}>
      <path d="M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5" />
    </svg>
  );
}
