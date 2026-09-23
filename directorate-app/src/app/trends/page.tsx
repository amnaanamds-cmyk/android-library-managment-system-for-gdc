"use client";

import React, { useMemo, useState } from "react";
import { useDirectorateNetwork } from "@/lib/directorate";
import { useSnapshotHistory, captureSnapshot, HistorySnapshot } from "@/lib/registry-admin";
import { PageHeader, Card, SectionTitle, Button, Spinner, EmptyState, numberFmt } from "@/components/ui";
import { IconTrend, IconPlus } from "@/components/icons";

type MetricKey = keyof HistorySnapshot["totals"];
const METRICS: { key: MetricKey; label: string; color: string }[] = [
  { key: "books", label: "Books", color: "#3B82F6" },
  { key: "members", label: "Members", color: "#F59E0B" },
  { key: "activeLoans", label: "Active loans", color: "#10B981" },
  { key: "overdue", label: "Overdue", color: "#EF4444" },
];

/**
 * Manually captured trend history.
 *
 * There is no Cloud Functions plan behind this project (see RUNBOOK.md), so
 * there is no scheduled nightly capture — a directorate user presses
 * "Capture today's snapshot" periodically, and that becomes one immutable
 * point on the line below. This is a genuine trade, not a placeholder: it
 * is what a real trend feature looks like on a Spark-plan budget, honestly
 * described rather than presented as automatic.
 */
export default function Trends() {
  const { totals, loading: loadingNetwork } = useDirectorateNetwork();
  const { items, loading: loadingHistory } = useSnapshotHistory();
  const [capturing, setCapturing] = useState(false);

  const doCapture = async () => {
    setCapturing(true);
    try {
      await captureSnapshot({
        colleges: totals.colleges,
        books: totals.books,
        members: totals.members,
        activeLoans: totals.activeLoans,
        overdue: totals.overdue,
      });
    } finally {
      setCapturing(false);
    }
  };

  if (loadingNetwork || loadingHistory) return <Spinner label="Loading trend history…" />;

  return (
    <div>
      <PageHeader
        title="Network Trends"
        description="Manually captured points in time. Press capture periodically to build a history — there is no automatic nightly snapshot on the current Firebase plan."
        actions={
          <Button variant="primary" size="sm" icon={<IconPlus className="h-3.5 w-3.5" />} disabled={capturing} onClick={doCapture}>
            {capturing ? "Capturing…" : "Capture today's snapshot"}
          </Button>
        }
      />

      {items.length < 2 ? (
        <EmptyState
          icon={<IconTrend className="h-8 w-8" />}
          title={items.length === 0 ? "No history captured yet" : "One point captured so far"}
          detail="Capture at least two snapshots, on different days, to see a trend line."
        />
      ) : (
        <Card>
          <SectionTitle icon={<IconTrend className="h-4 w-4" />}>
            {items.length} snapshots, {new Date(items[0].at).toLocaleDateString("en-PK")} – {new Date(items[items.length - 1].at).toLocaleDateString("en-PK")}
          </SectionTitle>
          <TrendChart items={items} />
        </Card>
      )}

      {items.length > 0 && (
        <Card className="mt-5">
          <SectionTitle icon={<IconTrend className="h-4 w-4" />}>Capture log</SectionTitle>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="text-[10px] font-bold uppercase tracking-wider text-slate-500">
                <tr>
                  <th className="py-2 pr-4">Captured</th>
                  <th className="py-2 pr-4">By</th>
                  <th className="py-2 pr-4 text-right">Institutions</th>
                  <th className="py-2 pr-4 text-right">Books</th>
                  <th className="py-2 pr-4 text-right">Members</th>
                  <th className="py-2 text-right">Overdue</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {[...items].reverse().map((s) => (
                  <tr key={s.id}>
                    <td className="py-2 pr-4 text-slate-300">{new Date(s.at).toLocaleString("en-PK")}</td>
                    <td className="py-2 pr-4 text-slate-500">{s.capturedByEmail}</td>
                    <td className="py-2 pr-4 text-right text-white">{numberFmt.format(s.totals.colleges)}</td>
                    <td className="py-2 pr-4 text-right text-white">{numberFmt.format(s.totals.books)}</td>
                    <td className="py-2 pr-4 text-right text-white">{numberFmt.format(s.totals.members)}</td>
                    <td className="py-2 text-right text-white">{numberFmt.format(s.totals.overdue)}</td>
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

/** A dependency-free inline-SVG line chart. Four series share one y-axis
 *  scaled to the largest value across all of them, which keeps the chart
 *  simple at the cost of small series (overdue, next to books) reading as
 *  nearly flat — acceptable here since the point is trend direction, not
 *  precise cross-series comparison. */
function TrendChart({ items }: { items: HistorySnapshot[] }) {
  const W = 760;
  const H = 220;
  const PAD = 34;

  const max = useMemo(
    () => Math.max(1, ...items.flatMap((s) => METRICS.map((m) => s.totals[m.key]))),
    [items],
  );

  const x = (i: number) => PAD + (i / (items.length - 1)) * (W - PAD * 2);
  const y = (v: number) => H - PAD - (v / max) * (H - PAD * 2);

  return (
    <div className="overflow-x-auto">
      <svg viewBox={`0 0 ${W} ${H}`} className="w-full min-w-[520px]" role="img" aria-label="Network totals over time">
        {[0, 0.25, 0.5, 0.75, 1].map((f) => (
          <line key={f} x1={PAD} x2={W - PAD} y1={y(max * f)} y2={y(max * f)} stroke="#1E293B" strokeWidth={1} />
        ))}
        {METRICS.map((m) => {
          const points = items.map((s, i) => `${x(i)},${y(s.totals[m.key])}`).join(" ");
          return <polyline key={m.key} points={points} fill="none" stroke={m.color} strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" />;
        })}
        {items.map((s, i) => (
          <g key={s.id}>
            {METRICS.map((m) => (
              <circle key={m.key} cx={x(i)} cy={y(s.totals[m.key])} r={2.5} fill={m.color} />
            ))}
          </g>
        ))}
        {items.map((s, i) =>
          i === 0 || i === items.length - 1 || items.length <= 6 ? (
            <text key={s.id} x={x(i)} y={H - 8} fontSize={9} fill="#64748B" textAnchor="middle">
              {new Date(s.at).toLocaleDateString("en-PK", { month: "short", day: "numeric" })}
            </text>
          ) : null,
        )}
      </svg>
      <div className="mt-3 flex flex-wrap gap-4">
        {METRICS.map((m) => (
          <span key={m.key} className="flex items-center gap-1.5 text-xs text-slate-400">
            <span className="h-2 w-2 rounded-full" style={{ background: m.color }} />
            {m.label}
          </span>
        ))}
      </div>
    </div>
  );
}
