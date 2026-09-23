// lib/schema.ts
//
// Collection names the directorate portal reads or writes. This app never
// writes tenant data, so it deliberately does not carry the web portal's
// full tenant schema — see firestore.rules for the access boundary each
// of these sits behind.

/** Root-level collections, outside any tenant. */
export const ROOT_COLLECTIONS = {
  users: "users",
  institutions: "institutions",
  directorateIndex: "directorate_index",
  colleges: "colleges",
} as const;

/** Tenant sub-collections this portal may read (public catalogue only). */
export const COLLECTIONS = {
  books: "books",
} as const;

/**
 * Directorate-internal collections — never touched by a college's own apps
 * except `announcements` and `documents`, which are readable (not writable)
 * by any signed-in user so a college app can display them once it grows a
 * screen to do so. See firestore.rules for the exact boundary; every one of
 * these is proven unreachable by a tenant account in
 * tests/firestore/rules.test.mjs.
 */
export const MIS_COLLECTIONS = {
  staff: "directorate_staff",
  auditLog: "directorate_audit_log",
  notes: "directorate_notes",
  followups: "directorate_followups",
  announcements: "directorate_announcements",
  documents: "directorate_documents",
  inspections: "directorate_inspections",
  snapshotsHistory: "directorate_snapshots_history",
} as const;
