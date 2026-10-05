// lib/analytics.ts
//
// Pure functions over the registry already loaded by useDirectorateNetwork().
// Deliberately NOT new Firestore reads or collections — district rollups,
// ranking, compliance scoring and data-quality alerts are all things the
// portal can compute from data it has in memory the moment the registry
// loads, so that is what they are. Keeping them pure functions (no hooks,
// no I/O) also makes them directly testable, which the exported test cases
// at the bottom of this file exercise.

import { DirectorateSnapshot, isStale, STALE_AFTER_MS } from "./directorate";

// ── District rollups ────────────────────────────────────────────────────

export interface DistrictRollup {
  district: string;
  colleges: number;
  books: number;
  members: number;
  activeLoans: number;
  overdue: number;
  reporting: number;
}

export function rollupByDistrict(colleges: DirectorateSnapshot[]): DistrictRollup[] {
  const map = new Map<string, DistrictRollup>();
  for (const c of colleges) {
    const key = c.district?.trim() || "Unassigned";
    const row =
      map.get(key) ||
      ({ district: key, colleges: 0, books: 0, members: 0, activeLoans: 0, overdue: 0, reporting: 0 } as DistrictRollup);
    row.colleges += 1;
    row.books += c.booksCount;
    row.members += c.membersCount;
    row.activeLoans += c.activeLoans;
    row.overdue += c.overdueCount;
    row.reporting += c.lastSyncAt > 0 ? 1 : 0;
    map.set(key, row);
  }
  return [...map.values()].sort((a, b) => b.colleges - a.colleges);
}

// ── Ranking / benchmarking ──────────────────────────────────────────────
//
// A percentile, not a raw rank: "better than 80% of the network" reads the
// same whether the network has 12 colleges or 350, which a raw "#3 of 12"
// figure does not — worth keeping in mind since the network is meant to
// grow from a handful of pilot colleges to the low hundreds.

export interface RankedCollege {
  docId: string;
  institutionId: string;
  name: string;
  value: number;
  percentile: number; // 0-100, higher is better
}

/** A rank key is either a raw numeric field on the snapshot, or a function
 *  deriving one — e.g. booksAvailablePct below, which is not itself stored
 *  anywhere and has to be computed from two fields that are. */
export type RankAccessor =
  | "booksCount"
  | "activeLoans"
  | "membersCount"
  | ((c: DirectorateSnapshot) => number);

function accessorValue(c: DirectorateSnapshot, accessor: RankAccessor): number {
  return typeof accessor === "function" ? accessor(c) : c[accessor];
}

/** Percentage of a college's printed books currently on the shelf rather
 *  than out on loan. 0 for a college reporting zero books, rather than
 *  NaN/Infinity, so it sorts to the bottom instead of breaking the sort. */
export function booksAvailablePct(c: DirectorateSnapshot): number {
  if (c.booksCount <= 0) return 0;
  return Math.round(((c.booksCount - c.activeLoans) / c.booksCount) * 100);
}

export function rankBy(colleges: DirectorateSnapshot[], key: RankAccessor): RankedCollege[] {
  const sorted = [...colleges].sort((a, b) => accessorValue(a, key) - accessorValue(b, key));
  const n = sorted.length;
  return sorted
    .map((c, i) => ({
      docId: c.docId,
      institutionId: c.institutionId,
      name: c.name,
      value: accessorValue(c, key),
      percentile: n <= 1 ? 100 : Math.round((i / (n - 1)) * 100),
    }))
    .sort((a, b) => b.value - a.value);
}

export function networkAverage(colleges: DirectorateSnapshot[], key: RankAccessor): number {
  if (colleges.length === 0) return 0;
  return colleges.reduce((n, c) => n + accessorValue(c, key), 0) / colleges.length;
}

// ── Compliance scorecard ────────────────────────────────────────────────
//
// Four checks a directorate can defend to an auditor, each with a plain
// reason attached — a score with no explanation invites "why?" from exactly
// the person it is meant to satisfy.

export interface ComplianceCheck {
  key: string;
  label: string;
  pass: boolean;
  detail: string;
}

export interface ComplianceResult {
  docId: string;
  institutionId: string;
  name: string;
  checks: ComplianceCheck[];
  score: number; // 0-100
}

export function scoreCompliance(c: DirectorateSnapshot): ComplianceResult {
  const checks: ComplianceCheck[] = [
    {
      key: "reporting",
      label: "Has ever synced",
      pass: c.lastSyncAt > 0,
      detail: c.lastSyncAt > 0 ? "Snapshot on file." : "No snapshot has ever been published.",
    },
    {
      key: "fresh",
      label: "Synced within 48 hours",
      pass: c.lastSyncAt > 0 && !isStale(c),
      detail:
        c.lastSyncAt > 0
          ? isStale(c)
            ? `Last synced over ${Math.round(STALE_AFTER_MS / 3_600_000)}h ago.`
            : "Current."
          : "Never synced.",
    },
    {
      key: "contact",
      label: "Contact details on file",
      pass: !!(c.contactEmail || c.phone),
      detail: c.contactEmail || c.phone ? "On file." : "No email or phone recorded.",
    },
    {
      key: "overdue-ratio",
      label: "Overdue ratio under 20%",
      pass: c.activeLoans === 0 || c.overdueCount / Math.max(1, c.activeLoans) < 0.2,
      detail:
        c.activeLoans === 0
          ? "No active loans to assess."
          : `${Math.round((c.overdueCount / c.activeLoans) * 100)}% of active loans overdue.`,
    },
  ];
  const score = Math.round((checks.filter((x) => x.pass).length / checks.length) * 100);
  return { docId: c.docId, institutionId: c.institutionId, name: c.name, checks, score };
}

