// components/icons.tsx
//
// A small, consistent line-icon set, replacing the emoji this portal used
// throughout its first version. Emoji render differently per OS and font,
// carry no weight or size control, and read as a prototype rather than an
// official system — one government evaluator opening this on a machine
// with a different emoji set sees a different (or missing) icon than the
// one it was designed against. These are plain inline SVG: no dependency,
// no network request, consistent everywhere.

import React from "react";

export type IconProps = React.SVGProps<SVGSVGElement>;

function base(children: React.ReactNode, props: IconProps) {
  return (
    <svg
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={1.75}
      strokeLinecap="round"
      strokeLinejoin="round"
      {...props}
    >
      {children}
    </svg>
  );
}

export const IconOverview = (p: IconProps) => base(<><rect x="3" y="3" width="7" height="9" rx="1.5" /><rect x="14" y="3" width="7" height="5" rx="1.5" /><rect x="14" y="12" width="7" height="9" rx="1.5" /><rect x="3" y="16" width="7" height="5" rx="1.5" /></>, p);
export const IconRegistry = (p: IconProps) => base(<><path d="M4 5.5A2.5 2.5 0 016.5 3H19v18H6.5A2.5 2.5 0 014 18.5v-13z" /><path d="M8 3v18" /><path d="M12 8h5M12 12h5M12 16h5" /></>, p);
export const IconDistrict = (p: IconProps) => base(<><path d="M12 21s-7-6.1-7-11.5A7 7 0 0119 9.5C19 14.9 12 21 12 21z" /><circle cx="12" cy="9.5" r="2.3" /></>, p);
export const IconBench = (p: IconProps) => base(<><path d="M4 21V10M10 21V4M16 21v-7M22 21H2" /></>, p);
export const IconAlert = (p: IconProps) => base(<><path d="M12 3l9.5 17H2.5L12 3z" /><path d="M12 10v4" /><path d="M12 17.2h.01" /></>, p);
export const IconTrend = (p: IconProps) => base(<><path d="M3 17l6-6 4 4 8-9" /><path d="M15 6h6v6" /></>, p);
export const IconSearch = (p: IconProps) => base(<><circle cx="10.5" cy="10.5" r="6.5" /><path d="M20 20l-4.6-4.6" /></>, p);
export const IconFollowup = (p: IconProps) => base(<><path d="M9 11l2.5 2.5L16 8" /><path d="M20 12a8 8 0 10-3.2 6.4" /><path d="M20 5v5h-5" /></>, p);
export const IconAnnouncement = (p: IconProps) => base(<><path d="M4 10v4a1 1 0 001 1h2l5 4V5L7 9H5a1 1 0 00-1 1z" /><path d="M15 8.5a4 4 0 010 7" /><path d="M18 6a8 8 0 010 12" /></>, p);
export const IconDocument = (p: IconProps) => base(<><path d="M7 3h7l5 5v13a1 1 0 01-1 1H7a1 1 0 01-1-1V4a1 1 0 011-1z" /><path d="M14 3v5h5" /><path d="M9 13h6M9 17h6" /></>, p);
export const IconInspection = (p: IconProps) => base(<><rect x="3" y="4" width="18" height="17" rx="2" /><path d="M3 9h18" /><path d="M8 2v4M16 2v4" /><path d="M8 14l2.2 2.2L15 12" /></>, p);
export const IconAudit = (p: IconProps) => base(<><path d="M9 2h6l5 5v13a2 2 0 01-2 2H7a2 2 0 01-2-2V4a2 2 0 012-2z" /><path d="M14 2v5h5" /><path d="M8 12h8M8 16h5" /><circle cx="8.5" cy="9" r=".6" fill="currentColor" stroke="none" /></>, p);
export const IconStaff = (p: IconProps) => base(<><circle cx="9" cy="8" r="3.2" /><path d="M2.5 20c.7-3.4 3.3-5.5 6.5-5.5s5.8 2.1 6.5 5.5" /><circle cx="18" cy="7" r="2.4" /><path d="M15.5 14.3c2.6.3 4.4 2.1 5 4.7" /></>, p);
export const IconReports = (p: IconProps) => base(<><rect x="4" y="3" width="16" height="18" rx="1.5" /><path d="M8 8h8M8 12h8M8 16h5" /></>, p);
export const IconLogout = (p: IconProps) => base(<><path d="M15 3H6a1 1 0 00-1 1v16a1 1 0 001 1h9" /><path d="M10 12h11M17 8l4 4-4 4" /></>, p);
export const IconChevronDown = (p: IconProps) => base(<path d="M6 9l6 6 6-6" />, p);
export const IconCheck = (p: IconProps) => base(<path d="M20 6L9 17l-5-5" />, p);
export const IconX = (p: IconProps) => base(<path d="M18 6L6 18M6 6l12 12" />, p);
export const IconPlus = (p: IconProps) => base(<path d="M12 5v14M5 12h14" />, p);
export const IconDownload = (p: IconProps) => base(<><path d="M12 3v13" /><path d="M7 11l5 5 5-5" /><path d="M4 20h16" /></>, p);
export const IconBuilding = (p: IconProps) => base(<><rect x="4" y="3" width="16" height="18" rx="1" /><path d="M9 8h1M14 8h1M9 12h1M14 12h1M9 16h1M14 16h1" /></>, p);
export const IconSpinner = (p: IconProps) => (
  <svg viewBox="0 0 24 24" fill="none" {...p}>
    <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="2.5" opacity="0.25" />
    <path d="M21 12a9 9 0 00-9-9" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" />
  </svg>
);
