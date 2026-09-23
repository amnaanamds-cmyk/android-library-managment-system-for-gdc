"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useDirectorateNetwork } from "@/lib/directorate";
import { searchUnionCatalogue, UnionSearchResult, MAX_COLLEGES_PER_SEARCH } from "@/lib/search";
import { PageHeader, Card, Input, Button, Badge, EmptyState, Spinner } from "@/components/ui";
import { IconSearch } from "@/components/icons";

/**
 * Union catalogue search across every approved college's public books.
 *
 * On-demand only — see lib/search.ts for why. This page's own job is to
 * make that boundedness visible rather than silent: it always reports how
 * many institutions were actually searched and how many were skipped,
 * because a directorate acting on "no results" needs to know whether that
 * means the network truly has nothing, or that the search only reached
 * the first 40 of 350 institutions.
 */
export default function UnionSearch() {
  const { colleges, loading } = useDirectorateNetwork();
  const [term, setTerm] = useState("");
  const [result, setResult] = useState<UnionSearchResult | null>(null);
  const [searching, setSearching] = useState(false);

  const runSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!term.trim() || searching) return;
    setSearching(true);
    try {
      setResult(await searchUnionCatalogue(colleges, term));
    } finally {
      setSearching(false);
    }
  };

  if (loading) return <Spinner label="Loading network…" />;

  return (
    <div>
      <PageHeader
        title="Union Catalogue Search"
        description={`Search titles and ISBNs across up to ${MAX_COLLEGES_PER_SEARCH} approved institutions at once, run on demand — never automatically, and never on every keystroke.`}
      />

      <form onSubmit={runSearch} className="mb-6 flex gap-2">
        <Input
          value={term}
          onChange={(e) => setTerm(e.target.value)}
          placeholder="Title prefix or exact ISBN…"
          className="flex-1"
        />
        <Button variant="primary" type="submit" disabled={searching || !term.trim()} icon={<IconSearch className="h-3.5 w-3.5" />}>
          {searching ? "Searching…" : "Search"}
        </Button>
      </form>

      {!result && !searching && (
        <EmptyState icon={<IconSearch className="h-8 w-8" />} title="Search the network" detail="Results come from each college's public catalogue only — never patron or loan records." />
      )}

      {searching && <Spinner label="Querying institutions…" />}

      {result && !searching && (
        <>
          <p className="mb-4 text-xs text-slate-500">
            Searched {result.collegesSearched} {result.collegesSearched === 1 ? "institution" : "institutions"}
            {result.collegesSkipped > 0 && (
              <span className="text-amber-400"> · {result.collegesSkipped} not searched (beyond the {MAX_COLLEGES_PER_SEARCH}-institution limit per query)</span>
            )}
            {" "}· {result.tookMs}ms · {result.hits.length} {result.hits.length === 1 ? "result" : "results"}
          </p>

          {result.hits.length === 0 ? (
            <EmptyState icon={<IconSearch className="h-8 w-8" />} title="No matches" />
          ) : (
            <div className="space-y-2">
              {result.hits.map((h) => (
                <Link
                  key={`${h.collegeId}-${h.bookId}`}
                  href={`/${encodeURIComponent(h.collegeId)}`}
                  className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-800 bg-[#0B1220] px-4 py-3 hover:border-slate-700"
                >
                  <div>
                    <p className="text-sm font-semibold text-white">{h.title}</p>
                    <p className="text-xs text-slate-500">{h.author || "Unknown author"}{h.isbn ? ` · ${h.isbn}` : ""}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-slate-400">{h.collegeName}</span>
                    <Badge tone={h.status === "Available" ? "emerald" : "amber"}>{h.status}</Badge>
                  </div>
                </Link>
              ))}
            </div>
          )}
        </>
      )}

      <Card className="mt-8 border-slate-800/60 bg-transparent">
        <p className="text-[11px] text-slate-600">
          Bounded by design: a network of 350 institutions searched naively is 350 collection reads for one query
          typed once. This page caps fan-out per search rather than repeating that mistake.
        </p>
      </Card>
    </div>
  );
}
