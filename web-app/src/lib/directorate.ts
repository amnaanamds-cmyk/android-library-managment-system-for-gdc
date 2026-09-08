// lib/directorate.ts
//
// The directorate portal's data layer.
//
// ─── Why a summary collection, and not a live query ──────────────────────────
//
// The directorate needs province-wide totals, but tenant data lives under
// /institutions/{id}/... and is readable only by that college's own staff.
// Firestore has no cross-tenant aggregate query. Fanning out across every
// institution on each dashboard load is a few thousand document reads per
// viewer per load — tolerable at three colleges, impossible at three hundred.
//
// So a scheduled Cloud Function (functions/src/summary.ts) walks the registry
// and writes one denormalized document per institution to
// /directorate_summary/{institutionId}. This portal reads ONLY that collection.
//
// ─── Why the fallback chain ──────────────────────────────────────────────────
//
// Three registries grew up in parallel and disagreed. In priority order:
//
//   1. directorate_summary   function-written, authoritative, cannot be forged
//   2. directorate_index     LEGACY, published by each college's own client
//   3. institution_registry  names only, so an approved college still appears
//
// (2) is retained because it is what every currently-deployed client writes,
// and a directorate upgrading before the function has run must not open an
// empty dashboard and conclude the rollout failed. It is untrustworthy by
// construction — a college publishes its own figures — so it is labelled as
// self-reported wherever it is used, and (1) always wins when present.

"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  collection,
  doc,
  setDoc,
  getDoc,
  onSnapshot,
  getDocs,
  DocumentData,
  Timestamp,
} from "firebase/firestore";
import { httpsCallable } from "firebase/functions";
import { db, functions } from "./firebase";
import { useAuth } from "./auth-context";
import { ROOT_COLLECTIONS } from "./schema";

/** Bumped when the summary document shape changes. Matches functions/src/config.ts. */
export const SUMMARY_SCHEMA_VERSION = 3;

/** A college whose figures are older than this is shown as stale, not current. */
export const STALE_AFTER_MS = 48 * 60 * 60 * 1000;

/** Where a row's figures came from. Displayed, because it changes how much to trust them. */
export type SummarySource = "summary" | "self-reported" | "registry-only";

export interface DirectorateSnapshot {
  institutionId: string;
  name: string;
  district: string;
  region: string;
  status: string;
  contactEmail: string;
  phone: string;

  totalBooks: number;
  totalEbooks: number;
  members: number;
  issued: number;
  overdue: number;
  reservations: number;

  /** Records with no sync envelope, so invisible to the clients' own queries. */
  recordsMissingSyncEnvelope: number;

  /** When this college's clients last pushed data, in epoch ms. 0 = never. */
  lastSynced: number;
  /** When the rollup last recomputed this row, in epoch ms. */
  computedAt: number;

  source: SummarySource;
}

const ZERO = {
  totalBooks: 0,
  totalEbooks: 0,
  members: 0,
  issued: 0,
  overdue: 0,
  reservations: 0,
  recordsMissingSyncEnvelope: 0,
};

/** Firestore Timestamp, epoch millis, or absent — all appear in practice. */
function toMillis(value: unknown): number {
  if (!value) return 0;
  if (value instanceof Timestamp) return value.toMillis();
  if (typeof value === "number") return value;
  if (typeof value === "object" && value !== null && "seconds" in value) {
    return Number((value as { seconds: number }).seconds) * 1000;
  }
  return 0;
}

/** Function-written summary document. */
function fromSummary(id: string, d: DocumentData): DirectorateSnapshot {
  return {
    institutionId: d.institutionId || id,
    name: d.name || id,
    district: d.district || "",
    region: d.region || "",
    status: d.status || "active",
    contactEmail: d.contactEmail || "",
    phone: d.phone || "",
    totalBooks: Number(d.totalBooks ?? 0),
    totalEbooks: Number(d.totalEbooks ?? 0),
    members: Number(d.members ?? 0),
    issued: Number(d.issued ?? 0),
    overdue: Number(d.overdue ?? 0),
    reservations: Number(d.reservations ?? 0),
    recordsMissingSyncEnvelope: Number(d.recordsMissingSyncEnvelope ?? 0),
    lastSynced: toMillis(d.lastSynced),
    computedAt: toMillis(d.computedAt),
    source: "summary",
  };
}

