/**
 * Directorate aggregation (spec section 6).
 *
 * The director dashboard must never fan out across institutions live. At three
 * colleges that merely feels slow; at three hundred it is a few thousand
 * document reads per dashboard load, per viewer, and it will not complete
 * inside a page load. So a scheduled function walks the registry, computes one
 * denormalized document per institution, and the dashboard reads only those.
 *
 * Counts use Firestore COUNT aggregations rather than fetching documents.
 * A count is billed per 1000 index entries scanned instead of per document, so
 * a 50,000-book college costs a handful of reads to summarise rather than
 * 50,000. This is the difference between a rollup that is affordable province-
 * wide and one that is not.
 *
 * The walk is paginated and bounded (spec section 8): institutions are
 * processed in pages with limited concurrency, so the function does not hold
 * every college in memory or open hundreds of simultaneous queries.
 */

import { onSchedule } from "firebase-functions/v2/scheduler";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { getFirestore, FieldValue, Timestamp } from "firebase-admin/firestore";
import type { Query } from "firebase-admin/firestore";

import {
  REGION,
  ROLES,
  ROOT,
  STATUS,
  SUMMARY_SCHEMA_VERSION,
  TENANT,
  TENANT_ALIASES,
  normaliseRole,
} from "./config";

/** Institutions per page of the registry walk. */
const PAGE_SIZE = 50;

/** Institutions summarised concurrently. Each costs ~10 count queries. */
const CONCURRENCY = 8;

// ─── Counting helpers ────────────────────────────────────────────────────────

/**
 * Run a COUNT aggregation, returning 0 rather than throwing.
 *
 * A missing composite index makes exactly one count fail. Letting that abort
 * the whole run would blank the entire dashboard because of one column, so a
 * failed count degrades to zero and is logged.
 */
async function countOf(query: Query, label: string): Promise<number> {
  try {
    const snap = await query.count().get();
    return snap.data().count;
  } catch (err) {
    logger.warn(`Count failed for ${label}`, { err: String(err) });
    return 0;
  }
}

/**
 * Sum a count across a collection and its spec-name alias.
 *
 * Patron records live in `members` on this deployment and `students` in the KPK
 * spec; loans in `issued_books` and `issues`. A college migrated to either
 * naming must still report, so both are counted and added. Only one is ever
 * non-empty in practice.
 */
async function countAcrossAliases(
  db: FirebaseFirestore.Firestore,
  institutionId: string,
  names: readonly string[],
  refine: (q: Query) => Query,
  label: string,
): Promise<number> {
  const counts = await Promise.all(
    names.map((name) =>
      countOf(refine(db.collection(`${ROOT.institutions}/${institutionId}/${name}`)), `${label}:${name}`),
    ),
  );
  return counts.reduce((a, b) => a + b, 0);
}

export interface InstitutionSummary {
  institutionId: string;
  name: string;
  district: string;
  region: string;
  status: string;

  totalBooks: number;
  totalEbooks: number;
  members: number;
  issued: number;
  overdue: number;
  reservations: number;

  /** Raw document count including soft-deleted rows. */
  booksIncludingDeleted: number;
  /**
   * Records that carry no `deleted` field at all, so they are invisible to
   * every `deleted == false` query the clients run. A non-zero value here means
   * that college has records predating the sync envelope, and its figures are
   * understated everywhere — not just on this dashboard.
   */
  recordsMissingSyncEnvelope: number;

  lastSynced: FirebaseFirestore.FieldValue | Timestamp | null;
  computedAt: FirebaseFirestore.FieldValue;
  schemaVersion: number;
}

/** Compute one institution's rollup. */
export async function summariseInstitution(
  db: FirebaseFirestore.Firestore,
  institutionId: string,
  registry: FirebaseFirestore.DocumentData,
): Promise<InstitutionSummary> {
  const base = `${ROOT.institutions}/${institutionId}`;
  const today = new Date().toISOString().slice(0, 10);

  const books = db.collection(`${base}/${TENANT.books}`);

  const [
    booksIncludingDeleted,
    liveBooks,
    digitalBooks,
    separateEbooks,
    members,
    issued,
    overdue,
    reservations,
  ] = await Promise.all([
    countOf(books, "books:all"),
    countOf(books.where("deleted", "==", false), "books:live"),
    // Digital titles are stored either as a flag on a book or in their own
    // collection, depending on which client created them.
    countOf(
      books.where("deleted", "==", false).where("isDigital", "==", true),
      "books:digital",
    ),
    countOf(
      db.collection(`${base}/${TENANT.ebooks}`).where("deleted", "==", false),
      "ebooks",
    ),
    countAcrossAliases(
      db,
      institutionId,
      TENANT_ALIASES.members,
      (q) => q.where("deleted", "==", false),
      "members",
    ),
    countAcrossAliases(
      db,
      institutionId,
      TENANT_ALIASES.issuedBooks,
      (q) => q.where("deleted", "==", false).where("status", "==", "Issued"),
      "issued",
    ),
    countAcrossAliases(
      db,
      institutionId,
      TENANT_ALIASES.issuedBooks,
      (q) =>
        q
          .where("deleted", "==", false)
          .where("status", "==", "Issued")
          .where("dueDate", "<", today),
      "overdue",
    ),
    countOf(
      db
        .collection(`${base}/${TENANT.reservations}`)
        .where("deleted", "==", false)
        .where("status", "==", "Pending"),
      "reservations",
    ),
  ]);

  const deletedBooks = await countOf(books.where("deleted", "==", true), "books:deleted");
  const missingEnvelope = Math.max(0, booksIncludingDeleted - liveBooks - deletedBooks);

  return {
    institutionId,
    name: registry.name ?? institutionId,
    district: registry.district ?? "",
    region: registry.region ?? "",
    status: registry.status ?? STATUS.active,

    // Printed stock only, so the printed and digital figures do not double-count.
    totalBooks: Math.max(0, liveBooks - digitalBooks),
    totalEbooks: digitalBooks + separateEbooks,
    members,
    issued,
    overdue,
    reservations,

    booksIncludingDeleted,
    recordsMissingSyncEnvelope: missingEnvelope,

    // What the college's own clients last reported. Distinct from computedAt:
    // a college that stopped syncing a month ago still gets a fresh computedAt
    // every run, and only lastSynced reveals that its figures are frozen.
    lastSynced: registry.lastSyncAt
      ? Timestamp.fromMillis(Number(registry.lastSyncAt))
      : registry.lastSynced ?? null,
    computedAt: FieldValue.serverTimestamp(),
    schemaVersion: SUMMARY_SCHEMA_VERSION,
  };
}

