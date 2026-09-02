"use client";
import React, { useState, useCallback } from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";
import { COLLECTIONS } from "@/lib/schema";

interface Recommendation {
  title: string;
  author: string;
  category: string;
  stars: number;
  reason: string;
}

function generateRecommendations(books: any[], issues: any[], memberId: string, categoryFilter: string): Recommendation[] {
  const bookMap: Record<string, any> = {};
  books.forEach(b => { if (b.id) bookMap[b.id] = b; });

  const userBooks: Record<string, Set<string>> = {};
  const bookPopularity: Record<string, number> = {};

  issues.forEach(issue => {
    const mid = issue.memberId || "";
    const bid = issue.bookId || "";
    if (mid && bid) {
      if (!userBooks[mid]) userBooks[mid] = new Set();
      userBooks[mid].add(bid);
      bookPopularity[bid] = (bookPopularity[bid] || 0) + 1;
    }
  });

  const results: Recommendation[] = [];
  const passesFilter = (cat: string) => !categoryFilter || categoryFilter === "All" || (cat || "").toLowerCase().includes(categoryFilter.toLowerCase());

  if (memberId && userBooks[memberId]) {
    const targetBooks = userBooks[memberId];
    const bookScores: Record<string, number> = {};

    Object.entries(userBooks).forEach(([mid, bSet]) => {
      if (mid === memberId) return;
      const common = [...targetBooks].filter(bid => bSet.has(bid)).length;
      if (common > 0) {
        bSet.forEach(bid => {
          if (!targetBooks.has(bid)) bookScores[bid] = (bookScores[bid] || 0) + common;
        });
      }
    });

    const maxScore = Math.max(...Object.values(bookScores), 1);
    Object.entries(bookScores).sort(([,a],[,b]) => b - a).slice(0, 20).forEach(([bid, score]) => {
      const b = bookMap[bid];
      if (!b || !passesFilter(b.category)) return;
      results.push({ title: b.title, author: b.author || "Unknown", category: b.category || "General", stars: Math.round((score / maxScore) * 50) / 10, reason: "Popular among similar members" });
    });

    if (!results.length) {
      // Fallback to popular
      Object.entries(bookPopularity).sort(([,a],[,b]) => b - a).slice(0, 12).forEach(([bid, pop]) => {
        const b = bookMap[bid];
        if (!b || targetBooks.has(bid) || !passesFilter(b.category)) return;
        results.push({ title: b.title, author: b.author || "Unknown", category: b.category || "General", stars: Math.round((pop / Math.max(...Object.values(bookPopularity), 1)) * 50) / 10, reason: "Recommended for you" });
      });
    }
  } else {
    // Trending
    const maxPop = Math.max(...Object.values(bookPopularity), 1);
    Object.entries(bookPopularity).sort(([,a],[,b]) => b - a).slice(0, 20).forEach(([bid, pop]) => {
      const b = bookMap[bid];
      if (!b || !passesFilter(b.category)) return;
      results.push({ title: b.title, author: b.author || "Unknown", category: b.category || "General", stars: Math.round((pop / maxPop) * 50) / 10, reason: "Trending this month" });
    });
    if (!results.length) {
      books.filter(b => passesFilter(b.category)).slice(0, 12).forEach(b => {
        results.push({ title: b.title, author: b.author || "Unknown", category: b.category || "General", stars: 3.0, reason: "New Arrival" });
      });
    }
  }
  return results.slice(0, 20);
}

export default function AIRecommenderPage() {
  const { data: books } = useTenantCollection(COLLECTIONS.books);
  const { data: issues } = useTenantCollection(COLLECTIONS.issuedBooks);
  const [memberId, setMemberId] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("All");
  const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
  const [loading, setLoading] = useState(false);
  const [hasLoaded, setHasLoaded] = useState(false);

  const categories = ["All", ...Array.from(new Set((books ?? []).map(b => b.category).filter(Boolean)))];

  const loadRecommendations = useCallback(() => {
    setLoading(true);
    setHasLoaded(false);
    setTimeout(() => {
      const recs = generateRecommendations(books ?? [], issues ?? [], memberId.trim(), categoryFilter);
      setRecommendations(recs);
      setLoading(false);
      setHasLoaded(true);
    }, 800);
  }, [books, issues, memberId, categoryFilter]);

  const starsDisplay = (s: number) => {
    const full = Math.floor(s);
    const empty = 5 - full;
    return "★".repeat(full) + "☆".repeat(empty);
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-500 max-w-6xl mx-auto">
      <div>
        <h1 className="text-3xl font-extrabold text-[#E8EEF8]">🤖 AI Smart Book Recommender</h1>
        <p className="text-sm text-slate-400">Collaborative filtering engine — analyzes borrowing patterns to surface personalized picks</p>
      </div>

      {/* Controls */}
      <div className="flex gap-3 flex-wrap">
        <input
          type="text"
          value={memberId}
          onChange={(e) => setMemberId(e.target.value)}
          placeholder="Enter Member ID for personalized picks (leave blank for trending)..."
          className="flex-1 min-w-[260px] rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2.5 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]"
        />
        <select value={categoryFilter} onChange={(e) => setCategoryFilter(e.target.value)}
          className="rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2.5 text-sm text-[#E8EEF8] outline-none">
          {categories.map(c => <option key={c}>{c}</option>)}
        </select>
        <button
          onClick={loadRecommendations}
          disabled={loading}
          className="px-6 py-2.5 rounded-lg bg-blue-600 text-white font-bold text-sm hover:bg-blue-500 disabled:opacity-60 transition-colors shadow"
        >
          {loading ? "Analyzing..." : "🔄 Get Recommendations"}
        </button>
      </div>

      {loading && (
        <div className="py-16 flex flex-col items-center gap-4">
          <div className="h-12 w-12 border-4 border-blue-500 border-t-transparent rounded-full animate-spin" />
          <p className="text-blue-400 font-bold animate-pulse">Analyzing borrowing patterns...</p>
        </div>
      )}

      {!loading && !hasLoaded && (
        <div className="py-16 flex flex-col items-center gap-3 text-slate-500">
          <span className="text-5xl">🤖</span>
          <p className="font-bold text-lg">Enter details and click Get Recommendations</p>
          <p className="text-sm">The AI engine uses collaborative filtering to identify relevant books</p>
        </div>
      )}

      {!loading && hasLoaded && recommendations.length === 0 && (
        <div className="py-16 flex flex-col items-center gap-3 text-slate-500">
          <span className="text-4xl">📭</span>
          <p className="font-bold">No recommendations found for this criteria.</p>
          <p className="text-sm">Try clearing the Member ID or changing the category filter.</p>
        </div>
      )}

      {!loading && recommendations.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {recommendations.map((rec, i) => (
            <div
              key={i}
              className="rounded-xl border border-[#1E3050] bg-[#0D1F38] p-5 hover:border-blue-700 transition-all hover:shadow-lg hover:shadow-blue-900/20"
            >
              <p className="font-bold text-white text-base leading-snug mb-1">{rec.title}</p>
              <p className="text-sm text-slate-400 mb-3">{rec.author} • <span className="text-blue-400">{rec.category}</span></p>
              <div className="flex items-center justify-between">
                <span className="text-[#C8A84B] font-bold text-sm">
                  {starsDisplay(rec.stars)} <span className="text-xs text-slate-400 ml-1">{rec.stars.toFixed(1)}</span>
                </span>
                <span className="text-xs bg-blue-500/10 text-blue-400 border border-blue-500/20 px-3 py-1 rounded-full font-bold">
                  {rec.reason}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