/**
 * Legacy client-published row. Every historical field spelling is tolerated so
 * a college that last published from an older build shows figures rather than
 * a blank line.
 */
function fromLegacyIndex(id: string, d: DocumentData): DirectorateSnapshot {
  return {
    institutionId: d.institutionId || d.collegeId || d.college_id || id,
    name: d.name || d.collegeName || d.collegeFullName || d.libraryName || id,
    district: d.district || d.location || "",
    region: d.region || "",
    status: d.status || "active",
    contactEmail: d.contactEmail || d.email || "",
    phone: d.phone || "",
    totalBooks: Number(d.booksCount ?? d.books ?? d.totalBooks ?? 0),
    totalEbooks: Number(d.ebooksCount ?? d.totalEbooks ?? 0),
    members: Number(d.membersCount ?? d.members ?? 0),
    issued: Number(d.activeLoans ?? d.circulationCount ?? d.issued ?? 0),
    overdue: Number(d.overdueCount ?? d.overdue ?? 0),
    reservations: Number(d.reservationsCount ?? d.reservations ?? 0),
    recordsMissingSyncEnvelope: 0,
    lastSynced: toMillis(d.lastSyncAt ?? d.lastSeen ?? d.lastUpdated),
    computedAt: 0,
    source: "self-reported",
  };
}

/** Registry entry with no figures yet — an approved college that has not reported. */
function fromRegistry(id: string, d: DocumentData): DirectorateSnapshot {
  return {
    institutionId: d.institutionId || id,
    name: d.name || id,
    district: d.district || "",
    region: d.region || "",
    status: d.status || "pending",
    contactEmail: d.contactEmail || "",
    phone: d.adminPhone || d.phone || "",
    ...ZERO,
    lastSynced: 0,
    computedAt: 0,
    source: "registry-only",
  };
}

export function isStale(row: Pick<DirectorateSnapshot, "lastSynced">): boolean {
  if (!row.lastSynced) return true;
  return Date.now() - row.lastSynced > STALE_AFTER_MS;
}

// ─── Totals and rollups ──────────────────────────────────────────────────────

export interface NetworkTotals {
  institutions: number;
  reporting: number;
  stale: number;
  totalBooks: number;
  totalEbooks: number;
  members: number;
  issued: number;
  overdue: number;
  reservations: number;
}

export interface DistrictRollup extends NetworkTotals {
  district: string;
}

function sum(rows: DirectorateSnapshot[]): Omit<NetworkTotals, "institutions" | "reporting" | "stale"> {
  return rows.reduce(
    (acc, r) => ({
      totalBooks: acc.totalBooks + r.totalBooks,
      totalEbooks: acc.totalEbooks + r.totalEbooks,
      members: acc.members + r.members,
      issued: acc.issued + r.issued,
      overdue: acc.overdue + r.overdue,
      reservations: acc.reservations + r.reservations,
    }),
    { totalBooks: 0, totalEbooks: 0, members: 0, issued: 0, overdue: 0, reservations: 0 },
  );
}

function totalsFor(rows: DirectorateSnapshot[]): NetworkTotals {
  const reporting = rows.filter((r) => r.lastSynced > 0 || r.source === "summary");
  return {
    institutions: rows.length,
    reporting: reporting.length,
    stale: reporting.filter(isStale).length,
    ...sum(rows),
  };
}

