// components/ui.tsx
//
// The portal's design system, in one file because it is small enough to
// stay in one file. Every page should build from these rather than
// hand-rolling Tailwind class strings — that repetition (rounded-2xl +
// shadow-xl + border on every single card, scattered across five files)
// was most of what made the first version read as a prototype rather than
// one coherent system.
//
// Deliberately restrained relative to a marketing page: sharp-ish corners
// (rounded-lg, not rounded-2xl), no decorative gradients, no icon
// watermarks bleeding out of card corners. An MIS is read many times a day
// by someone doing a job, not looked at once and admired.

import React from "react";
import { IconSpinner } from "./icons";

export const numberFmt = new Intl.NumberFormat("en-PK");
export const currencyFmt = (v: number) => `Rs ${numberFmt.format(Math.round(v))}`;

export function relativeTime(ts: number): string {
  if (!ts) return "Never";
  const diff = Date.now() - ts;
  const mins = Math.round(diff / 60_000);
  if (mins < 1) return "Just now";
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.round(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.round(hours / 24);
  return `${days}d ago`;
}

// ── Layout primitives ───────────────────────────────────────────────────

export function PageHeader({
  title,
  description,
  actions,
}: {
  title: string;
  description?: string;
  actions?: React.ReactNode;
}) {
  return (
    <div className="mb-7 flex flex-wrap items-end justify-between gap-4 border-b border-slate-800 pb-5">
      <div>
        <h1 className="text-2xl font-bold tracking-tight text-white">{title}</h1>
        {description && <p className="mt-1.5 max-w-2xl text-sm text-slate-400">{description}</p>}
      </div>
      {actions && <div className="flex items-center gap-2">{actions}</div>}
    </div>
  );
}

export function Card({
  children,
  className = "",
  padded = true,
}: {
  children: React.ReactNode;
  className?: string;
  padded?: boolean;
}) {
  return (
    <div className={`rounded-lg border border-slate-800 bg-[#0B1220] ${padded ? "p-5" : ""} ${className}`}>
      {children}
    </div>
  );
}

export function SectionTitle({
  icon,
  children,
  action,
}: {
  icon?: React.ReactNode;
  children: React.ReactNode;
  action?: React.ReactNode;
}) {
  return (
    <div className="mb-4 flex items-center justify-between gap-3">
      <h2 className="flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-slate-300">
        {icon && <span className="text-amber-400">{icon}</span>}
        {children}
      </h2>
      {action}
    </div>
  );
}

// ── Data display ────────────────────────────────────────────────────────

export function StatTile({
  label,
  value,
  suffix = "",
  prefix = "",
  tone = "default",
}: {
  label: string;
  value: number | string;
  suffix?: string;
  prefix?: string;
  tone?: "default" | "amber" | "red" | "emerald" | "blue";
}) {
  const toneClass = {
    default: "text-white",
    amber: "text-amber-400",
    red: "text-red-400",
    emerald: "text-emerald-400",
    blue: "text-blue-400",
  }[tone];
  return (
    <Card>
      <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">{label}</p>
      <p className={`mt-1.5 text-2xl font-bold tabular-nums tracking-tight ${toneClass}`}>
        {prefix}
        {typeof value === "number" ? numberFmt.format(value) : value}
        {suffix && <span className="ml-1 text-base font-medium text-slate-500">{suffix}</span>}
      </p>
    </Card>
  );
}

type BadgeTone = "neutral" | "amber" | "red" | "emerald" | "blue";

const BADGE_TONE: Record<BadgeTone, string> = {
  neutral: "bg-slate-800 text-slate-300 border-slate-700",
  amber: "bg-amber-500/10 text-amber-400 border-amber-500/30",
  red: "bg-red-500/10 text-red-400 border-red-500/30",
  emerald: "bg-emerald-500/10 text-emerald-400 border-emerald-500/30",
  blue: "bg-blue-500/10 text-blue-400 border-blue-500/30",
};

export function Badge({ tone = "neutral", children }: { tone?: BadgeTone; children: React.ReactNode }) {
  return (
    <span className={`inline-flex items-center gap-1 rounded border px-2 py-0.5 text-[11px] font-semibold ${BADGE_TONE[tone]}`}>
      {children}
    </span>
  );
}

/** A single solid meter bar. No gradient — one fill color reads faster and
 *  matches the corresponding tone used elsewhere (a red bar next to a red
 *  badge is a stronger signal than a gradient that fades toward gold). */
export function Meter({ pct, tone = "blue" }: { pct: number; tone?: BadgeTone }) {
  const fill = {
    neutral: "bg-slate-500",
    amber: "bg-amber-500",
    red: "bg-red-500",
    emerald: "bg-emerald-500",
    blue: "bg-blue-500",
  }[tone];
  return (
    <div className="h-1.5 overflow-hidden rounded-full bg-slate-800">
      <div className={`h-full rounded-full ${fill}`} style={{ width: `${Math.max(0, Math.min(100, pct))}%` }} />
    </div>
  );
}

export function EmptyState({ icon, title, detail }: { icon?: React.ReactNode; title: string; detail?: string }) {
  return (
    <div className="flex flex-col items-center gap-2 py-14 text-center">
      {icon && <div className="text-slate-700">{icon}</div>}
      <p className="text-sm font-semibold text-slate-400">{title}</p>
      {detail && <p className="max-w-sm text-xs text-slate-600">{detail}</p>}
    </div>
  );
}

export function Spinner({ label }: { label?: string }) {
  return (
    <div className="flex flex-col items-center gap-3 py-20">
      <IconSpinner className="h-7 w-7 animate-spin text-amber-400" />
      {label && <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</p>}
    </div>
  );
}

// ── Controls ────────────────────────────────────────────────────────────

type ButtonVariant = "primary" | "secondary" | "danger" | "ghost";

const BUTTON_VARIANT: Record<ButtonVariant, string> = {
  primary: "bg-amber-500 text-slate-950 hover:bg-amber-400 disabled:bg-amber-500/40",
  secondary: "border border-slate-700 text-slate-200 hover:border-slate-500 hover:bg-slate-800/60",
  danger: "border border-red-900/60 text-red-400 hover:border-red-500/60 hover:bg-red-500/10",
  ghost: "text-slate-400 hover:text-white hover:bg-slate-800/60",
};

export function Button({
  variant = "secondary",
  size = "md",
  icon,
  children,
  className = "",
  ...rest
}: {
  variant?: ButtonVariant;
  size?: "sm" | "md";
  icon?: React.ReactNode;
} & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  const sizeClass = size === "sm" ? "px-2.5 py-1.5 text-xs" : "px-3.5 py-2 text-sm";
  return (
    <button
      className={`inline-flex items-center gap-1.5 rounded-md font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${sizeClass} ${BUTTON_VARIANT[variant]} ${className}`}
      {...rest}
    >
      {icon}
      {children}
    </button>
  );
}

export function Input(props: React.InputHTMLAttributes<HTMLInputElement>) {
  return (
    <input
      {...props}
      className={`rounded-md border border-slate-700 bg-[#0B1220] px-3 py-2 text-sm text-slate-100 placeholder:text-slate-600 outline-none focus:border-amber-500/60 ${props.className || ""}`}
    />
  );
}

export function Textarea(props: React.TextareaHTMLAttributes<HTMLTextAreaElement>) {
  return (
    <textarea
      {...props}
      className={`rounded-md border border-slate-700 bg-[#0B1220] px-3 py-2 text-sm text-slate-100 placeholder:text-slate-600 outline-none focus:border-amber-500/60 ${props.className || ""}`}
    />
  );
}

export function Select({ children, ...props }: React.SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select
      {...props}
      className={`rounded-md border border-slate-700 bg-[#0B1220] px-3 py-2 text-sm text-slate-100 outline-none focus:border-amber-500/60 ${props.className || ""}`}
    >
      {children}
    </select>
  );
}

export function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="flex flex-col gap-1.5">
      <span className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">{label}</span>
      {children}
    </label>
  );
}
