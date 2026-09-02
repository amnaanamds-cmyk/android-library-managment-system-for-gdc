"use client";

import React, { useMemo, useState } from "react";
import Link from "next/link";
import {
  useDirectorateNetwork,
  isStale,
  DirectorateSnapshot,
  STALE_AFTER_MS,
} from "@/lib/directorate";

type SortKey = "name" | "booksCount" | "membersCount" | "activeLoans" | "overdueCount" | "lastSyncAt";

const numberFmt = new Intl.NumberFormat("en-PK");

function relativeTime(ts: number): string {
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

export default function DirectorateOverview() {
  const { colleges, totals, loading, error, usedFallback } = useDirectorateNetwork();
  const [search, setSearch] = useState("");
  const [sortKey, setSortKey] = useState<SortKey>("booksCount");
  const [ascending, setAscending] = useState(false);

  const rows = useMemo(() => {
    const term = search.trim().toLowerCase();
    const filtered = term
      ? colleges.filter(
          (c) =>
            c.name.toLowerCase().includes(term) ||
            c.institutionId.toLowerCase().includes(term) ||
            (c.location || "").toLowerCase().includes(term),
        )
      : colleges;

    return [...filtered].sort((a, b) => {
      const av = a[sortKey];
      const bv = b[sortKey];
      const cmp =
        typeof av === "string" && typeof bv === "string"
          ? av.localeCompare(bv)
          : Number(av) - Number(bv);
      return ascending ? cmp : -cmp;
    });
  }, [colleges, search, sortKey, ascending]);

  const toggleSort = (key: SortKey) => {
    if (key === sortKey) {
      setAscending((v) => !v);
    } else {
      setSortKey(key);
      setAscending(key === "name");
    }
  };

  const exportCsv = () => {
    const header = [
      "Institution ID",
      "Name",
      "Location",
      "Books",
      "E-Books",
      "Members",
      "Active Loans",
      "Overdue",
      "Reservations",
      "Fines Outstanding",
      "Last Sync",
      "Platform",
    ];
    const lines = rows.map((c) =>
      [
        c.institutionId,
        c.name,
        c.location || "",
        c.booksCount,
        c.ebooksCount,
        c.membersCount,
        c.activeLoans,
        c.overdueCount,
        c.reservationsCount,
        c.finesOutstanding,
        c.lastSyncAt ? new Date(c.lastSyncAt).toISOString() : "never",
        c.lastSyncPlatform,
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
        <div className="h-10 w-10 animate-spin rounded-full border-4 border-blue-600 border-t-transparent" />
        <p className="text-xs font-bold uppercase tracking-widest text-slate-500">
          Loading network data…
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-8">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight text-white">Network Overview</h1>
          <p className="mt-1 text-sm text-slate-400">
            Aggregated library statistics across {totals.colleges} registered{" "}
            {totals.colleges === 1 ? "college" : "colleges"}.
          </p>
        </div>
        <button
          onClick={exportCsv}
          disabled={rows.length === 0}
          className="rounded-lg bg-[#C8A84B] px-4 py-2 text-xs font-bold text-[#1a1400] transition-colors hover:bg-[#E6C96E] disabled:opacity-40"
        >
          ⬇ Export CSV
        </button>
      </div>

      {error && (
        <div className="rounded-xl border border-red-500/30 bg-red-500/10 p-4 text-sm text-red-300">
          <p className="font-bold">Could not read the directorate registry.</p>
          <p className="mt-1 font-mono text-xs opacity-80">{error}</p>
          <p className="mt-2 text-xs text-red-200/70">
            Confirm the deployed <span className="font-mono">firestore.rules</span> grants signed-in
            read access to <span className="font-mono">/directorate_index</span>.
          </p>
        </div>
      )}

      {usedFallback && (
        <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 p-4 text-sm text-amber-200">
          <p className="font-bold">No college has published a snapshot yet.</p>
          <p className="mt-1 text-xs opacity-80">
            Showing the institution registry so you can see the network. Counts appear as each
            college&apos;s desktop, web, or Android app completes its next sync.
          </p>
        </div>
      )}

      {/* Network KPIs */}
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Kpi label="Colleges" value={totals.colleges} icon="🏢" accent="text-[#E6C96E]" />
        <Kpi label="Total Books" value={totals.books} icon="📚" />
        <Kpi label="Total Members" value={totals.members} icon="👥" />
        <Kpi label="Active Loans" value={totals.activeLoans} icon="🔄" accent="text-blue-400" />
      </div>
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <Kpi label="Overdue Loans" value={totals.overdue} icon="⏰" accent="text-red-400" />
        <Kpi label="Reservations" value={totals.reservations} icon="🔖" />
        <Kpi label="E-Books" value={totals.ebooks} icon="💾" />
        <Kpi
          label="Reporting"
          value={totals.reporting}
          suffix={` / ${totals.colleges}`}
          icon="📡"
          accent={totals.reporting < totals.colleges ? "text-amber-400" : "text-emerald-400"}
        />
      </div>

      {totals.stale > 0 && (
        <div className="rounded-xl border border-amber-500/20 bg-amber-500/5 px-4 py-3 text-xs text-amber-200/90">
          ⚠️ {totals.stale} {totals.stale === 1 ? "college has" : "colleges have"} not synced in over{" "}
          {Math.round(STALE_AFTER_MS / 3_600_000)} hours. Their figures below are marked stale.
        </div>
      )}

      {/* Registry table */}
      <div className="overflow-hidden rounded-2xl border border-blue-950 bg-[#070F1E] shadow-xl">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-blue-950/60 p-5">
          <h2 className="text-lg font-bold text-white">College Registry</h2>
          <input
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by name, ID or district…"
            className="w-64 rounded-lg border border-[#1E3050] bg-[#0D1F38] px-3 py-2 text-xs text-slate-200 outline-none focus:border-[#C8A84B]"
          />
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-blue-950/30 text-[10px] font-black uppercase tracking-widest text-blue-400">
              <tr>
                <Th onClick={() => toggleSort("name")} active={sortKey === "name"} asc={ascending}>
                  Institution
                </Th>
                <th className="px-5 py-3">ID</th>
                <Th
                  onClick={() => toggleSort("booksCount")}
                  active={sortKey === "booksCount"}
                  asc={ascending}
                  align="right"
                >
                  Books
                </Th>
                <Th
                  onClick={() => toggleSort("membersCount")}
                  active={sortKey === "membersCount"}
                  asc={ascending}
                  align="right"
                >
                  Members
                </Th>
                <Th
                  onClick={() => toggleSort("activeLoans")}
                  active={sortKey === "activeLoans"}
                  asc={ascending}
                  align="right"
                >
                  Active
                </Th>
                <Th
                  onClick={() => toggleSort("overdueCount")}
                  active={sortKey === "overdueCount"}
                  asc={ascending}
                  align="right"
                >
                  Overdue
                </Th>
                <Th
                  onClick={() => toggleSort("lastSyncAt")}
                  active={sortKey === "lastSyncAt"}
                  asc={ascending}
                  align="right"
                >
                  Last Sync
                </Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-blue-950/40">
              {rows.map((c) => (
                <CollegeRow key={c.institutionId} college={c} />
              ))}
              {rows.length === 0 && (
                <tr>
                  <td colSpan={7} className="py-16 text-center">
                    <div className="flex flex-col items-center gap-2 opacity-40">
                      <span className="text-4xl">🏢</span>
                      <p className="text-sm font-bold uppercase tracking-wider text-slate-400">
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

function CollegeRow({ college }: { college: DirectorateSnapshot }) {
  const stale = isStale(college);
  const neverReported = !college.lastSyncAt;

  return (
    <tr className="group transition-colors hover:bg-blue-950/20">
      <td className="px-5 py-4">
        <Link href={`/director/${encodeURIComponent(college.institutionId)}`} className="flex items-center gap-3">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-blue-600/10 text-xs font-black text-blue-500">
            {college.name?.[0]?.toUpperCase() || "C"}
          </div>
          <div>
            <span className="font-bold text-white transition-colors group-hover:text-blue-400">
              {college.name}
            </span>
            {college.location && (
              <p className="text-[11px] text-slate-500">{college.location}</p>
            )}
          </div>
        </Link>
      </td>
      <td className="px-5 py-4 font-mono text-[11px] uppercase text-slate-500">
        {college.institutionId}
      </td>
      <td className="px-5 py-4 text-right font-bold text-white">
        {numberFmt.format(college.booksCount)}
      </td>
      <td className="px-5 py-4 text-right text-slate-300">
        {numberFmt.format(college.membersCount)}
      </td>
      <td className="px-5 py-4 text-right text-blue-300">
        {numberFmt.format(college.activeLoans)}
      </td>
      <td className="px-5 py-4 text-right">
        <span className={college.overdueCount > 0 ? "font-bold text-red-400" : "text-slate-500"}>
          {numberFmt.format(college.overdueCount)}
        </span>
      </td>
      <td className="px-5 py-4 text-right">
        <span
          className={`text-[11px] font-bold ${
            neverReported ? "text-slate-600" : stale ? "text-amber-400" : "text-emerald-400"
          }`}
        >
          {neverReported ? "Not reporting" : relativeTime(college.lastSyncAt)}
        </span>
        {!neverReported && (
          <p className="text-[10px] uppercase tracking-wider text-slate-600">
            {college.lastSyncPlatform}
          </p>
        )}
      </td>
    </tr>
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
        className={`uppercase tracking-widest transition-colors hover:text-white ${
          active ? "text-white" : ""
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
  accent = "text-white",
  suffix = "",
}: {
  label: string;
  value: number;
  icon: string;
  accent?: string;
  suffix?: string;
}) {
  return (
    <div className="group relative overflow-hidden rounded-2xl border border-blue-950 bg-[#070F1E] p-5 shadow-xl transition-colors hover:border-blue-800">
      <div className="absolute -bottom-3 -right-3 text-5xl opacity-5 transition-transform duration-500 group-hover:scale-110">
        {icon}
      </div>
      <p className="mb-1 text-[10px] font-black uppercase tracking-widest text-slate-500">{label}</p>
      <h2 className={`text-3xl font-black tracking-tighter ${accent}`}>
        {numberFmt.format(value)}
        {suffix && <span className="text-lg text-slate-600">{suffix}</span>}
      </h2>
    </div>
  );
}
