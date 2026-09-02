// lib/directorate.ts
//
// The directorate registry: one aggregate document per college at
// /directorate_index/{collegeId}, and the hooks the director portal reads it
// with.
//
// Why a registry at all: the directorate needs network-wide totals across
// every college, but tenant data lives under /institutions/{collegeId}/... and
// is readable only by that college's own staff. Firestore has no cross-tenant
// aggregate query, and Cloud Functions are not available on the Spark plan, so
// the rollup has to be published by the clients themselves.
//
// Each college's app writes its own snapshot after a sync. The document holds
// counts and a heartbeat only — no patron records, no book records — which is
// what makes it safe to expose to directorate staff.
//
// This replaces three competing registries that previously disagreed:
//   /colleges/{id}          written by web onboarding with `name`, read by the
//                           old director page expecting `collegeName`
//   /directorate_index/{id} written by Android + the desktop profile screen
//   /institutions/{id}      the tenant root, which carried some profile fields
// `directorate_index` is now canonical. `colleges` is kept as a legacy mirror.

"use client";

import { useEffect, useMemo, useState } from "react";
import {
  collection,
  doc,
  onSnapshot,
  setDoc,
  getDocs,
  DocumentData,
} from "firebase/firestore";
import { db } from "./firebase";
import { useAuth } from "./auth-context";
import { ROOT_COLLECTIONS } from "./schema";

/** Schema version, so a director portal can tell stale publishers apart. */
export const REGISTRY_SCHEMA_VERSION = 2;

/** Snapshots older than this are shown as stale in the portal. */
export const STALE_AFTER_MS = 48 * 60 * 60 * 1000; // 48 hours

export interface DirectorateSnapshot {
  /** Document id === institutionId. */
  institutionId: string;
  name: string;
  location?: string;
  district?: string;
  contactEmail?: string;
  phone?: string;

  // Aggregate counts
  booksCount: number;
  ebooksCount: number;
  membersCount: number;
  activeLoans: number;
  overdueCount: number;
  reservationsCount: number;
  finesOutstanding: number;

  // Provenance
  lastSyncAt: number;
  lastSyncPlatform: "web" | "desktop" | "android" | string;
  schemaVersion: number;
}

const EMPTY_COUNTS = {
  booksCount: 0,
  ebooksCount: 0,
  membersCount: 0,
  activeLoans: 0,
  overdueCount: 0,
  reservationsCount: 0,
  finesOutstanding: 0,
};

/**
 * Publish this college's aggregate snapshot.
 *
 * Called from the dashboard once its live collections have loaded, so the
 * registry stays current simply by someone using the app. Failures are
 * swallowed: a college that cannot publish its rollup must still be able to
 * run its library.
 */
export async function publishSnapshot(
  institutionId: string,
  snapshot: Partial<DirectorateSnapshot> & { name?: string },
  platform: DirectorateSnapshot["lastSyncPlatform"] = "web",
): Promise<boolean> {
  if (!institutionId) return false;
  try {
    await setDoc(
      doc(db, ROOT_COLLECTIONS.directorateIndex, institutionId),
      {
        ...EMPTY_COUNTS,
        ...snapshot,
        institutionId,
        lastSyncAt: Date.now(),
        lastSyncPlatform: platform,
        schemaVersion: REGISTRY_SCHEMA_VERSION,
      },
      { merge: true },
    );
    return true;
  } catch (err) {
    // Expected when the signed-in user is not staff of this college.
    console.warn("Directorate snapshot not published:", err);
    return false;
  }
}

/** True once the snapshot is old enough that its counts should not be trusted. */
export function isStale(snapshot: Pick<DirectorateSnapshot, "lastSyncAt">): boolean {
  if (!snapshot.lastSyncAt) return true;
  return Date.now() - snapshot.lastSyncAt > STALE_AFTER_MS;
}

