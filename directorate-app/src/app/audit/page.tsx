"use client";

import React, { useState } from "react";
import { useAuditLog } from "@/lib/audit";
import { PageHeader, Card, Badge, Select, EmptyState, Spinner } from "@/components/ui";
import { IconAudit } from "@/components/icons";

export default function AuditLog() {
  const { entries, loading } = useAuditLog(500);
  const [filter, setFilter] = useState("");

  const categories = [...new Set(entries.map((e) => String(e.action).split(".")[0]))].sort();
  const rows = filter ? entries.filter((e) => String(e.action).startsWith(filter)) : entries;

  if (loading) return <Spinner label="Loading audit log…" />;

  return (
    <div>
      <PageHeader
        title="Audit Log"
        description="Every approval, removal, restoration and staff change made from this portal. Append-only — nobody, including a super-admin, can edit or delete an entry once written."
        actions={
          <Select value={filter} onChange={(e) => setFilter(e.target.value)}>
            <option value="">All categories</option>
            {categories.map((c) => <option key={c} value={c}>{c}</option>)}
          </Select>
        }
      />

      {rows.length === 0 ? (
        <EmptyState icon={<IconAudit className="h-8 w-8" />} title="No actions recorded yet" />
      ) : (
        <Card padded={false}>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-900/40 text-[10px] font-bold uppercase tracking-wider text-slate-500">
                <tr>
                  <th className="px-4 py-3">When</th>
                  <th className="px-4 py-3">Action</th>
                  <th className="px-4 py-3">Target</th>
                  <th className="px-4 py-3">By</th>
                  <th className="px-4 py-3">Detail</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {rows.map((e) => (
                  <tr key={e.id}>
                    <td className="whitespace-nowrap px-4 py-3 text-slate-400">{new Date(e.at).toLocaleString("en-PK")}</td>
                    <td className="px-4 py-3"><Badge>{e.action}</Badge></td>
                    <td className="px-4 py-3 font-mono text-slate-300">{e.target}</td>
                    <td className="px-4 py-3 text-slate-400">{e.byEmail}</td>
                    <td className="max-w-xs truncate px-4 py-3 text-slate-500">
                      {Object.keys(e.meta).length > 0 ? JSON.stringify(e.meta) : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  );
}
