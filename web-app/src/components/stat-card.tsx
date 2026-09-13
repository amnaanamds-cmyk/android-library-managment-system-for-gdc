"use client";

// Shared stat-card pattern — see DESIGN_SPEC.md §3. One template for every
// dashboard tile: icon chip, uppercase label (+ optional pill), a big
// value, an optional caption, and an optional progress bar. Colors read
// from the design tokens in globals.css so this card is correct in both
// themes without per-usage overrides.

import React from "react";

type Accent = "blue" | "green" | "amber" | "red" | "purple";

const ACCENT_CLASSES: Record<Accent, { chipBg: string; chipFg: string; bar: string }> = {
  blue: { chipBg: "bg-[var(--accent-100)]", chipFg: "text-[var(--accent-600)]", bar: "bg-[var(--accent-600)]" },
  green: { chipBg: "bg-[var(--success-bg)]", chipFg: "text-[var(--success-fg)]", bar: "bg-[var(--success-fg)]" },
  amber: { chipBg: "bg-[var(--warning-bg)]", chipFg: "text-[var(--warning-fg)]", bar: "bg-[var(--warning-fg)]" },
  red: { chipBg: "bg-[var(--danger-bg)]", chipFg: "text-[var(--danger-fg)]", bar: "bg-[var(--danger-fg)]" },
  purple: { chipBg: "bg-[var(--info-bg)]", chipFg: "text-[var(--info-fg)]", bar: "bg-[var(--info-fg)]" },
};

export interface StatCardProps {
  icon: React.ReactNode;
  label: string;
  value: React.ReactNode;
  /** Small right-aligned status pill, e.g. "0% full", "Profit". */
  pill?: string;
  /** Secondary caption under the value, e.g. "of 123 beds". */
  sub?: string;
  /** 0-100. Omit to render no progress bar. */
  progress?: number;
  accent?: Accent;
  className?: string;
}

export function StatCard({
  icon,
  label,
  value,
  pill,
  sub,
  progress,
  accent = "blue",
  className = "",
}: StatCardProps) {
  const a = ACCENT_CLASSES[accent];
  return (
    <div
      className={`rounded-xl border border-[var(--surface-border)] bg-[var(--surface)] p-4 shadow-sm ${className}`}
    >
      <div className="mb-3 flex min-w-0 items-center gap-2.5">
        <span className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-lg ${a.chipBg} ${a.chipFg}`}>
          {icon}
        </span>
        <span className="min-w-0 flex-1 truncate text-[11px] font-bold uppercase tracking-wider text-[var(--text-secondary)]">
          {label}
        </span>
        {pill && (
          <span className="shrink-0 rounded-full bg-[var(--surface-sunken)] px-2 py-0.5 text-[10px] font-bold text-[var(--text-tertiary)]">
            {pill}
          </span>
        )}
      </div>

      <div
        className="mb-1 text-[28px] font-bold leading-none text-[var(--text-primary)]"
        style={{ fontVariantNumeric: "tabular-nums" }}
      >
        {value}
      </div>
      {sub && <div className="mb-2 text-[11px] text-[var(--text-tertiary)]">{sub}</div>}

      {typeof progress === "number" && (
        <div className="mt-2 h-1 w-full overflow-hidden rounded-full bg-[var(--surface-sunken)]">
          <div
            className={`h-full rounded-full ${a.bar}`}
            style={{ width: `${Math.max(0, Math.min(100, progress))}%` }}
          />
        </div>
      )}
    </div>
  );
}
