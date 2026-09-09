"use client";

import React, { useMemo, useState } from "react";
import Link from "next/link";
import {
  useDirectorateNetwork,
  useInstitutionRegistry,
  useInstitutionStatusAction,
  useRebuildSummaries,
  isStale,
  DirectorateSnapshot,
  STALE_AFTER_MS,
} from "@/lib/directorate";

type SortKey = "name" | "totalBooks" | "members" | "issued" | "overdue" | "lastSynced";

const numberFmt = new Intl.NumberFormat("en-PK");

function relativeTime(ts: number): string {
  if (!ts) return "Never";
  const mins = Math.round((Date.now() - ts) / 60_000);
  if (mins < 1) return "Just now";
  if (mins < 60) return `${mins}m ago`;
  const hours = Math.round(mins / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.round(hours / 24)}d ago`;
}

export default function DirectorateOverview() {
  const { rows, totals, districts, loading, error, source } = useDirectorateNetwork();
  const { pending } = useInstitutionRegistry();
  const { setStatus, busy } = useInstitutionStatusAction();
  const { rebuild, busy: rebuilding, result } = useRebuildSummaries();

  const [search, setSearch] = useState("");
  const [sortKey, setSortKey] = useState<SortKey>("totalBooks");
  const [ascending, setAscending] = useState(false);

  const visible = useMemo(() => {
    const term = search.trim().toLowerCase();
    const filtered = term
      ? rows.filter(
          (c) =>
            c.name.toLowerCase().includes(term) ||
            c.institutionId.toLowerCase().includes(term) ||
            c.district.toLowerCase().includes(term),
        )
      : rows;

    return [...filtered].sort((a, b) => {
      const av = a[sortKey];
      const bv = b[sortKey];
      const cmp =
        typeof av === "string" && typeof bv === "string"
          ? av.localeCompare(bv)
          : Number(av) - Number(bv);
      return ascending ? cmp : -cmp;
    });
  }, [rows, search, sortKey, ascending]);

  const toggleSort = (key: SortKey) => {
    if (key === sortKey) setAscending((v) => !v);
    else {
      setSortKey(key);
      setAscending(key === "name");
    }
  };

  const exportCsv = () => {
    const header = [
      "Institution ID", "Name", "District", "Region", "Status",
      "Books", "E-Books", "Members", "Active Loans", "Overdue", "Reservations",
      "Last Synced", "Figures Computed", "Source",
    ];
    const lines = visible.map((c) =>
      [
        c.institutionId, c.name, c.district, c.region, c.status,
        c.totalBooks, c.totalEbooks, c.members, c.issued, c.overdue, c.reservations,
        c.lastSynced ? new Date(c.lastSynced).toISOString() : "never",
        c.computedAt ? new Date(c.computedAt).toISOString() : "never",
        c.source,
      ]
        // Quote every field and escape embedded quotes, so a college name
        // containing a comma cannot shift the remaining columns.
        .map((v) => `"${String(v).replace(/"/g, '""')}"`)
        .join(","),
    );
    const blob = new Blob([[header.join(","), ...lines].join("\n")], {
      type: "text/csv;charset=utf-8;",
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `nexlib-directorate-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  if (loading) {
    return (
      <div className="flex flex-col items-center gap-4 py-24">
        <div className="h-10 w-10 animate-spin rounded-full border-4 border-line border-t-transparent" />
        <p className="text-xs font-bold uppercase tracking-widest text-muted">
          Loading network data…
        </p>
      </div>
    );
  }

  const envelopeGaps = rows.filter((r) => r.recordsMissingSyncEnvelope > 0);

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight text-ink">Network Overview</h1>
          <p className="mt-1 text-sm text-muted">
            Aggregated statistics across {totals.institutions} registered{" "}
            {totals.institutions === 1 ? "college" : "colleges"} in{" "}
            {districts.length} {districts.length === 1 ? "district" : "districts"}.
          </p>
        </div>
        <div className="flex gap-2">
          <Link
            href="/director/register"
            className="rounded-lg bg-positive px-4 py-2 text-xs font-bold text-on-accent transition-colors hover:bg-positive"
          >
            + Add College
          </Link>
          <button
            onClick={() => void rebuild()}
            disabled={rebuilding}
            className="rounded-lg border border-line px-4 py-2 text-xs font-bold text-body transition-colors hover:bg-surface-2 disabled:opacity-40"
          >
            {rebuilding ? "Recomputing…" : "↻ Recompute figures"}
          </button>
          <button
            onClick={exportCsv}
            disabled={visible.length === 0}
            className="rounded-lg bg-accent-bg px-4 py-2 text-xs font-bold text-on-accent transition-colors hover:bg-accent-strong disabled:opacity-40"
          >
            ⬇ Export CSV
          </button>
        </div>
      </div>

      {result && (
        <Banner tone="emerald">
          Recomputed {result.written} of {result.institutions} active institutions.
        </Banner>
      )}

      {error && (
        <Banner tone="red" title="Could not read the directorate summary.">
          <p className="mt-1 font-mono text-xs opacity-80">{error}</p>
          <p className="mt-2 text-xs opacity-70">
            This portal requires an account whose role is{" "}
            <span className="font-mono">directorate</span>. A college&apos;s own{" "}
            <span className="font-mono">director</span> or <span className="font-mono">admin</span>{" "}
            is scoped to that college only.
          </p>
        </Banner>
      )}

      {source === "self-reported" && (
        <Banner tone="amber" title="Showing self-reported figures.">
          The scheduled rollup has not written any summaries yet, so these numbers come from each
          college&apos;s own app rather than from the server. Deploy the Cloud Functions (Blaze plan
          required) or press <span className="font-semibold">Recompute figures</span>.
        </Banner>
      )}

      {source === "registry-only" && (
        <Banner tone="amber" title="No college has reported yet.">
          Showing the institution registry so the network is visible. Counts appear once the
          scheduled rollup runs or each college completes its next sync.
        </Banner>
      )}

      {/* Approval queue — the control point that stops unmanaged growth. */}
      {pending.length > 0 && (
        <section className="rounded-2xl border border-accent/30 bg-accent-bg/5 p-5">
          <h2 className="text-sm font-black uppercase tracking-widest text-accent-strong">
            {pending.length} institution{pending.length === 1 ? "" : "s"} awaiting approval
          </h2>
          <p className="mt-1 text-xs text-muted">
            A pending college can sign in and run its own library, but does not report to the
            directorate until approved.
          </p>
          <ul className="mt-4 space-y-2">
            {pending.map((p) => (
              <li
                key={p.institutionId}
                className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-line bg-surface-2 px-4 py-3"
              >
                <div>
                  <p className="text-sm font-bold text-ink">{p.name}</p>
                  <p className="font-mono text-[11px] uppercase text-muted">
                    {p.institutionId}
                    {p.district && ` · ${p.district}`}
                    {p.adminName && ` · ${p.adminName}`}
                    {p.contactEmail && ` · ${p.contactEmail}`}
                  </p>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => void setStatus(p.institutionId, "active")}
                    disabled={busy === p.institutionId}
                    className="rounded-lg bg-positive px-3 py-1.5 text-xs font-bold text-on-accent hover:bg-positive disabled:opacity-40"
                  >
                    {busy === p.institutionId ? "Working…" : "Approve"}
                  </button>
                  <button
                    onClick={() => void setStatus(p.institutionId, "suspended")}
                    disabled={busy === p.institutionId}
                    className="rounded-lg border border-line px-3 py-1.5 text-xs font-bold text-body hover:bg-surface-2 disabled:opacity-40"
                  >
                    Reject
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}

      {/* Network KPIs */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Kpi label="Colleges" value={totals.institutions} icon="🏢" accent="text-accent-strong" />
        <Kpi label="Total Books" value={totals.totalBooks} icon="📚" />
        <Kpi label="Total Members" value={totals.members} icon="👥" />
        <Kpi label="Active Loans" value={totals.issued} icon="🔄" accent="text-accent" />
      </div>
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Kpi label="Overdue Loans" value={totals.overdue} icon="⏰" accent="text-danger" />
        <Kpi label="Reservations" value={totals.reservations} icon="🔖" />
        <Kpi label="E-Books" value={totals.totalEbooks} icon="💾" />
        <Kpi
          label="Reporting"
          value={totals.reporting}
          suffix={` / ${totals.institutions}`}
          icon="📡"
          accent={totals.reporting < totals.institutions ? "text-warning" : "text-positive"}
        />
      </div>

      {totals.stale > 0 && (
        <Banner tone="amber">
          ⚠️ {totals.stale} {totals.stale === 1 ? "college has" : "colleges have"} not synced in over{" "}
          {Math.round(STALE_AFTER_MS / 3_600_000)} hours. Their figures below are marked stale.
        </Banner>
      )}

      {envelopeGaps.length > 0 && (
        <Banner tone="amber" title="Some records predate the sync envelope.">
          {envelopeGaps.length} {envelopeGaps.length === 1 ? "college has" : "colleges have"}{" "}
          records with no <span className="font-mono">deleted</span> flag. Those rows are invisible
          to every client query, so the figures below understate them:{" "}
          {envelopeGaps
            .slice(0, 4)
            .map((c) => `${c.name} (${c.recordsMissingSyncEnvelope})`)
            .join(", ")}
          {envelopeGaps.length > 4 && ", …"}
        </Banner>
      )}

      {/* District rollups */}
      {districts.length > 1 && (
        <section className="overflow-hidden rounded-2xl border border-line bg-surface-2 shadow-xl">
          <div className="border-b border-line/60 p-5">
            <h2 className="text-lg font-bold text-ink">District Rollup</h2>
            <p className="mt-1 text-xs text-muted">
              Aggregated by <span className="font-mono">district</span> from the institution
              registry.
            </p>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-surface-2/30 text-[10px] font-black uppercase tracking-widest text-accent">
                <tr>
                  <th className="px-5 py-3">District</th>
                  <th className="px-5 py-3 text-right">Colleges</th>
                  <th className="px-5 py-3 text-right">Books</th>
                  <th className="px-5 py-3 text-right">Members</th>
                  <th className="px-5 py-3 text-right">Active</th>
                  <th className="px-5 py-3 text-right">Overdue</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line/40">
                {districts.map((d) => (
                  <tr key={d.district} className="hover:bg-surface-2/20">
                    <td className="px-5 py-3 font-bold text-ink">{d.district}</td>
                    <td className="px-5 py-3 text-right text-body">{d.institutions}</td>
                    <td className="px-5 py-3 text-right font-bold text-ink">
                      {numberFmt.format(d.totalBooks)}
                    </td>
                    <td className="px-5 py-3 text-right text-body">
                      {numberFmt.format(d.members)}
                    </td>
                    <td className="px-5 py-3 text-right text-accent">
                      {numberFmt.format(d.issued)}
                    </td>
                    <td className="px-5 py-3 text-right">
                      <span className={d.overdue > 0 ? "font-bold text-danger" : "text-muted"}>
                        {numberFmt.format(d.overdue)}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {/* College registry table */}
      <div className="overflow-hidden rounded-2xl border border-line bg-surface-2 shadow-xl">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line/60 p-5">
          <h2 className="text-lg font-bold text-ink">College Registry</h2>
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by name, ID or district…"
            className="w-64 rounded-lg border border-line bg-surface px-3 py-2 text-xs text-body outline-none focus:border-accent"
          />
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-surface-2/30 text-[10px] font-black uppercase tracking-widest text-accent">
              <tr>
                <Th onClick={() => toggleSort("name")} active={sortKey === "name"} asc={ascending}>
                  Institution
                </Th>
                <th className="px-5 py-3">District</th>
                <Th onClick={() => toggleSort("totalBooks")} active={sortKey === "totalBooks"} asc={ascending} align="right">
                  Books
                </Th>
                <Th onClick={() => toggleSort("members")} active={sortKey === "members"} asc={ascending} align="right">
                  Members
                </Th>
                <Th onClick={() => toggleSort("issued")} active={sortKey === "issued"} asc={ascending} align="right">
                  Active
                </Th>
                <Th onClick={() => toggleSort("overdue")} active={sortKey === "overdue"} asc={ascending} align="right">
                  Overdue
                </Th>
                <Th onClick={() => toggleSort("lastSynced")} active={sortKey === "lastSynced"} asc={ascending} align="right">
                  Last Sync
                </Th>
                <th className="px-5 py-3 text-right">Manage</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line/40">
              {visible.map((c) => (
                <CollegeRow
                  key={c.institutionId}
                  college={c}
                  onSetStatus={setStatus}
                  busy={busy === c.institutionId}
                />
              ))}
              {visible.length === 0 && (
                <tr>
                  <td colSpan={8} className="py-16 text-center">
                    <div className="flex flex-col items-center gap-2 opacity-40">
                      <span className="text-4xl">🏢</span>
                      <p className="text-sm font-bold uppercase tracking-wider text-muted">
                        {search ? "No college matches that search" : "No colleges registered yet"}
                      </p>
                    </div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

function CollegeRow({
  college,
  onSetStatus,
  busy,
}: {
  college: DirectorateSnapshot;
  onSetStatus: (id: string, status: "active" | "pending" | "suspended") => Promise<boolean>;
  busy: boolean;
}) {
  const stale = isStale(college);
  const neverReported = !college.lastSynced;
  const suspended = college.status === "suspended";

  // Suspend is the "remove" for a college. A college is never hard-deleted:
  // its books, patrons and loan history stay intact, it simply stops reporting
  // to the directorate and can be brought back. Deleting the tenant would
  // destroy a real library's catalogue, which no dashboard button should do.
  const toggle = async () => {
    const next = suspended ? "active" : "suspended";
    if (
      !suspended &&
      !window.confirm(
        `Suspend ${college.name}?\n\n` +
          "It stops appearing in directorate figures. Its own staff can still " +
          "run their library, and nothing is deleted. You can reactivate it here.",
      )
    ) {
      return;
    }
    await onSetStatus(college.institutionId, next);
  };

  return (
    <tr className="group transition-colors hover:bg-surface-2/20">
      <td className="px-5 py-4">
        <Link
          href={`/director/${encodeURIComponent(college.institutionId)}`}
          className="flex items-center gap-3"
        >
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-accent-bg/10 text-xs font-black text-accent">
            {college.name?.[0]?.toUpperCase() || "C"}
          </div>
          <div>
            <span className="font-bold text-ink transition-colors group-hover:text-accent">
              {college.name}
            </span>
            <p className="font-mono text-[10px] uppercase text-muted">
              {college.institutionId}
              {college.status !== "active" && (
                <span className="ml-2 rounded bg-warning/15 px-1.5 py-0.5 text-warning">
                  {college.status}
                </span>
              )}
            </p>
          </div>
        </Link>
      </td>
      <td className="px-5 py-4 text-[11px] text-muted">{college.district || "—"}</td>
      <td className="px-5 py-4 text-right font-bold text-ink">
        {numberFmt.format(college.totalBooks)}
      </td>
      <td className="px-5 py-4 text-right text-body">{numberFmt.format(college.members)}</td>
      <td className="px-5 py-4 text-right text-accent">{numberFmt.format(college.issued)}</td>
      <td className="px-5 py-4 text-right">
        <span className={college.overdue > 0 ? "font-bold text-danger" : "text-muted"}>
          {numberFmt.format(college.overdue)}
        </span>
      </td>
      <td className="px-5 py-4 text-right">
        <span
          className={`text-[11px] font-bold ${
            neverReported ? "text-muted" : stale ? "text-warning" : "text-positive"
          }`}
        >
          {neverReported ? "Not reporting" : relativeTime(college.lastSynced)}
        </span>
        <p className="text-[10px] uppercase tracking-wider text-muted">{college.source}</p>
      </td>
      <td className="px-5 py-4 text-right">
        <button
          onClick={toggle}
          disabled={busy}
          title={suspended ? "Bring this college back into the network" : "Stop this college reporting"}
          className={`rounded-lg px-3 py-1.5 text-[11px] font-bold transition-colors disabled:opacity-40 ${
            suspended
              ? "bg-positive text-on-accent hover:bg-positive"
              : "border border-line text-body hover:bg-surface-2 hover:text-on-accent"
          }`}
        >
          {busy ? "…" : suspended ? "Reactivate" : "Suspend"}
        </button>
      </td>
    </tr>
  );
}

function Banner({
  tone,
  title,
  children,
}: {
  tone: "amber" | "red" | "emerald";
  title?: string;
  children: React.ReactNode;
}) {
  const tones = {
    amber: "border-warning/30 bg-warning/10 text-warning",
    red: "border-danger/30 bg-danger/10 text-danger",
    emerald: "border-positive/30 bg-positive/10 text-positive",
  } as const;
  return (
    <div className={`rounded-xl border p-4 text-sm ${tones[tone]}`}>
      {title && <p className="font-bold">{title}</p>}
      <div className={title ? "mt-1 text-xs opacity-90" : ""}>{children}</div>
    </div>
  );
}

function Th({
  children,
  onClick,
  active,
  asc,
  align = "left",
}: {
  children: React.ReactNode;
  onClick: () => void;
  active: boolean;
  asc: boolean;
  align?: "left" | "right";
}) {
  return (
    <th className={`px-5 py-3 ${align === "right" ? "text-right" : "text-left"}`}>
      <button
        onClick={onClick}
        className={`uppercase tracking-widest transition-colors hover:text-ink ${
          active ? "text-ink" : ""
        }`}
      >
        {children}
        {active && <span className="ml-1">{asc ? "▲" : "▼"}</span>}
      </button>
    </th>
  );
}

function Kpi({
  label,
  value,
  icon,
  accent = "text-ink",
  suffix = "",
}: {
  label: string;
  value: number;
  icon: string;
  accent?: string;
  suffix?: string;
}) {
  return (
    <div className="group relative overflow-hidden rounded-2xl border border-line bg-surface-2 p-5 shadow-xl transition-colors hover:border-line">
      <div className="absolute -bottom-3 -right-3 text-5xl opacity-5 transition-transform duration-500 group-hover:scale-110">
        {icon}
      </div>
      <p className="mb-1 text-[10px] font-black uppercase tracking-widest text-muted">{label}</p>
      <h2 className={`text-3xl font-black tracking-tighter ${accent}`}>
        {numberFmt.format(value)}
        {suffix && <span className="text-lg text-muted">{suffix}</span>}
      </h2>
    </div>
  );
}
