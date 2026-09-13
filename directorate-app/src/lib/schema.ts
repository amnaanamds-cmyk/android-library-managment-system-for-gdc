// lib/schema.ts
//
// Only the collection names the directorate portal actually reads. This app
// never writes tenant data, so it deliberately does not carry the web
// portal's full tenant schema.

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
