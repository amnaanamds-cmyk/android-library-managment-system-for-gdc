"use client";

import React from "react";
import { useDirectorateNetwork } from "@/lib/directorate";
import { rollupByDistrict } from "@/lib/analytics";
import { PageHeader, Card, Meter, Spinner, EmptyState, numberFmt } from "@/components/ui";
import { IconDistrict } from "@/components/icons";

export default function Districts() {
  const { colleges, loading } = useDirectorateNetwork();
  const rows = rollupByDistrict(colleges);
  const maxBooks = Math.max(1, ...rows.map((r) => r.books));

  if (loading) return <Spinner label="Loading districts…" />;

  return (
    <div>
      <PageHeader
        title="District Coverage"
        description="Institutions rolled up by district — where the network is thick, and where it is thin."
      />

      {rows.length === 0 ? (
        <EmptyState icon={<IconDistrict className="h-8 w-8" />} title="No institutions to group yet" />
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {rows.map((d) => (
            <Card key={d.district}>
              <div className="mb-3 flex items-center justify-between">
                <h3 className="text-sm font-bold text-white">{d.district}</h3>
                <span className="text-xs text-slate-500">
                  {d.colleges} {d.colleges === 1 ? "institution" : "institutions"}
                </span>
              </div>
              <Meter pct={(d.books / maxBooks) * 100} tone="blue" />
              <div className="mt-4 grid grid-cols-3 gap-3 text-xs">
                <div>
                  <p className="text-slate-500">Books</p>
                  <p className="font-semibold text-white">{numberFmt.format(d.books)}</p>
                </div>
                <div>
                  <p className="text-slate-500">Members</p>
                  <p className="font-semibold text-white">{numberFmt.format(d.members)}</p>
                </div>
                <div>
                  <p className="text-slate-500">Reporting</p>
                  <p className="font-semibold text-white">{d.reporting}/{d.colleges}</p>
                </div>
              </div>
              {d.overdue > 0 && (
                <p className="mt-3 text-xs text-red-400">{numberFmt.format(d.overdue)} overdue loans across this district</p>
              )}
            </Card>
          ))}
        </div>
      )}

      {rows.some((r) => r.district === "Unassigned") && (
        <p className="mt-4 text-xs text-slate-600">
          &ldquo;Unassigned&rdquo; means the college itself has not published a district field on its own
          registry snapshot — this page groups by whatever each college reports, and cannot set it for them.
        </p>
      )}
    </div>
  );
}