/** Per-district rollups, largest collection first (spec section 6). */
export function rollupByDistrict(rows: DirectorateSnapshot[]): DistrictRollup[] {
  const byDistrict = new Map<string, DirectorateSnapshot[]>();
  for (const row of rows) {
    const key = row.district || "Unassigned";
    const bucket = byDistrict.get(key);
    if (bucket) bucket.push(row);
    else byDistrict.set(key, [row]);
  }
  return [...byDistrict.entries()]
    .map(([district, group]) => ({ district, ...totalsFor(group) }))
    .sort((a, b) => b.totalBooks - a.totalBooks);
}

// ─── Hooks ───────────────────────────────────────────────────────────────────

/**
 * Live view of every institution the directorate can see.
 *
 * Subscribes to the summary collection, and only if it is empty falls back down
 * the chain described at the top of this file.
 */
export function useDirectorateNetwork() {
  const { user, profile } = useAuth();
  const [rows, setRows] = useState<DirectorateSnapshot[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [source, setSource] = useState<SummarySource>("summary");

  useEffect(() => {
    if (!user || !canViewDirectorate(profile?.role)) {
      setRows([]);
      setLoading(false);
      return;
    }

    let cancelled = false;

    const unsubscribe = onSnapshot(
      collection(db, ROOT_COLLECTIONS.directorateSummary),
      async (snap) => {
        if (cancelled) return;

        if (!snap.empty) {
          setRows(snap.docs.map((d) => fromSummary(d.id, d.data())));
          setSource("summary");
          setLoading(false);
          return;
        }

        // Summary empty — the scheduled function has not run yet, or this is a
        // fresh deployment. Degrade rather than showing an empty province.
        try {
          const legacy = await getDocs(collection(db, ROOT_COLLECTIONS.directorateIndex));
          if (cancelled) return;
          if (!legacy.empty) {
            setRows(legacy.docs.map((d) => fromLegacyIndex(d.id, d.data())));
            setSource("self-reported");
            setLoading(false);
            return;
          }

          const registry = await getDocs(collection(db, ROOT_COLLECTIONS.institutionRegistry));
          if (cancelled) return;
          setRows(registry.docs.map((d) => fromRegistry(d.id, d.data())));
          setSource("registry-only");
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
  }, [user, profile?.role]);

  const totals = useMemo(() => totalsFor(rows), [rows]);
  const districts = useMemo(() => rollupByDistrict(rows), [rows]);

  return { rows, totals, districts, loading, error, source };
}

export interface RegistryEntry {
  institutionId: string;
  name: string;
  district: string;
  region: string;
  status: string;
  contactEmail: string;
  adminName: string;
  createdAt: number;
}

/** The institution registry, including colleges still awaiting approval. */
export function useInstitutionRegistry() {
  const { user, profile } = useAuth();
  const [entries, setEntries] = useState<RegistryEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!user || !canViewDirectorate(profile?.role)) {
      setEntries([]);
      setLoading(false);
      return;
    }
    const unsubscribe = onSnapshot(
      collection(db, ROOT_COLLECTIONS.institutionRegistry),
      (snap) => {
        setEntries(
          snap.docs.map((d) => {
            const data = d.data();
            return {
              institutionId: data.institutionId || d.id,
              name: data.name || d.id,
              district: data.district || "",
              region: data.region || "",
              status: data.status || "pending",
              contactEmail: data.contactEmail || "",
              adminName: data.adminName || "",
              createdAt: toMillis(data.createdAt),
            };
          }),
        );
        setLoading(false);
      },
      (err) => {
        setError(err.message);
        setLoading(false);
      },
    );
    return () => unsubscribe();
  }, [user, profile?.role]);

  const pending = useMemo(() => entries.filter((e) => e.status === "pending"), [entries]);
  return { entries, pending, loading, error };
}

// ─── Directorate actions ─────────────────────────────────────────────────────

/** Approve, suspend or re-activate a college. Server enforces directorate role. */
export function useInstitutionStatusAction() {
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const setStatus = useCallback(
    async (institutionId: string, status: "active" | "pending" | "suspended") => {
      setBusy(institutionId);
      setError(null);
      try {
        await httpsCallable(functions, "setInstitutionStatus")({ institutionId, status });
        // Approving a college is pointless if its figures then take up to six
        // hours to appear, so summarise it immediately.
        if (status === "active") {
          await httpsCallable(functions, "refreshInstitutionSummary")({ institutionId });
        }
        return true;
      } catch (err) {
        setError((err as Error).message);
        return false;
      } finally {
        setBusy(null);
      }
    },
    [],
  );

  return { setStatus, busy, error };
}

/** Force a full province-wide recompute. */
export function useRebuildSummaries() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<{ institutions: number; written: number } | null>(null);

  const rebuild = useCallback(async () => {
    setBusy(true);
    setError(null);
    try {
      const res = await httpsCallable(functions, "rebuildDirectorateSummary")({});
      setResult(res.data as { institutions: number; written: number });
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  }, []);

  return { rebuild, busy, error, result };
}

/**
 * One institution's figures, down the same fallback chain as the network view.
 *
 * Shared with the drill-down page deliberately: that page previously inlined
 * its own copy of the field mapping, so every field spelling had to be fixed in
 * two places and they had already drifted apart.
 */
export async function fetchSnapshot(
  institutionId: string,
): Promise<DirectorateSnapshot | null> {
  if (!institutionId) return null;

  const summarySnap = await getDoc(doc(db, ROOT_COLLECTIONS.directorateSummary, institutionId));
  if (summarySnap.exists()) return fromSummary(institutionId, summarySnap.data());

  const legacySnap = await getDoc(doc(db, ROOT_COLLECTIONS.directorateIndex, institutionId));
  if (legacySnap.exists()) return fromLegacyIndex(institutionId, legacySnap.data());

  const registrySnap = await getDoc(doc(db, ROOT_COLLECTIONS.institutionRegistry, institutionId));
  if (registrySnap.exists()) return fromRegistry(institutionId, registrySnap.data());

  return null;
}

// ─── Legacy self-publishing (Spark-plan fallback) ────────────────────────────

/**
 * Publish this college's own aggregate row to the LEGACY directorate_index.
 *
 * Retained deliberately, for one reason: Cloud Functions require the Blaze
 * plan. On Spark there is no scheduled rollup, so a college publishing its own
 * figures is the only thing that puts anything on the director dashboard at
 * all. `useDirectorateNetwork` reads this only when directorate_summary is
 * empty, and labels the result self-reported.
 *
 * Once the project is on Blaze and the scheduled function has run, this becomes
 * dead weight and the call sites can be deleted — the summary collection always
 * takes priority, so leaving it in place does no harm in the meantime.
 *
 * Failures are swallowed: a college that cannot publish its rollup must still
 * be able to run its library.
 */
export async function publishSnapshot(
  institutionId: string,
  snapshot: Record<string, unknown> & { name?: string },
  platform: "web" | "desktop" | "android" = "web",
): Promise<boolean> {
  if (!institutionId) return false;
  try {
    await setDoc(
      doc(db, ROOT_COLLECTIONS.directorateIndex, institutionId),
      {
        booksCount: 0,
        ebooksCount: 0,
        membersCount: 0,
        activeLoans: 0,
        overdueCount: 0,
        reservationsCount: 0,
        ...snapshot,
        institutionId,
        lastSyncAt: Date.now(),
        lastSyncPlatform: platform,
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

// ─── Access ──────────────────────────────────────────────────────────────────

/**
 * Only the `directorate` role may open this portal.
 *
 * This is deliberately narrower than it used to be. The old list also admitted
 * `director`, `owner`, `admin` and `college_admin` — that is, every college
 * administrator in the province could open the network-wide dashboard. A
 * college's "director" is its own administrator, not a directorate official.
 *
 * The security rules draw the same line, so widening this list would not grant
 * access, it would only produce a dashboard whose every query fails. Existing
 * directorate staff carrying role "director" must be moved to "directorate"
 * via the setUserRole callable — see DEPLOYMENT.md.
 */
export function canViewDirectorate(role: string | undefined | null): boolean {
  return role === "directorate";
}
