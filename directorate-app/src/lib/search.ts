// lib/search.ts
//
// Union catalogue search: find a title across every approved college's
// public books collection, so the directorate can answer "which colleges
// hold this?" without visiting each one.
//
// This is the one feature in this file that is genuinely dangerous to get
// wrong, and the danger is the same one documented at length in this
// project's engineering casebook — read amplification: a listener or query
// that reads a whole collection on every keystroke turns a search box into
// a bill. Two decisions here exist specifically to avoid repeating that:
//
//   1. On-demand only. This is a plain async function, not a hook wired to
//      a live listener — a search runs once, when the user submits it, and
//      never re-runs itself.
//   2. Bounded fan-out. It queries at most MAX_COLLEGES_PER_SEARCH colleges
//      per call, each with its own `limit()`, rather than every approved
//      college's entire catalogue. A network of 350 colleges searched
//      naively is 350 collection reads for one query typed once — bounding
//      it is not an optimization, it is what keeps this feature affordable
//      at the scale this portal is meant to reach.

import { collection, getDocs, limit, query, where } from "firebase/firestore";
import { db } from "./firebase";
import { DirectorateSnapshot } from "./directorate";

const MAX_COLLEGES_PER_SEARCH = 40;
const MAX_RESULTS_PER_COLLEGE = 8;

export interface UnionSearchHit {
  collegeId: string;
  collegeName: string;
  bookId: string;
  title: string;
  author: string;
  isbn: string;
  status: string;
}

export interface UnionSearchResult {
  hits: UnionSearchHit[];
  collegesSearched: number;
  collegesSkipped: number;
  tookMs: number;
}

function toHit(c: DirectorateSnapshot, id: string, data: Record<string, unknown>): UnionSearchHit {
  return {
    collegeId: c.institutionId,
    collegeName: c.name,
    bookId: (data.syncId as string) || id,
    title: (data.title as string) || "Untitled",
    author: (data.author as string) || "",
    isbn: (data.isbn as string) || "",
    status: (data.status as string) || "Unknown",
  };
}

/** Search approved colleges' public catalogues for a title prefix or an
 *  exact ISBN. Firestore cannot express "title starts with X OR isbn
 *  equals X" as a single query — a range filter cannot share a query with
 *  a differently-shaped OR branch the way this needs — so this runs two
 *  bounded queries per college and merges the results client-side, rather
 *  than one query that would silently return the wrong rows. */
export async function searchUnionCatalogue(
  colleges: DirectorateSnapshot[],
  term: string,
): Promise<UnionSearchResult> {
  const started = Date.now();
  const trimmed = term.trim();
  if (!trimmed) return { hits: [], collegesSearched: 0, collegesSkipped: 0, tookMs: 0 };

  const targets = colleges.slice(0, MAX_COLLEGES_PER_SEARCH);
  const skipped = Math.max(0, colleges.length - targets.length);

  const perCollege = await Promise.all(
    targets.map(async (c) => {
      const books = collection(db, "institutions", c.institutionId, "books");
      try {
        const [byTitle, byIsbn] = await Promise.all([
          getDocs(
            query(
              books,
              where("title", ">=", trimmed),
              where("title", "<=", trimmed + ""),
              limit(MAX_RESULTS_PER_COLLEGE),
            ),
          ),
          getDocs(query(books, where("isbn", "==", trimmed), limit(MAX_RESULTS_PER_COLLEGE))),
        ]);
        const seen = new Set<string>();
        const hits: UnionSearchHit[] = [];
        for (const d of [...byTitle.docs, ...byIsbn.docs]) {
          const data = d.data();
          if (data.deleted) continue;
          const id = (data.syncId as string) || d.id;
          if (seen.has(id)) continue;
          seen.add(id);
          hits.push(toHit(c, d.id, data));
        }
        return hits;
      } catch {
        // A college that has locked down its catalogue, or a transient
        // error, must not fail the whole search — it simply contributes
        // no hits.
        return [] as UnionSearchHit[];
      }
    }),
  );

  return {
    hits: perCollege.flat(),
    collegesSearched: targets.length,
    collegesSkipped: skipped,
    tookMs: Date.now() - started,
  };
}

export { MAX_COLLEGES_PER_SEARCH };
