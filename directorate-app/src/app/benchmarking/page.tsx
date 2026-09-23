"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useDirectorateNetwork } from "@/lib/directorate";
import { rankBy, networkAverage, scoreCompliance } from "@/lib/analytics";
import { PageHeader, Card, SectionTitle, Meter, Badge, Select, Spinner, EmptyState, numberFmt } from "@/components/ui";
import { IconBench } from "@/components/icons";

type RankKey = "booksCount" | "activeLoans" | "membersCount";

const KEY_LABEL: Record<RankKey, string> = {
  booksCount: "Books",
  activeLoans: "Active loans",
  membersCount: "Members",
};

export default function Benchmarking() {
  const { colleges, loading } = useDirectorateNetwork();
  const [key, setKey] = useState<RankKey>("booksCount");
  const ranked = rankBy(colleges, key);
  const avg = networkAverage(colleges, key);
  const compliance = colleges.map(scoreCompliance).sort((a, b) => a.score - b.score);

  if (loading) return <Spinner label="Loading benchmarks…" />;
  if (colleges.length === 0) return <EmptyState icon={<IconBench className="h-8 w-8" />} title="No institutions to benchmark yet" />;

  return (
    <div>
      <PageHeader
        title="Benchmarking & Compliance"
        description="Where each institution sits relative to the network, and a plain compliance score for each."
      />

      <div className="grid gap-5 lg:grid-cols-2">
        <Card>
          <SectionTitle
            icon={<IconBench className="h-4 w-4" />}
            action={
              <Select value={key} onChange={(e) => setKey(e.target.value as RankKey)}>
                {(Object.keys(KEY_LABEL) as RankKey[]).map((k) => <option key={k} value={k}>{KEY_LABEL[k]}</option>)}
              </Select>
            }
          >
            Ranked by {KEY_LABEL[key].toLowerCase()}
          </SectionTitle>
          <p className="mb-4 text-xs text-slate-500">
            Network average: <span className="font-semibold text-slate-300">{numberFmt.format(Math.round(avg))}</span>
          </p>
          <div className="space-y-3">
            {ranked.map((r, i) => (
              <Link key={r.institutionId} href={`/${encodeURIComponent(r.institutionId)}`} className="block">
                <div className="mb-1 flex items-center justify-between text-xs">
                  <span className="flex items-center gap-2 font-semibold text-slate-300">
                    <span className="w-5 text-right font-mono text-slate-600">{i + 1}</span>
                    {r.name}
                  </span>
                  <span className="text-slate-500">
                    {numberFmt.format(r.value)} · P{r.percentile}
                  </span>
                </div>
                <Meter pct={r.percentile} tone={r.value >= avg ? "emerald" : "amber"} />
              </Link>
            ))}
          </div>
        </Card>

        <Card>
          <SectionTitle icon={<IconBench className="h-4 w-4" />}>Compliance scorecard</SectionTitle>
          <p className="mb-4 text-xs text-slate-500">
            Four checks: has ever synced, synced within 48h, contact details on file, overdue ratio under 20%.
          </p>
          <div className="space-y-3">
            {compliance.map((c) => (
              <Link key={c.institutionId} href={`/${encodeURIComponent(c.institutionId)}`} className="block rounded-md border border-slate-800/80 px-3 py-2.5 hover:border-slate-700">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-semibold text-slate-200">{c.name}</span>
                  <Badge tone={c.score >= 75 ? "emerald" : c.score >= 50 ? "amber" : "red"}>{c.score}%</Badge>
                </div>
                <div className="mt-1.5 flex flex-wrap gap-1">
                  {c.checks.map((chk) => (
                    <span
                      key={chk.key}
                      title={chk.detail}
                      className={`h-1.5 flex-1 rounded-full ${chk.pass ? "bg-emerald-600" : "bg-red-900"}`}
                    />
                  ))}
                </div>
              </Link>
            ))}
          </div>
        </Card>
      </div>
    </div>
  );
}