/** Run `worker` over `items` with bounded concurrency. */
async function mapLimit<T>(
  items: T[],
  limit: number,
  worker: (item: T) => Promise<void>,
): Promise<void> {
  let cursor = 0;
  const runners = Array.from({ length: Math.min(limit, items.length) }, async () => {
    while (cursor < items.length) {
      const item = items[cursor++];
      try {
        await worker(item);
      } catch (err) {
        logger.error("Summary failed for one institution", { err: String(err) });
      }
    }
  });
  await Promise.all(runners);
}

/**
 * Walk every active institution and rewrite its summary document.
 *
 * Returns counts rather than throwing on partial failure: one unreachable
 * college must not stop the other 299 being summarised.
 */
export async function rebuildAllSummaries(): Promise<{
  institutions: number;
  written: number;
  pages: number;
}> {
  const db = getFirestore();
  let cursor: FirebaseFirestore.QueryDocumentSnapshot | null = null;
  let institutions = 0;
  let written = 0;
  let pages = 0;

  for (;;) {
    let page: Query = db
      .collection(ROOT.registry)
      .where("status", "==", STATUS.active)
      .orderBy("__name__")
      .limit(PAGE_SIZE);
    if (cursor) page = page.startAfter(cursor);

    const snap = await page.get();
    if (snap.empty) break;

    pages++;
    institutions += snap.size;

    await mapLimit(snap.docs, CONCURRENCY, async (docSnap) => {
      const summary = await summariseInstitution(db, docSnap.id, docSnap.data());
      await db.doc(`${ROOT.summary}/${docSnap.id}`).set(summary, { merge: true });
      written++;
    });

    if (snap.size < PAGE_SIZE) break;
    cursor = snap.docs[snap.docs.length - 1];
  }

  logger.info("Directorate summary rebuilt", { institutions, written, pages });
  return { institutions, written, pages };
}

/**
 * Scheduled rollup.
 *
 * Every six hours rather than nightly: a directorate looking at the dashboard
 * during the working day should not be reading figures from midnight. Raise the
 * interval, not the timeout, if the run starts approaching its limit.
 */
export const buildDirectorateSummary = onSchedule(
  {
    schedule: "every 6 hours",
    timeZone: "Asia/Karachi",
    region: REGION,
    timeoutSeconds: 540,
    memory: "512MiB",
    retryCount: 2,
  },
  async () => {
    await rebuildAllSummaries();
  },
);

/**
 * Force a rebuild now. Directorate only.
 *
 * The dashboard's "refresh figures" button, and what to call after approving a
 * college rather than making them wait up to six hours to appear.
 */
export const rebuildDirectorateSummary = onCall(
  { region: REGION, timeoutSeconds: 540, memory: "512MiB" },
  async (request) => {
    if (normaliseRole(request.auth?.token?.role) !== ROLES.directorate) {
      throw new HttpsError("permission-denied", "Directorate access required.");
    }
    return rebuildAllSummaries();
  },
);

/**
 * Summarise a single institution immediately.
 *
 * Called by `setInstitutionStatus` on approval, and available to a college's own
 * admin so a newly onboarded library can confirm it is reporting without
 * waiting for the schedule.
 */
export const refreshInstitutionSummary = onCall({ region: REGION }, async (request) => {
  const role = normaliseRole(request.auth?.token?.role);
  const institutionId = (request.data ?? {}).institutionId as string | undefined;
  if (!institutionId) throw new HttpsError("invalid-argument", "institutionId is required.");

  const callerInstitution = request.auth?.token?.institutionId;
  if (role !== ROLES.directorate && callerInstitution !== institutionId) {
    throw new HttpsError("permission-denied", "You may only refresh your own institution.");
  }

  const db = getFirestore();
  const registry = await db.doc(`${ROOT.registry}/${institutionId}`).get();
  if (!registry.exists) {
    throw new HttpsError("not-found", `"${institutionId}" is not in the institution registry.`);
  }

  const summary = await summariseInstitution(db, institutionId, registry.data() ?? {});
  await db.doc(`${ROOT.summary}/${institutionId}`).set(summary, { merge: true });

  // `computedAt` is a server-timestamp sentinel and `lastSynced` may be one
  // too; neither survives serialisation back to the caller, so they are
  // replaced with the wall-clock time of this run.
  return { ...summary, computedAt: Date.now(), lastSynced: null };
});
