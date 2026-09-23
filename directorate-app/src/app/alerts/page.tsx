"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useDirectorateNetwork } from "@/lib/directorate";
import { computeAlerts, AlertSeverity } from "@/lib/analytics";
import { PageHeader, Card, Badge, Select, Spinner, EmptyState } from "@/components/ui";
import { IconAlert } from "@/components/icons";

export default function Alerts() {
  const { colleges, loading } = useDirectorateNetwork();
  const [severity, setSeverity] = useState<AlertSeverity | "">("");
  const alerts = computeAlerts(colleges).filter((a) => !severity || a.severity === severity);

  if (loading) return <Spinner label="Scanning the registry…" />;

  const high = alerts.filter((a) => a.severity === "high").length;
  const medium = alerts.filter((a) => a.severity === "medium").length;

  return (
    <div>
      <PageHeader
        title="Alerts"
        description="Derived directly from the registry — nothing here is stored, so an alert disappears the moment its cause is fixed."
        actions={
          <Select value={severity} onChange={(e) => setSeverity(e.target.value as AlertSeverity | "")}>
            <option value="">All severities</option>
            <option value="high">High only</option>
            <option value="medium">Medium only</option>
          </Select>
        }
      />

      <div className="mb-6 grid grid-cols-2 gap-3 sm:grid-cols-3">
        <Card><p className="text-[11px] uppercase text-slate-500">Total alerts</p><p className="mt-1 text-2xl font-bold text-white">{alerts.length}</p></Card>
        <Card><p className="text-[11px] uppercase text-slate-500">High severity</p><p className="mt-1 text-2xl font-bold text-red-400">{high}</p></Card>
        <Card><p className="text-[11px] uppercase text-slate-500">Medium severity</p><p className="mt-1 text-2xl font-bold text-amber-400">{medium}</p></Card>
      </div>

      {alerts.length === 0 ? (
        <EmptyState icon={<IconAlert className="h-8 w-8" />} title="No alerts" detail="Every approved institution is reporting, contactable, and within its overdue threshold." />
      ) : (
        <div className="space-y-2">
          {alerts.map((a, i) => (
            <Link
              key={i}
              href={`/${encodeURIComponent(a.institutionId)}`}
              className="flex items-center gap-3 rounded-lg border border-slate-800 bg-[#0B1220] px-4 py-3 hover:border-slate-700"
            >
              <Badge tone={a.severity === "high" ? "red" : "amber"}>{a.severity}</Badge>
              <span className="text-sm text-slate-300">
                <span className="font-semibold text-white">{a.name}</span> — {a.message}
              </span>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
