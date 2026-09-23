"use client";

import React, { useMemo, useState } from "react";
import Link from "next/link";
import {
  useDirectorateNetwork,
  isStale,
  setApproval,
  DirectorateSnapshot,
} from "@/lib/directorate";
import { useAuth } from "@/lib/auth-context";
import { useMyTier, tierCan } from "@/lib/staff";
import { PageHeader, Card, Badge, Button, Input, Select, EmptyState, Spinner, numberFmt, relativeTime } from "@/components/ui";
import { IconRegistry, IconDownload } from "@/components/icons";

type SortKey = "name" | "booksCount" | "membersCount" | "activeLoans" | "overdueCount" | "lastSyncAt";

export default function Registry() {
  const { colleges, pending, hidden, loading, usedFallback } = useDirectorateNetwork();
  const { profile } = useAuth();
  const tier = useMyTier();
  const canWrite = tierCan(tier, "approve");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [district, setDistrict] = useState("");
  const [sortKey, setSortKey] = useState<SortKey>("booksCount");
  const [ascending, setAscending] = useState(false);

  const decide = async (id: string, status: "approved" | "hidden" | "pending") => {
    setBusyId(id);
    try {
      await setApproval(id, status, profile?.email || "");
    } finally {
      setBusyId(null);
    }
  };

  const districts = useMemo(
    () => [...new Set(colleges.map((c) => c.district).filter(Boolean))].sort() as string[],
    [colleges],
  );

  const rows = useMemo(() => {
    const term = search.trim().toLowerCase();
    let filtered = colleges;
    if (term) {
      filtered = filtered.filter(
        (c) =>
          c.name.toLowerCase().includes(term) ||
          c.institutionId.toLowerCase().includes(term) ||
          (c.location || "").toLowerCase().includes(term),
      );
    }
    if (district) filtered = filtered.filter((c) => c.district === district);
    return [...filtered].sort((a, b) => {
      const av = a[sortKey];
      const bv = b[sortKey];
      const cmp = typeof av === "string" && typeof bv === "string" ? av.localeCompare(bv) : Number(av) - Number(bv);
      return ascending ? cmp : -cmp;
    });
  }, [colleges, search, district, sortKey, ascending]);

  const toggleSort = (key: SortKey) => {
    if (key === sortKey) setAscending((v) => !v);
    else {
      setSortKey(key);
      setAscending(key === "name");
    }
  };

  const exportCsv = () => {
    const header = ["Institution ID", "Name", "District", "Location", "Books", "E-Books", "Members", "Active Loans", "Overdue", "Reservations", "Fines Outstanding", "Last Sync", "Platform"];
    const lines = rows.map((c) =>
      [c.institutionId, c.name, c.district || "", c.location || "", c.booksCount, c.ebooksCount, c.membersCount, c.activeLoans, c.overdueCount, c.reservationsCount, c.finesOutstanding, c.lastSyncAt ? new Date(c.lastSyncAt).toISOString() : "never", c.lastSyncPlatform]
        .map((v) => `"${String(v).replace(/"/g, '""')}"`)
        .join(","),
    );
    const blob = new Blob([[header.join(","), ...lines].join("\n")], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `nexlib-registry-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  if (loading) return <Spinner label="Loading registry…" />;

  return (
    <div>
      <PageHeader
        title="College Registry"
        description="Every institution the directorate has admitted, plus anything awaiting a decision."
        actions={
          <Button variant="secondary" size="sm" icon={<IconDownload className="h-3.5 w-3.5" />} onClick={exportCsv} disabled={rows.length === 0}>
            Export CSV
          </Button>
        }
      />

      {usedFallback && (
        <Card className="mb-6 border-amber-900/60 bg-amber-500/5">
          <p className="text-sm font-semibold text-amber-200">No college has published a snapshot yet.</p>
        </Card>
      )}

      {pending.length > 0 && (
        <Card className="mb-6 border-amber-500/30 bg-amber-500/5">
          <h2 className="text-sm font-bold text-amber-300">Awaiting approval ({pending.length})</h2>
          <p className="mt-1 text-xs text-slate-400">
            Self-registered. Running normally, but excluded from network totals until admitted.
          </p>
          <div className="mt-4 space-y-2">
            {pending.map((c) => (
              <div key={c.institutionId} className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-slate-800 bg-[#070F1E] px-4 py-3">
                <div>
                  <p className="font-semibold text-white">{c.name}</p>
                  <p className="font-mono text-[11px] uppercase text-slate-500">
                    {c.institutionId}{c.location ? ` · ${c.location}` : ""}
                  </p>
                </div>
                {canWrite ? (
                  <div className="flex gap-2">
                    <Button variant="primary" size="sm" disabled={busyId === c.institutionId} onClick={() => decide(c.institutionId, "approved")}>
                      Approve
                    </Button>
                    <Button variant="danger" size="sm" disabled={busyId === c.institutionId} onClick={() => decide(c.institutionId, "hidden")}>
                      Reject
                    </Button>
                  </div>
                ) : (
                  <Badge>Read-only access</Badge>
                )}
              </div>
            ))}
          </div>
        </Card>
      )}

      {hidden.length > 0 && (
        <details className="mb-6 rounded-lg border border-slate-800 bg-[#0B1220] p-5">
          <summary className="cursor-pointer text-sm font-bold text-slate-400">Removed from dashboard ({hidden.length})</summary>
          <p className="mt-2 text-xs text-slate-500">Excluded from totals. Their library data is intact and unaffected.</p>
          <div className="mt-4 space-y-2">
            {hidden.map((c) => (
              <div key={c.institutionId} className="flex flex-wrap items-center justify-between gap-3 rounded-md border border-slate-800/60 px-4 py-3">
                <div>
                  <p className="font-semibold text-slate-300">{c.name}</p>
                  <p className="font-mono text-[11px] uppercase text-slate-600">{c.institutionId}</p>
                </div>
                {canWrite && (
                  <Button variant="secondary" size="sm" disabled={busyId === c.institutionId} onClick={() => decide(c.institutionId, "approved")}>
                    Restore
                  </Button>
                )}
              </div>
            ))}
          </div>
        </details>
      )}

      <Card padded={false}>
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-800 p-4">
          <h2 className="flex items-center gap-2 text-sm font-semibold text-white">
            <IconRegistry className="h-4 w-4 text-amber-400" /> Approved institutions ({rows.length})
          </h2>
          <div className="flex gap-2">
            <Select value={district} onChange={(e) => setDistrict(e.target.value)}>
              <option value="">All districts</option>
              {districts.map((d) => <option key={d} value={d}>{d}</option>)}
            </Select>
            <Input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search name, ID or location…" className="w-64" />
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-slate-900/40 text-[10px] font-bold uppercase tracking-wider text-slate-500">
              <tr>
                <Th onClick={() => toggleSort("name")} active={sortKey === "name"} asc={ascending}>Institution</Th>
                <th className="px-4 py-3">District</th>
                <Th onClick={() => toggleSort("booksCount")} active={sortKey === "booksCount"} asc={ascending} align="right">Books</Th>
                <Th onClick={() => toggleSort("membersCount")} active={sortKey === "membersCount"} asc={ascending} align="right">Members</Th>
                <Th onClick={() => toggleSort("activeLoans")} active={sortKey === "activeLoans"} asc={ascending} align="right">Active</Th>
                <Th onClick={() => toggleSort("overdueCount")} active={sortKey === "overdueCount"} asc={ascending} align="right">Overdue</Th>
                <Th onClick={() => toggleSort("lastSyncAt")} active={sortKey === "lastSyncAt"} asc={ascending} align="right">Last Sync</Th>
                {canWrite && <th className="px-4 py-3 text-right">Actions</th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/60">
              {rows.map((c) => (
                <Row key={c.institutionId} college={c} busy={busyId === c.institutionId} canWrite={canWrite} onRemove={() => decide(c.institutionId, "hidden")} />
              ))}
            </tbody>
          </table>
          {rows.length === 0 && (
            <EmptyState icon={<IconRegistry className="h-8 w-8" />} title={search || district ? "No institution matches this filter" : "No institutions registered yet"} />
          )}
        </div>
      </Card>
    </div>
  );
}

function Row({ college, busy, canWrite, onRemove }: { college: DirectorateSnapshot; busy: boolean; canWrite: boolean; onRemove: () => void }) {
  const stale = isStale(college);
  const never = !college.lastSyncAt;
  return (
    <tr className="hover:bg-slate-900/30">
      <td className="px-4 py-3.5">
        <Link href={`/${encodeURIComponent(college.institutionId)}`} className="flex items-center gap-3">
          <div className="flex h-7 w-7 items-center justify-center rounded-md bg-blue-500/10 text-xs font-bold text-blue-400">
            {college.name?.[0]?.toUpperCase() || "C"}
          </div>
          <div>
            <span className="font-semibold text-white hover:text-blue-400">{college.name}</span>
            <p className="font-mono text-[10px] uppercase text-slate-500">{college.institutionId}</p>
          </div>
        </Link>
      </td>
      <td className="px-4 py-3.5 text-xs text-slate-400">{college.district || "—"}</td>
      <td className="px-4 py-3.5 text-right font-semibold text-white">{numberFmt.format(college.booksCount)}</td>
      <td className="px-4 py-3.5 text-right text-slate-300">{numberFmt.format(college.membersCount)}</td>
      <td className="px-4 py-3.5 text-right text-blue-300">{numberFmt.format(college.activeLoans)}</td>
      <td className="px-4 py-3.5 text-right">
        <span className={college.overdueCount > 0 ? "font-semibold text-red-400" : "text-slate-500"}>{numberFmt.format(college.overdueCount)}</span>
      </td>
      <td className="px-4 py-3.5 text-right">
        <Badge tone={never ? "neutral" : stale ? "amber" : "emerald"}>{never ? "Not reporting" : relativeTime(college.lastSyncAt)}</Badge>
      </td>
      {canWrite && (
        <td className="px-4 py-3.5 text-right">
          <Button variant="danger" size="sm" disabled={busy} onClick={onRemove}>Remove</Button>
        </td>
      )}
    </tr>
  );
}

function Th({ children, onClick, active, asc, align = "left" }: { children: React.ReactNode; onClick: () => void; active: boolean; asc: boolean; align?: "left" | "right" }) {
  return (
    <th className={`px-4 py-3 ${align === "right" ? "text-right" : "text-left"}`}>
      <button onClick={onClick} className={`uppercase tracking-wider hover:text-white ${active ? "text-white" : ""}`}>
        {children}{active && <span className="ml-1">{asc ? "▲" : "▼"}</span>}
      </button>
    </th>
  );
}
