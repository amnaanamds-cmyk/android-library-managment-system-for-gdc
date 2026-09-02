// lib/schema.ts
//
// Single source of truth for Firestore collection names and record shapes,
// shared by every page in the web app.
//
// Before this existed each page hardcoded its own collection string, and they
// drifted: the AI recommender read "issues" while the rest of the app wrote
// "issued_books", so it always rendered an empty dataset. Import from here
// instead of typing a collection name inline.
//
// The shapes below mirror the Kotlin data classes in
// shared/src/commonMain/kotlin/com/college/library/data/model/ and the Python
// dataclasses in gdc_desktop/models/. Keep all three in step.

export const COLLECTIONS = {
  books: "books",
  ebooks: "ebooks",
  members: "members",
  issuedBooks: "issued_books",
  reservations: "reservations",
  auditLog: "audit_log",
  visitorLog: "visitor_log",
  purchaseOrders: "purchase_orders",
  bookTransfers: "book_transfers",
  illRequests: "ill_requests",
  settings: "settings",
} as const;

/** Root-level collections, outside any tenant. */
export const ROOT_COLLECTIONS = {
  users: "users",
  institutions: "institutions",
  directorateIndex: "directorate_index",
  colleges: "colleges",
} as const;

// ─── Sync envelope ───────────────────────────────────────────────────────────
// Every tenant record carries these fields. The sync engines on all three
// platforms rely on them for last-write-wins resolution and soft deletes.

export interface SyncEnvelope {
  syncId: string;
  collegeId: string;
  lastUpdated: number;
  deleted: boolean;
  syncStatus: "synced" | "pending";
}

export interface BookRecord extends SyncEnvelope {
  id?: number;
  isbn?: string;
  accNo?: string;
  title: string;
  author?: string;
  publisher?: string;
  category?: string;
  callNumber?: string;
  status: string; // "Available" | "Issued"
  isDigital?: boolean;
  digitalUrl?: string | null;
  price?: number;
}

export interface MemberRecord extends SyncEnvelope {
  id?: number;
  name: string;
  memberId?: string;
  email?: string;
  phone?: string;
  department?: string;
  status?: string;
}

export interface IssueRecord extends SyncEnvelope {
  bookId?: number;
  bookTitle?: string;
  bookIsbn?: string;
  memberId?: number;
  memberName?: string;
  memberMemberId?: string;
  issueDate?: string;
  dueDate?: string;
  returnDate?: string | null;
  fine?: number;
  /** Canonical status field: "Issued" or "Returned". */
  status: string;
}

// ─── Predicates ──────────────────────────────────────────────────────────────
//
// The canonical marker for an open loan is status === "Issued", matching the
// Kotlin and Python models. Several pages previously tested a `returned`
// boolean that no platform has ever written, so every loan counted as active.
// Route all loan logic through these helpers.

export function isActiveIssue(issue: Partial<IssueRecord> | undefined | null): boolean {
  if (!issue || issue.deleted) return false;
  if (typeof issue.status === "string" && issue.status.length > 0) {
    return issue.status.toLowerCase() === "issued";
  }
  // Records written before `status` existed only carried returnDate.
  return !issue.returnDate;
}

/** An active loan whose due date is in the past (dates are ISO "YYYY-MM-DD"). */
export function isOverdue(
  issue: Partial<IssueRecord> | undefined | null,
  today: string = new Date().toISOString().slice(0, 10),
): boolean {
  if (!isActiveIssue(issue)) return false;
  const due = issue?.dueDate;
  return typeof due === "string" && due.length > 0 && due < today;
}

/** Days a loan is overdue by, or 0 when it is not. */
export function overdueDays(
  issue: Partial<IssueRecord> | undefined | null,
  today: Date = new Date(),
): number {
  if (!isOverdue(issue, today.toISOString().slice(0, 10))) return 0;
  const due = new Date(`${issue!.dueDate}T00:00:00Z`);
  const now = new Date(`${today.toISOString().slice(0, 10)}T00:00:00Z`);
  return Math.max(0, Math.round((now.getTime() - due.getTime()) / 86_400_000));
}

/** Generate a sync id in the same shape the other platforms use (UUID v4). */
export function newSyncId(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  // Fallback for older browsers, still RFC-4122 shaped.
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}
