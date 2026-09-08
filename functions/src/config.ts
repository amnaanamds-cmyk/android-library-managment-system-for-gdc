/**
 * Shared constants for the NEXLIB backend.
 *
 * Collection names and role vocabularies are duplicated across four clients
 * (Kotlin, Python, TypeScript x2). When one drifts, sync breaks silently on the
 * platforms that did not change. Keep this file in step with:
 *
 *   web-app/src/lib/schema.ts
 *   shared/src/commonMain/kotlin/com/college/library/data/model/
 *   gdc_desktop/models/ and gdc_desktop/config.py
 */

/**
 * Deployment region. Mumbai is the closest Google Cloud region to Khyber
 * Pakhtunkhwa; every function and the Firestore database should share it, or
 * each rollup pays a cross-continent round trip per institution.
 */
export const REGION = "asia-south1";

/** Root-level collections, outside any tenant. */
export const ROOT = {
  users: "users",
  institutions: "institutions",
  /** Lightweight province-wide index: name, district, region, status. */
  registry: "institution_registry",
  /** Denormalized rollup the director dashboard reads. Function-written only. */
  summary: "directorate_summary",
  /** Legacy client-published rollup, still read as a fallback. */
  legacyIndex: "directorate_index",
  legacyColleges: "colleges",
} as const;

/**
 * Tenant sub-collections.
 *
 * The KPK spec names these `students`, `staff` and `issues`. This deployment
 * has been writing `members` and `issued_books` from all four clients since
 * before that spec, and renaming them would orphan every existing record, so
 * the deployed names stay canonical and the spec names are accepted as
 * aliases wherever data is read. See SYNC_ARCHITECTURE.md.
 */
export const TENANT = {
  books: "books",
  ebooks: "ebooks",
  members: "members",
  issuedBooks: "issued_books",
  reservations: "reservations",
  settings: "settings",
  auditLog: "audit_log",
  syncConflicts: "sync_conflicts",
  meta: "meta",
} as const;

/** Collections read under both their deployed and their spec name. */
export const TENANT_ALIASES = {
  members: ["members", "students"],
  issuedBooks: ["issued_books", "issues"],
} as const;

// ─── Roles ───────────────────────────────────────────────────────────────────

/** The canonical role set. New accounts are always minted with one of these. */
export const ROLES = {
  directorate: "directorate",
  admin: "admin",
  librarian: "librarian",
  staff: "staff",
  student: "student",
} as const;

export type Role = (typeof ROLES)[keyof typeof ROLES];

/**
 * Historical role spellings, mapped to the canonical set.
 *
 * Three generations of clients wrote roles differently: Python lowercase,
 * Kotlin capitalized, and an older web build using `owner`/`college_admin`.
 * Normalising on read is what lets claims be minted for an existing account
 * without first migrating every profile document.
 */
const ROLE_ALIASES: Record<string, Role> = {
  directorate: ROLES.directorate,
  directorateadmin: ROLES.directorate,
  directorate_admin: ROLES.directorate,

  admin: ROLES.admin,
  owner: ROLES.admin,
  college_admin: ROLES.admin,
  collegeadmin: ROLES.admin,
  // A college's own "director" is its administrator, NOT directorate oversight.
  // Conflating the two would grant every college head province-wide read access.
  director: ROLES.admin,

  librarian: ROLES.librarian,
  staff: ROLES.staff,

  student: ROLES.student,
  member: ROLES.student,
  patron: ROLES.student,
};

/**
 * Normalise any historical role spelling to the canonical set.
 *
 * Unknown roles resolve to `student`, the least-privileged option. A typo in a
 * role name must never widen access.
 */
export function normaliseRole(raw: unknown): Role {
  if (typeof raw !== "string") return ROLES.student;
  return ROLE_ALIASES[raw.trim().toLowerCase()] ?? ROLES.student;
}

export function isStaffRole(role: Role): boolean {
  return role === ROLES.admin || role === ROLES.librarian || role === ROLES.staff;
}

// ─── Registry status ─────────────────────────────────────────────────────────

export const STATUS = {
  pending: "pending",
  active: "active",
  suspended: "suspended",
} as const;

export type InstitutionStatus = (typeof STATUS)[keyof typeof STATUS];

/** Schema version stamped onto every summary document. */
export const SUMMARY_SCHEMA_VERSION = 3;
