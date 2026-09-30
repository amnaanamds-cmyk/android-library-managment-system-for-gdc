"use client";

import React, { useMemo, useState } from "react";
import Link from "next/link";
import { useDirectorateNetwork } from "@/lib/directorate";
import { categoryNetworkTotals, rankByCategory } from "@/lib/analytics";
import { PageHeader, Card, SectionTitle, Meter, Badge, Select, Spinner, EmptyState, numberFmt } from "@/components/ui";
import { IconStock } from "@/components/icons";

/**
 * Cross-college stock by category — "which colleges are under-stocked in
 * Science" answered from the registry alone, never by reading a single
 * book record. Each college publishes only its own category counts (see
 * services/registry_service.py on the desktop client); this page is
 * nothing more than a ranking over numbers colleges already chose to
 * report about themselves.
 *
 * Colleges that haven't published category data yet (schemaVersion < 3,
 * or simply not synced since this shipped) are shown separately, never
 * folded into the ranking at zero — that would make every college read as
 * "understocked in everything" the moment it upgrades.
 */
export default function StockByCategory() {
  const { colleges, loading } = useDirectorateNetwork();
  const totals = useMemo(() => categoryNetworkTotals(colleges), [colleges]);
  const [category, setCategory] = useState<string>("");

  const activeCategory = category || totals[0]?.category || "";
  const { ranked, notReporting } = useMemo(
    () => rankByCategory(colleges, activeCategory),
    [colleges, activeCategory],
  );

  const networkAvg = useMemo(() => {
    if (ranked.length === 0) return 0;
    return ranked.reduce((n, r) => n + r.copies, 0) / ranked.length;
  }, [ranked]);

  if (loading) return <Spinner label="Loading network…" />;

  if (totals.length === 0) {
    return (
      <div>
        <PageHeader
          title="Stock by Category"
          description="Cross-college comparison of shelf stock, broken down by category."
        />
        <EmptyState
          icon={<IconStock className="h-8 w-8" />}
          title="No category data published yet"
          detail="Colleges publish this automatically on sync from a build that reports it. Nothing to compare until at least one has."
        />
      </div>
    );
  }

  return (
    <div>
      <PageHeader
        title="Stock by Category"
        description="Which colleges are carrying a category well, and which are running thin — computed entirely from counts colleges publish about themselves."
      />

      <Card className="mb-6">
        <SectionTitle
          icon={<IconStock className="h-4 w-4" />}
          action={
            <Select value={activeCategory} onChange={(e) => setCategory(e.target.value)}>
              {totals.map((t) => (
                <option key={t.category} value={t.category}>
                  {t.category} ({numberFmt.format(t.totalCopies)})
                </option>
              ))}
            </Select>
          }
        >
          {activeCategory}
        </SectionTitle>
        <p className="mb-4 text-xs text-slate-500">
          {numberFmt.format(ranked.reduce((n, r) => n + r.copies, 0))} copies across{" "}
          {ranked.length} reporting {ranked.length === 1 ? "college" : "colleges"} · network average{" "}
          <span className="font-semibold text-slate-300">{numberFmt.format(Math.round(networkAvg))}</span> copies
        </p>

        {ranked.length === 0 ? (
          <EmptyState icon={<IconStock className="h-8 w-8" />} title="No college has reported this category" />
        ) : (
          <div className="space-y-3">
            {ranked.map((r) => {
              const understocked = networkAvg > 0 && r.copies < networkAvg * 0.5;
              return (
                <Link key={r.docId} href={`/${encodeURIComponent(r.institutionId)}`} className="block">
                  <div className="mb-1 flex items-center justify-between text-xs">
                    <span className="flex items-center gap-2 font-semibold text-slate-300">
                      {r.name}
                      {r.district && <span className="text-slate-600">· {r.district}</span>}
                      {understocked && <Badge tone="amber">Understocked</Badge>}
                    </span>
                    <span className="text-slate-500">
                      {numberFmt.format(r.copies)} copies · P{r.percentile}
                    </span>
                  </div>
                  <Meter pct={r.percentile} tone={r.copies >= networkAvg ? "emerald" : "amber"} />
                </Link>
              );
            })}
          </div>
        )}
      </Card>

      {notReporting.length > 0 && (
        <details className="rounded-lg border border-slate-800 bg-[#0B1220] p-5">
          <summary className="cursor-pointer text-sm font-bold text-slate-400">
            Not yet reporting category data ({notReporting.length})
          </summary>
          <p className="mt-2 text-xs text-slate-500">
            These colleges haven&apos;t published a category breakdown yet — excluded above rather than
            shown as zero, since a college with no data reported is not the same as one that genuinely
            has nothing in this category.
          </p>
          <div className="mt-4 flex flex-wrap gap-2">
            {notReporting.map((c) => (
              <Link
                key={c.docId}
                href={`/${encodeURIComponent(c.institutionId)}`}
                className="rounded-md border border-slate-800/80 px-3 py-1.5 text-xs text-slate-400 hover:border-slate-700 hover:text-slate-200"
              >
                {c.name}
              </Link>
            ))}
          </div>
        </details>
      )}
    </div>
  );
}
