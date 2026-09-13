// lib/roles.ts
//
// Role predicates with no other imports, so both auth-context and directorate
// can use them without forming an import cycle (directorate imports
// auth-context for its hooks).

/**
 * Roles permitted to open the directorate portal — Higher Education
 * Department oversight accounts ONLY.
 *
 * "director"/"Director" and "owner"/"admin"/"college_admin" are deliberately
 * excluded: those are a single college's OWN roles, used by every college
 * that signs up. This list must stay in sync with isDirectorateAdmin() in
 * firestore.rules, which is the layer that actually enforces it.
 */
export const DIRECTOR_ROLES = ["directorate_admin", "DirectorateAdmin"];

export function canViewDirectorate(role: string | undefined | null): boolean {
  return !!role && DIRECTOR_ROLES.includes(role);
}