function normalise(id: string, data: DocumentData): DirectorateSnapshot {
  // Tolerate every historical field spelling so colleges that last published
  // from an older build still appear in the portal rather than as a blank row.
  return {
    institutionId: data.institutionId || data.collegeId || data.college_id || id,
    name:
      data.name ||
      data.collegeName ||
      data.collegeFullName ||
      data.libraryName ||
      id,
    location: data.location || data.address || "",
    district: data.district || "",
    contactEmail: data.contactEmail || data.email || "",
    phone: data.phone || "",
    booksCount: Number(data.booksCount ?? data.books ?? 0),
    ebooksCount: Number(data.ebooksCount ?? 0),
    membersCount: Number(data.membersCount ?? data.members ?? 0),
    activeLoans: Number(data.activeLoans ?? data.circulationCount ?? 0),
    overdueCount: Number(data.overdueCount ?? 0),
    reservationsCount: Number(data.reservationsCount ?? 0),
    finesOutstanding: Number(data.finesOutstanding ?? 0),
    lastSyncAt: Number(data.lastSyncAt ?? data.lastSeen ?? data.lastUpdated ?? 0),
    lastSyncPlatform: data.lastSyncPlatform || "unknown",
    schemaVersion: Number(data.schemaVersion ?? 1),
  };
}

export interface NetworkTotals {
  colleges: number;
  reporting: number;
  stale: number;
  books: number;
  ebooks: number;
  members: number;
  activeLoans: number;
  overdue: number;
  reservations: number;
  finesOutstanding: number;
}

/**
 * Live view of every college in the directorate network.
 *
 * Reads the registry with a real-time listener so the portal updates as
 * colleges publish. Falls back to enumerating /institutions when the registry
 * is empty, which is what happens on an existing deployment before any client
 * has published its first snapshot — without that fallback a freshly upgraded
 * directorate would see an empty dashboard and conclude it was still broken.
 */
export function useDirectorateNetwork() {
  const { user } = useAuth();
  const [colleges, setColleges] = useState<DirectorateSnapshot[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [usedFallback, setUsedFallback] = useState(false);

  useEffect(() => {
    if (!user) {
      setColleges([]);
      setLoading(false);
      return;
    }

    let cancelled = false;

    const unsubscribe = onSnapshot(
      collection(db, ROOT_COLLECTIONS.directorateIndex),
      async (snap) => {
        if (cancelled) return;

        if (!snap.empty) {
          setColleges(snap.docs.map((d) => normalise(d.id, d.data())));
          setUsedFallback(false);
          setLoading(false);
          return;
        }

        // Registry empty — fall back to the institution list so the portal
        // still shows the network, flagged as not yet reporting.
        try {
          const instSnap = await getDocs(collection(db, ROOT_COLLECTIONS.institutions));
          if (cancelled) return;
          setColleges(
            instSnap.docs.map((d) => ({
              ...normalise(d.id, d.data()),
              lastSyncAt: 0,
              lastSyncPlatform: "not reporting",
            })),
          );
          setUsedFallback(true);
        } catch (err) {
          if (!cancelled) setError((err as Error).message);
        } finally {
          if (!cancelled) setLoading(false);
        }
      },
      (err) => {
        if (cancelled) return;
        setError(err.message);
        setLoading(false);
      },
    );

    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, [user]);

  const totals = useMemo<NetworkTotals>(() => {
    const reporting = colleges.filter((c) => c.lastSyncAt > 0);
    return {
      colleges: colleges.length,
      reporting: reporting.length,
      stale: reporting.filter(isStale).length,
      books: colleges.reduce((n, c) => n + c.booksCount, 0),
      ebooks: colleges.reduce((n, c) => n + c.ebooksCount, 0),
      members: colleges.reduce((n, c) => n + c.membersCount, 0),
      activeLoans: colleges.reduce((n, c) => n + c.activeLoans, 0),
      overdue: colleges.reduce((n, c) => n + c.overdueCount, 0),
      reservations: colleges.reduce((n, c) => n + c.reservationsCount, 0),
      finesOutstanding: colleges.reduce((n, c) => n + c.finesOutstanding, 0),
    };
  }, [colleges]);

  return { colleges, totals, loading, error, usedFallback };
}

/** Roles permitted to open the directorate portal. */
export const DIRECTOR_ROLES = [
  "director",
  "Director",
  "directorate_admin",
  "DirectorateAdmin",
  "owner",
  "admin",
  "college_admin",
];

export function canViewDirectorate(role: string | undefined | null): boolean {
  return !!role && DIRECTOR_ROLES.includes(role);
}