// ── Data-quality and operational alerts ─────────────────────────────────
//
// Everything here is derived, not stored — an alert disappears the moment
// its underlying cause is fixed, with nothing to reset or acknowledge.

export type AlertSeverity = "high" | "medium";

export interface RegistryAlert {
  institutionId: string;
  name: string;
  severity: AlertSeverity;
  message: string;
}

export function computeAlerts(colleges: DirectorateSnapshot[]): RegistryAlert[] {
  const alerts: RegistryAlert[] = [];
  const seenIds = new Map<string, number>();

  for (const c of colleges) {
    seenIds.set(c.institutionId, (seenIds.get(c.institutionId) || 0) + 1);

    if (!c.lastSyncAt) {
      alerts.push({ institutionId: c.institutionId, name: c.name, severity: "high", message: "Never published a snapshot — approved but not yet reporting." });
    } else if (isStale(c)) {
      alerts.push({ institutionId: c.institutionId, name: c.name, severity: "medium", message: `No sync in over ${Math.round(STALE_AFTER_MS / 3_600_000)} hours.` });
    }

    if (c.lastSyncAt && c.booksCount === 0) {
      alerts.push({ institutionId: c.institutionId, name: c.name, severity: "medium", message: "Reporting, but zero books on record." });
    }

    if (!c.contactEmail && !c.phone) {
      alerts.push({ institutionId: c.institutionId, name: c.name, severity: "medium", message: "No contact email or phone on file." });
    }

    if (c.activeLoans > 0 && c.overdueCount / c.activeLoans > 0.5) {
      alerts.push({
        institutionId: c.institutionId,
        name: c.name,
        severity: "high",
        message: `${Math.round((c.overdueCount / c.activeLoans) * 100)}% of active loans are overdue.`,
      });
    }
  }

  for (const [id, count] of seenIds) {
    if (count > 1) {
      const name = colleges.find((c) => c.institutionId === id)?.name || id;
      alerts.push({ institutionId: id, name, severity: "high", message: `Institution ID appears ${count} times in the registry — likely a duplicate publish.` });
    }
  }

  return alerts.sort((a, b) => (a.severity === b.severity ? 0 : a.severity === "high" ? -1 : 1));
}

// ── Category stock across the network ───────────────────────────────────
//
// booksByCategory is self-reported per college, same trust model as every
// other figure in the registry (see lib/directorate.ts). A college that has
// never published under schemaVersion 3 has an EMPTY booksByCategory, not a
// zero one — every function here keeps "not reporting this category" and
// "reports zero copies of it" distinct, so a college doesn't read as
// understocked in everything the moment it upgrades.

export interface CategoryTotal {
  category: string;
  totalCopies: number;
  collegesReporting: number;
}

/** Every category any reporting college has published, network totals. */
export function categoryNetworkTotals(colleges: DirectorateSnapshot[]): CategoryTotal[] {
  const map = new Map<string, CategoryTotal>();
  for (const c of colleges) {
    for (const [category, count] of Object.entries(c.booksByCategory || {})) {
      const row = map.get(category) || { category, totalCopies: 0, collegesReporting: 0 };
      row.totalCopies += count;
      row.collegesReporting += 1;
      map.set(category, row);
    }
  }
  return [...map.values()].sort((a, b) => b.totalCopies - a.totalCopies);
}

export interface CollegeCategoryStock {
  docId: string;
  institutionId: string;
  name: string;
  district?: string;
  copies: number;
  percentile: number; // 0-100 among colleges that reported ANY category data
}

/** Colleges ranked by copies of one category, restricted to colleges that
 *  have published category data at all — a college with no booksByCategory
 *  yet is reported separately as `notReporting`, never silently folded into
 *  the ranking at 0. */
export function rankByCategory(
  colleges: DirectorateSnapshot[],
  category: string,
): { ranked: CollegeCategoryStock[]; notReporting: DirectorateSnapshot[] } {
  const reporting = colleges.filter((c) => Object.keys(c.booksByCategory || {}).length > 0);
  const notReporting = colleges.filter((c) => Object.keys(c.booksByCategory || {}).length === 0);

  const withCopies = reporting
    .map((c) => ({ c, copies: c.booksByCategory[category] || 0 }))
    .sort((a, b) => a.copies - b.copies);
  const n = withCopies.length;

  const ranked = withCopies
    .map(({ c, copies }, i) => ({
      docId: c.docId,
      institutionId: c.institutionId,
      name: c.name,
      district: c.district,
      copies,
      percentile: n <= 1 ? 100 : Math.round((i / (n - 1)) * 100),
    }))
    .sort((a, b) => b.copies - a.copies);

  return { ranked, notReporting };
}
