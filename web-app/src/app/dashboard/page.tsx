"use client";

import React, { useEffect, useMemo, useRef } from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";
import { useAuth } from "@/lib/auth-context";
import { publishSnapshot } from "@/lib/directorate";
import { COLLECTIONS, isActiveIssue, isOverdue } from "@/lib/schema";
import { StatCard } from "@/components/stat-card";

/** Republish the directorate snapshot at most this often per session. */
const PUBLISH_THROTTLE_MS = 5 * 60 * 1000;

export default function DashboardOverview() {
  const { profile } = useAuth();
  const { data: books, loading: loadingBooks, error: booksError } = useTenantCollection(COLLECTIONS.books);
  const { data: members, loading: loadingMembers } = useTenantCollection(COLLECTIONS.members);
  const { data: issues, loading: loadingIssues } = useTenantCollection(COLLECTIONS.issuedBooks);
  const { data: ebooks } = useTenantCollection(COLLECTIONS.ebooks);
  const { data: reservations } = useTenantCollection(COLLECTIONS.reservations);

  // A loan is open when status === "Issued". The previous check tested a
  // `returned` boolean that no platform writes, so every loan ever recorded
  // counted as active and the figure only ever grew.
  const activeIssues = useMemo(() => issues.filter(isActiveIssue), [issues]);
  const overdueIssues = useMemo(() => issues.filter((i) => isOverdue(i)), [issues]);

  const totalBooks = books.length;
  const totalMembers = members.length;
  const activeLoans = activeIssues.length;

  const loading = loadingBooks || loadingMembers || loadingIssues;

  // ── Publish this college's aggregate snapshot for the directorate ─────────
  //
  // The Spark plan has no Cloud Functions, so the rollup the directorate reads
  // has to come from the clients. Publishing here means the registry stays
  // current simply because someone opened the dashboard.
  const lastPublished = useRef(0);
  useEffect(() => {
    if (loading || !profile?.institutionId) return;
    const now = Date.now();
    if (now - lastPublished.current < PUBLISH_THROTTLE_MS) return;
    lastPublished.current = now;

    publishSnapshot(
      profile.institutionId,
      {
        booksCount: totalBooks,
        ebooksCount: ebooks.length,
        membersCount: totalMembers,
        activeLoans,
        overdueCount: overdueIssues.length,
        reservationsCount: reservations.length,
        finesOutstanding: activeIssues.reduce((sum, i) => sum + (Number(i.fine) || 0), 0),
      },
      "web",
    );
  }, [
    loading,
    profile?.institutionId,
    totalBooks,
    totalMembers,
    activeLoans,
    ebooks.length,
    reservations.length,
    overdueIssues.length,
    activeIssues,
  ]);

  const overdueRate = activeLoans > 0 ? (overdueIssues.length / activeLoans) * 100 : 0;

  const stats: Array<{
    name: string;
    value: number;
    icon: string;
    accent: "blue" | "green" | "amber" | "red";
    sub?: string;
    progress?: number;
  }> = [
    { name: "Total Books Cataloged", value: totalBooks, icon: "📚", accent: "blue" },
    { name: "Registered Members", value: totalMembers, icon: "👥", accent: "green" },
    { name: "Active Book Issues", value: activeLoans, icon: "🔄", accent: "amber" },
    {
      name: "Overdue Loans",
      value: overdueIssues.length,
      icon: "⏰",
      accent: "red",
      sub: activeLoans > 0 ? `${overdueRate.toFixed(0)}% of active loans` : undefined,
      progress: activeLoans > 0 ? overdueRate : undefined,
    },
  ];

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-extrabold text-[var(--text-primary)]">Dashboard Overview</h1>
        <p className="text-sm text-[var(--text-secondary)]">Real-time statistics for your active institution</p>
      </div>

      {booksError && (
        <div className="rounded-xl border border-red-500/30 bg-red-500/10 p-4 text-sm text-red-300">
          <p className="font-bold">Cannot read this institution&apos;s data.</p>
          <p className="mt-1 font-mono text-xs opacity-80">{booksError}</p>
          <p className="mt-2 text-xs text-red-200/70">
            A permission error here usually means your profile&apos;s{" "}
            <span className="font-mono">institutionId</span> does not match the institution you are
            trying to open, or the current <span className="font-mono">firestore.rules</span> have
            not been deployed.
          </p>
        </div>
      )}

      {/* Stats Grid */}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        {stats.map((stat, i) => (
          <StatCard
            key={i}
            icon={stat.icon}
            label={stat.name}
            accent={stat.accent}
            sub={stat.sub}
            progress={stat.progress}
            value={
              loading ? (
                <span className="inline-block h-6 w-12 animate-pulse rounded bg-[var(--surface-sunken)]" />
              ) : (
                stat.value
              )
            }
          />
        ))}
      </div>

      <div className="grid grid-cols-1 gap-8 lg:grid-cols-2">
        {/* Recent Books */}
        <div className="rounded-xl border border-blue-950 bg-[#070F1E] p-6 shadow-xl">
          <h3 className="mb-4 text-lg font-bold text-white">Newly Added Books</h3>
          <div className="space-y-4">
            {[...books]
              .sort((a, b) => (Number(b.lastUpdated) || 0) - (Number(a.lastUpdated) || 0))
              .slice(0, 5)
              .map((book, idx) => (
                <div key={book.id || idx} className="flex items-center justify-between border-b border-blue-950/40 pb-2">
                  <div>
                    <p className="text-sm font-semibold text-slate-200">{book.title}</p>
                    <p className="text-xs text-slate-400">{book.author || "Unknown Author"}</p>
                  </div>
                  <span className="rounded border border-blue-800 bg-blue-950 px-2 py-0.5 text-xs text-blue-400">
                    {book.category || book.subject || "General"}
                  </span>
                </div>
              ))}
            {books.length === 0 && <p className="text-xs text-slate-500">No books cataloged yet.</p>}
          </div>
        </div>

        {/* Active Issues */}
        <div className="rounded-xl border border-blue-950 bg-[#070F1E] p-6 shadow-xl">
          <h3 className="mb-4 text-lg font-bold text-white">Active Issues</h3>
          <div className="space-y-4">
            {activeIssues.slice(0, 5).map((issue, idx) => (
              <div key={issue.id || idx} className="flex items-center justify-between border-b border-blue-950/40 pb-2">
                <div>
                  <p className="text-sm font-semibold text-slate-200">
                    {issue.bookTitle || `Book ID: ${issue.bookId}`}
                  </p>
                  <p className="text-xs text-slate-400">
                    Issued to: {issue.memberName || `Member ID: ${issue.memberId}`}
                  </p>
                </div>
                <span className={`text-xs ${isOverdue(issue) ? "font-bold text-red-400" : "text-[#E6C96E]"}`}>
                  Due: {issue.dueDate || "N/A"}
                </span>
              </div>
            ))}
            {activeIssues.length === 0 && (
              <p className="text-xs text-slate-500">No active book loans at the moment.</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
