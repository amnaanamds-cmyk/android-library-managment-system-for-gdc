"use client";

import React, { useEffect, useMemo, useRef } from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";
import { useAuth } from "@/lib/auth-context";
import { publishSnapshot } from "@/lib/directorate";
import { COLLECTIONS, isActiveIssue, isOverdue } from "@/lib/schema";

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

  // Same rule as the desktop dashboard: a tile is neutral unless its own value
  // means something. Four gradient tiles in four different hues gave the
  // Overdue figure - the only one that ever needs acting on - no more weight
  // than the book count.
  const stats: { name: string; value: number; icon: string; tone?: "warning" | "danger" }[] = [
    { name: "Books Catalogued", value: totalBooks, icon: "📚" },
    { name: "Registered Members", value: totalMembers, icon: "👥" },
    { name: "Books on Loan", value: activeLoans, icon: "🔄" },
    {
      name: "Overdue Loans",
      value: overdueIssues.length,
      icon: "⏰",
      tone: overdueIssues.length > 0 ? "danger" : undefined,
    },
  ];

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-ink">Dashboard</h1>
        <p className="text-sm text-muted">
          Live figures for {profile?.institutionId || "your institution"}
        </p>
      </div>

      {booksError && (
        <div className="rounded-xl border border-danger/40 bg-danger-soft p-4 text-sm text-danger">
          <p className="font-bold">Cannot read this institution&apos;s data.</p>
          <p className="mt-1 font-mono text-xs opacity-80">{booksError}</p>
          <p className="mt-2 text-xs opacity-80">
            A permission error here usually means your profile&apos;s{" "}
            <span className="font-mono">institutionId</span> does not match the institution you are
            trying to open, or the current <span className="font-mono">firestore.rules</span> have
            not been deployed.
          </p>
        </div>
      )}

      {/* Stats */}
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4">
        {stats.map((stat) => (
          <div
            key={stat.name}
            className={`rounded-xl border border-line bg-surface p-5 border-l-[3px] ${
              stat.tone === "danger"
                ? "border-l-danger"
                : stat.tone === "warning"
                  ? "border-l-warning"
                  : "border-l-line-strong"
            }`}
          >
            <p className="flex items-center gap-2 text-[11px] font-bold uppercase tracking-wide text-muted">
              <span className="text-sm">{stat.icon}</span>
              {stat.name}
            </p>
            <h2
              className={`mt-2 text-3xl font-extrabold ${
                stat.tone === "danger"
                  ? "text-danger"
                  : stat.tone === "warning"
                    ? "text-warning"
                    : "text-ink"
              }`}
            >
              {loading ? (
                <span className="inline-block h-7 w-14 animate-pulse rounded bg-surface-2" />
              ) : (
                stat.value.toLocaleString()
              )}
            </h2>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        {/* Recently added */}
        <div className="rounded-xl border border-line bg-surface p-5">
          <h3 className="mb-4 text-sm font-bold uppercase tracking-wide text-muted">
            Newly Added Books
          </h3>
          <div className="divide-y divide-line">
            {[...books]
              .sort((a, b) => (Number(b.lastUpdated) || 0) - (Number(a.lastUpdated) || 0))
              .slice(0, 5)
              .map((book, idx) => (
                <div key={book.id || idx} className="flex items-center justify-between gap-3 py-2.5">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-semibold text-ink">{book.title}</p>
                    <p className="truncate text-xs text-muted">{book.author || "Unknown author"}</p>
                  </div>
                  <span className="shrink-0 rounded border border-line px-2 py-0.5 text-xs text-muted">
                    {book.category || book.subject || "General"}
                  </span>
                </div>
              ))}
            {books.length === 0 && (
              <p className="py-2 text-xs text-muted">No books catalogued yet.</p>
            )}
          </div>
        </div>

        {/* On loan */}
        <div className="rounded-xl border border-line bg-surface p-5">
          <h3 className="mb-4 text-sm font-bold uppercase tracking-wide text-muted">
            Books on Loan
          </h3>
          <div className="divide-y divide-line">
            {activeIssues.slice(0, 5).map((issue, idx) => (
              <div key={issue.id || idx} className="flex items-center justify-between gap-3 py-2.5">
                <div className="min-w-0">
                  <p className="truncate text-sm font-semibold text-ink">
                    {issue.bookTitle || `Book ${issue.bookId}`}
                  </p>
                  <p className="truncate text-xs text-muted">
                    {issue.memberName || `Member ${issue.memberId}`}
                  </p>
                </div>
                <span
                  className={`shrink-0 text-xs ${
                    isOverdue(issue) ? "font-bold text-danger" : "text-muted"
                  }`}
                >
                  Due {issue.dueDate || "—"}
                </span>
              </div>
            ))}
            {activeIssues.length === 0 && (
              <p className="py-2 text-xs text-muted">Nothing is on loan right now.</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
