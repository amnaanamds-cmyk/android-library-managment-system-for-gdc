"use client";

import React, { useEffect, useState } from "react";
import Link from "next/link";
import { doc, getDoc, setDoc } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { useAuth } from "@/lib/auth-context";
import { useDirectorateNetwork, DirectorateSnapshot } from "@/lib/directorate";
import { MIS_COLLECTIONS } from "@/lib/schema";
import { computeAlerts, rollupByDistrict } from "@/lib/analytics";
import { useFollowups } from "@/lib/registry-admin";
import { useAuditLog } from "@/lib/audit";
import { PageHeader, StatTile, Card, SectionTitle, Badge, Spinner, relativeTime, numberFmt } from "@/components/ui";
import { IconAlert, IconDistrict, IconAudit, IconFollowup, IconAssistant } from "@/components/icons";

function todayDateId(): string {
  return new Date().toISOString().slice(0, 10);
}

/**
 * Today's network briefing. Generated once per calendar date, by whichever
 * directorate admin opens this page first that day — there is no Cloud
 * Functions plan behind this project (see firestore.rules), so "whichever
 * admin opens the portal next" stands in for a nightly cron. The
 * directorate_ai_briefings/{date} document is the throttle: once it exists,
 * every later admin that day just reads it.
 */
function useDailyBriefing(colleges: DirectorateSnapshot[], ready: boolean) {
  const { user } = useAuth();
  const [text, setText] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!ready || !user) {
      setLoading(false);
      return;
    }
    if (colleges.length === 0) {
      setLoading(false);
      return;
    }
    let cancelled = false;

    (async () => {
      const ref = doc(db, MIS_COLLECTIONS.aiBriefings, todayDateId());
      try {
        const existing = await getDoc(ref);
        if (existing.exists()) {
          if (!cancelled) setText(existing.data().text as string);
          return;
        }

        const token = await user.getIdToken();
        const res = await fetch("/api/agent/nightly-briefing", {
          method: "POST",
          headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
          body: JSON.stringify({ colleges }),
        });
        const data = await res.json();
        if (!res.ok) {
          if (!cancelled) setError(data.error || "Could not generate today's briefing.");
          return;
        }
        if (!cancelled) setText(data.text);
        // Best-effort: if another admin opened the page at the same moment
        // and already wrote today's doc, this simply overwrites it — the
        // rule allows that by design (see firestore.rules), so there is
        // nothing to reconcile.
        await setDoc(ref, { text: data.text, generatedByUid: user.uid, generatedAt: Date.now() });
      } catch {
        if (!cancelled) setError("Could not reach the assistant to generate today's briefing.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
    // colleges is refetched live; only its size (any data at all vs. none)
    // should re-trigger this, not every row update.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ready, user, colleges.length]);

  return { text, loading, error };
}

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
  const briefing = useDailyBriefing(colleges, !loading);

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

      {briefing.error && (
        <Card className="mb-6 border-red-900/60 bg-red-500/5">
          <p className="text-sm text-red-300/80">{briefing.error}</p>
        </Card>
      )}
      {(briefing.loading || briefing.text) && !briefing.error && (
        <Card className="mb-6 border-amber-900/40 bg-amber-500/5">
          <SectionTitle
            icon={<IconAssistant className="h-4 w-4" />}
            action={
              <Link href="/assistant" className="text-xs font-semibold text-amber-400 hover:text-amber-300">
                Ask a follow-up →
              </Link>
            }
          >
            Today&apos;s briefing
          </SectionTitle>
          {briefing.loading ? (
            <p className="text-xs text-slate-500">Generating today&apos;s briefing…</p>
          ) : (
            <p className="text-sm leading-relaxed text-slate-300">{briefing.text}</p>
          )}
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
