"use client";

import React, { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { collection, doc, getDoc, getDocs, DocumentData } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { ROOT_COLLECTIONS, COLLECTIONS } from "@/lib/schema";
import { isStale, fetchSnapshot, DirectorateSnapshot } from "@/lib/directorate";

const numberFmt = new Intl.NumberFormat("en-PK");

/**
 * Single-college drill-down.
 *
 * The directorate is a read-only oversight role and is deliberately NOT a
 * member of any college, so tenant collections (members, loans) stay closed to
 * it under the security rules. What it can legitimately see is:
 *
 *   - the college's published aggregate snapshot  (/directorate_index)
 *   - the college's public catalogue              (/institutions/{id}/books,
 *                                                  readable by anyone, since
 *                                                  the OPAC is public)
 *
 * Anything requiring patron-level data stays with the college. That boundary
 * is enforced by the rules, not just by this page.
 */
export default function CollegeDetail() {
  const params = useParams<{ collegeId: string }>();
  const collegeId = decodeURIComponent(
    Array.isArray(params.collegeId) ? params.collegeId[0] : params.collegeId || "",
  );

  const [snapshot, setSnapshot] = useState<DirectorateSnapshot | null>(null);
  const [institution, setInstitution] = useState<DocumentData | null>(null);
  const [books, setBooks] = useState<DocumentData[]>([]);
  const [catalogueError, setCatalogueError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!collegeId) return;
    let cancelled = false;

    (async () => {
      setLoading(true);
      try {
        const [snap, instSnap] = await Promise.all([
          fetchSnapshot(collegeId),
          getDoc(doc(db, ROOT_COLLECTIONS.institutions, collegeId)),
        ]);
        if (cancelled) return;

        if (snap) setSnapshot(snap);
        if (instSnap.exists()) setInstitution(instSnap.data());
      } finally {
        if (!cancelled) setLoading(false);
      }

      // The public catalogue is a separate, best-effort read: if a college has
      // locked it down, the rest of the page must still render.
      try {
        const booksSnap = await getDocs(
          collection(db, ROOT_COLLECTIONS.institutions, collegeId, COLLECTIONS.books),
        );
        if (cancelled) return;
        setBooks(
          booksSnap.docs
            .map((d) => ({ id: d.id, ...d.data() }) as DocumentData)
            .filter((b) => !b.deleted),
        );
      } catch (err) {
        if (!cancelled) setCatalogueError((err as Error).message);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [collegeId]);

  const categories = useMemo(() => {
    const counts = new Map<string, number>();
    for (const b of books) {
      const key = (b.category as string) || "Uncategorized";
      counts.set(key, (counts.get(key) || 0) + 1);
    }
    return [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, 8);
  }, [books]);

  const availability = useMemo(() => {
    const issued = books.filter((b) => b.status === "Issued").length;
    return { issued, available: books.length - issued };
  }, [books]);

  if (loading) {
    return (
      <div className="flex flex-col items-center gap-4 py-24">
        <div className="h-10 w-10 animate-spin rounded-full border-4 border-blue-600 border-t-transparent" />
        <p className="text-xs font-bold uppercase tracking-widest text-slate-500">
          Loading {collegeId}…
        </p>
      </div>
    );
  }

  const name = snapshot?.name || institution?.name || collegeId;

  return (
    <div className="space-y-8">
      <div>
        <Link href="/director" className="text-xs font-bold text-blue-400 hover:text-blue-300">
          ← Back to network overview
        </Link>
        <div className="mt-3 flex flex-wrap items-end justify-between gap-4">
          <div>
            <h1 className="text-3xl font-extrabold tracking-tight text-white">{name}</h1>
            <p className="mt-1 font-mono text-xs uppercase tracking-wider text-slate-500">
              {collegeId}
              {snapshot?.district ? ` · ${snapshot.district}` : ""}
            </p>
          </div>
          {snapshot && (
            <div className="text-right">
              <p className="text-[10px] font-black uppercase tracking-widest text-slate-500">
                Last published
              </p>
              <p
                className={`text-sm font-bold ${
                  isStale(snapshot) ? "text-amber-400" : "text-emerald-400"
                }`}
              >
                {snapshot.lastSynced
                  ? new Date(snapshot.lastSynced).toLocaleString("en-PK")
                  : "Never"}
              </p>
              <p className="text-[10px] uppercase tracking-wider text-slate-600">
                {snapshot.source === "summary" ? "server rollup" : snapshot.source}
              </p>
            </div>
          )}
        </div>
      </div>

      {!snapshot && (
        <div className="rounded-xl border border-amber-500/30 bg-amber-500/10 p-4 text-sm text-amber-200">
          <p className="font-bold">This college has not published a snapshot yet.</p>
          <p className="mt-1 text-xs opacity-80">
            Aggregate counts appear once its desktop, web, or Android app completes a sync while
            signed in. The catalogue figures below are read directly from the public catalogue.
          </p>
        </div>
      )}

      {snapshot && (
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          <Stat label="Books" value={snapshot.totalBooks} />
          <Stat label="Members" value={snapshot.members} />
          <Stat label="Active Loans" value={snapshot.issued} accent="text-blue-400" />
          <Stat
            label="Overdue"
            value={snapshot.overdue}
            accent={snapshot.overdue > 0 ? "text-red-400" : "text-slate-400"}
          />
          <Stat label="E-Books" value={snapshot.totalEbooks} />
          <Stat label="Reservations" value={snapshot.reservations} />
          <Stat
            label="Unsynced Records"
            value={snapshot.recordsMissingSyncEnvelope}
            accent={
              snapshot.recordsMissingSyncEnvelope > 0 ? "text-amber-400" : "text-slate-400"
            }
          />
          <Stat
            label="Utilisation"
            value={
              snapshot.totalBooks > 0
                ? Math.round((snapshot.issued / snapshot.totalBooks) * 100)
                : 0
            }
            suffix="%"
          />
        </div>
      )}

      {/* Public catalogue analysis */}
      <div className="grid gap-6 lg:grid-cols-2">
        <div className="rounded-2xl border border-blue-950 bg-[#070F1E] p-6 shadow-xl">
          <h3 className="mb-4 text-lg font-bold text-white">Catalogue by Category</h3>
          {catalogueError ? (
            <p className="text-xs text-slate-500">
              Catalogue not readable for this college ({catalogueError}).
            </p>
          ) : categories.length === 0 ? (
            <p className="text-xs text-slate-500">No catalogue records found.</p>
          ) : (
            <div className="space-y-3">
              {categories.map(([label, count]) => {
                const pct = Math.round((count / books.length) * 100);
                return (
                  <div key={label}>
                    <div className="mb-1 flex justify-between text-xs">
                      <span className="font-semibold text-slate-300">{label}</span>
                      <span className="text-slate-500">
                        {numberFmt.format(count)} · {pct}%
                      </span>
                    </div>
                    <div className="h-1.5 overflow-hidden rounded-full bg-blue-950">
                      <div
                        className="h-full rounded-full bg-gradient-to-r from-blue-600 to-[#C8A84B]"
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>

        <div className="rounded-2xl border border-blue-950 bg-[#070F1E] p-6 shadow-xl">
          <h3 className="mb-4 text-lg font-bold text-white">Catalogue Availability</h3>
          {catalogueError ? (
            <p className="text-xs text-slate-500">Not available.</p>
          ) : (
            <>
              <div className="grid grid-cols-2 gap-4">
                <div className="rounded-xl border border-emerald-900/40 bg-emerald-500/5 p-4">
                  <p className="text-[10px] font-black uppercase tracking-widest text-emerald-500">
                    On shelf
                  </p>
                  <p className="mt-1 text-3xl font-black text-emerald-400">
                    {numberFmt.format(availability.available)}
                  </p>
                </div>
                <div className="rounded-xl border border-blue-900/40 bg-blue-500/5 p-4">
                  <p className="text-[10px] font-black uppercase tracking-widest text-blue-500">
                    On loan
                  </p>
                  <p className="mt-1 text-3xl font-black text-blue-400">
                    {numberFmt.format(availability.issued)}
                  </p>
                </div>
              </div>
              <p className="mt-4 text-[11px] text-slate-500">
                Counted from {numberFmt.format(books.length)} catalogue records readable by the
                directorate. Patron-level records remain private to the college.
              </p>
            </>
          )}
        </div>
      </div>

      {(institution?.email || institution?.phone || snapshot?.contactEmail) && (
        <div className="rounded-2xl border border-blue-950 bg-[#070F1E] p-6">
          <h3 className="mb-3 text-sm font-bold uppercase tracking-wider text-slate-400">
            Contact
          </h3>
          <dl className="grid gap-3 text-sm sm:grid-cols-3">
            {(snapshot?.contactEmail || institution?.email) && (
              <div>
                <dt className="text-[10px] uppercase tracking-widest text-slate-600">Email</dt>
                <dd className="text-slate-300">{snapshot?.contactEmail || institution?.email}</dd>
              </div>
            )}
            {(snapshot?.phone || institution?.phone) && (
              <div>
                <dt className="text-[10px] uppercase tracking-widest text-slate-600">Phone</dt>
                <dd className="text-slate-300">{snapshot?.phone || institution?.phone}</dd>
              </div>
            )}
            {(snapshot?.district || institution?.address) && (
              <div>
                <dt className="text-[10px] uppercase tracking-widest text-slate-600">Address</dt>
                <dd className="text-slate-300">{snapshot?.district || institution?.address}</dd>
              </div>
            )}
          </dl>
        </div>
      )}
    </div>
  );
}

function Stat({
  label,
  value,
  accent = "text-white",
  prefix = "",
  suffix = "",
}: {
  label: string;
  value: number;
  accent?: string;
  prefix?: string;
  suffix?: string;
}) {
  return (
    <div className="rounded-2xl border border-blue-950 bg-[#070F1E] p-5 shadow-xl">
      <p className="mb-1 text-[10px] font-black uppercase tracking-widest text-slate-500">
        {label}
      </p>
      <p className={`text-2xl font-black tracking-tighter ${accent}`}>
        {prefix}
        {numberFmt.format(value)}
        {suffix}
      </p>
    </div>
  );
}
