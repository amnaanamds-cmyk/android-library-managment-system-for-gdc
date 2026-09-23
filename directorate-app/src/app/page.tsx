"use client";

import React from "react";
import Link from "next/link";
import { useDirectorateNetwork } from "@/lib/directorate";
import { computeAlerts, rollupByDistrict } from "@/lib/analytics";
import { useFollowups } from "@/lib/registry-admin";
import { useAuditLog } from "@/lib/audit";
import { PageHeader, StatTile, Card, SectionTitle, Badge, Spinner, relativeTime, numberFmt } from "@/components/ui";
import { IconAlert, IconDistrict, IconAudit, IconFollowup } from "@/components/icons";

/**
 * Executive summary. Deliberately shallow — every figure here links to the
 * section that goes deep on it, rather than trying to fit the whole MIS on
 * one screen the way the original single-page dashboard did.
 */
export default function Overview() {
  const { totals, loading, error, usedFallback, colleges } = useDirectorateNetwork();
  const alerts = computeAlerts(colleges);
  const districts = rollupByDistrict(colleges);
  const { items: followups } = useFollowups();
  const { entries: recentAudit } = useAuditLog(6);

  if (loading) return <Spinner label="Loading network data…" />;

  const openFollowups = followups.filter((f) => f.status === "open");
  const highAlerts = alerts.filter((a) => a.severity === "high");

  return (
    <div>
      <PageHeader
        title="Network Overview"
        description={`Aggregated figures across ${totals.colleges} approved ${totals.colleges === 1 ? "institution" : "institutions"} in ${districts.length} ${districts.length === 1 ? "district" : "districts"}.`}
      />

      {error && (
        <Card className="mb-6 border-red-900/60 bg-red-500/5">
          <p className="text-sm font-semibold text-red-300">Could not read the directorate registry.</p>
          <p className="mt-1 font-mono text-xs text-red-300/70">{error}</p>
        </Card>
      )}
      {usedFallback && (
        <Card className="mb-6 border-amber-900/60 bg-amber-500/5">
          <p className="text-sm font-semibold text-amber-200">No college has published a snapshot yet.</p>
          <p className="mt-1 text-xs text-amber-200/70">
            Showing the institution registry. Counts appear once each college&apos;s app completes a sync.
          </p>
        </Card>
      )}

      <div className="mb-7 grid grid-cols-2 gap-3 lg:grid-cols-4">
        <StatTile label="Institutions" value={totals.colleges} />
        <StatTile label="Total Books" value={totals.books} />
        <StatTile label="Total Members" value={totals.members} />
        <StatTile label="Active Loans" value={totals.activeLoans} tone="blue" />
        <StatTile label="Overdue Loans" value={totals.overdue} tone={totals.overdue > 0 ? "red" : "default"} />
        <StatTile label="Reservations" value={totals.reservations} />
        <StatTile label="Outstanding Fines" value={totals.finesOutstanding} prefix="Rs " tone="amber" />
        <StatTile
          label="Reporting"
          value={`${totals.reporting}/${totals.colleges}`}
          tone={totals.reporting < totals.colleges ? "amber" : "emerald"}
        />
      </div>

      <div className="grid gap-5 lg:grid-cols-2">
        <Card>
          <SectionTitle
            icon={<IconAlert className="h-4 w-4" />}
            action={
              <Link href="/alerts" className="text-xs font-semibold text-amber-400 hover:text-amber-300">
                View all {alerts.length} →
              </Link>
            }
          >
            Attention needed
          </SectionTitle>
          {highAlerts.length === 0 ? (
            <p className="py-6 text-center text-xs text-slate-600">No high-severity alerts across the network.</p>
          ) : (
            <div className="space-y-2.5">
              {highAlerts.slice(0, 5).map((a, i) => (
                <Link
                  key={i}
                  href={`/${encodeURIComponent(a.institutionId)}`}
                  className="flex items-start gap-2.5 rounded-md border border-slate-800/80 px-3 py-2.5 text-xs hover:border-slate-700"
                >
                  <Badge tone="red">High</Badge>
                  <span className="text-slate-300">
                    <span className="font-semibold text-white">{a.name}</span> — {a.message}
                  </span>
                </Link>
              ))}
            </div>
          )}
        </Card>

        <Card>
          <SectionTitle
            icon={<IconDistrict className="h-4 w-4" />}
            action={
              <Link href="/districts" className="text-xs font-semibold text-amber-400 hover:text-amber-300">
                All districts →
              </Link>
            }
          >
            District coverage
          </SectionTitle>
          {districts.length === 0 ? (
            <p className="py-6 text-center text-xs text-slate-600">No institutions yet.</p>
          ) : (
            <div className="space-y-2.5">
              {districts.slice(0, 5).map((d) => (
                <div key={d.district} className="flex items-center justify-between text-xs">
                  <span className="font-semibold text-slate-300">{d.district}</span>
                  <span className="text-slate-500">
                    {d.colleges} {d.colleges === 1 ? "institution" : "institutions"} · {numberFmt.format(d.books)} books
                  </span>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card>
          <SectionTitle
            icon={<IconFollowup className="h-4 w-4" />}
            action={
              <Link href="/followups" className="text-xs font-semibold text-amber-400 hover:text-amber-300">
                All follow-ups →
              </Link>
            }
          >
            Open follow-ups ({openFollowups.length})
          </SectionTitle>
          {openFollowups.length === 0 ? (
            <p className="py-6 text-center text-xs text-slate-600">Nothing currently open.</p>
          ) : (
            <div className="space-y-2.5">
              {openFollowups.slice(0, 5).map((f) => (
                <div key={f.id} className="rounded-md border border-slate-800/80 px-3 py-2.5 text-xs">
                  <p className="font-semibold text-slate-200">{f.title}</p>
                  <p className="mt-0.5 text-slate-500">
                    {f.collegeName} {f.dueDate ? `· due ${f.dueDate}` : ""}
                  </p>
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card>
          <SectionTitle
            icon={<IconAudit className="h-4 w-4" />}
            action={
              <Link href="/audit" className="text-xs font-semibold text-amber-400 hover:text-amber-300">
                Full log →
              </Link>
            }
          >
            Recent directorate activity
          </SectionTitle>
          {recentAudit.length === 0 ? (
            <p className="py-6 text-center text-xs text-slate-600">No actions recorded yet.</p>
          ) : (
            <div className="space-y-2.5">
              {recentAudit.map((e) => (
                <div key={e.id} className="flex items-center justify-between text-xs">
                  <span className="text-slate-300">
                    <span className="font-mono text-slate-500">{e.action}</span> · {e.target}
                  </span>
                  <span className="shrink-0 text-slate-600">{relativeTime(e.at)}</span>
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}
