"use client";
import React, { useState } from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";

export default function OPACPage() {
  const { data: books, loading, addRecord: addReservation } = useTenantCollection("reservations");
  const { data: allBooks } = useTenantCollection("books");
  const [search, setSearch] = useState("");
  const [catFilter, setCatFilter] = useState("All Categories");
  const [reserving, setReserving] = useState<string | null>(null);

  const bookList = allBooks ?? [];
  const categories = ["All Categories", ...Array.from(new Set(bookList.map(b => b.category).filter(Boolean)))];

  const filtered = bookList.filter(b => {
    const q = search.toLowerCase();
    const matchSearch = !q || (b.title || "").toLowerCase().includes(q) || (b.author || "").toLowerCase().includes(q);
    const matchCat = catFilter === "All Categories" || b.category === catFilter;
    return matchSearch && matchCat;
  });

  const handleReserve = async (book: any) => {
    const memberName = window.prompt(`Reserve "${book.title}"\n\nEnter your full name to reserve:`);
    if (!memberName?.trim()) return;
    setReserving(book.id);
    await addReservation({
      bookId: book.id,
      bookTitle: book.title,
      memberName: memberName.trim(),
      reservedDate: new Date().toISOString().split("T")[0],
      status: "Pending",
      createdAt: Date.now(),
    });
    setReserving(null);
    alert(`Book reserved for ${memberName}! The librarian will confirm your reservation.`);
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-500 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex items-center gap-4">
        <span className="text-4xl">🔍</span>
        <div>
          <h1 className="text-3xl font-extrabold text-ink">Online Public Access Catalog</h1>
          <p className="text-sm text-muted">Search the GDC Library database and reserve books</p>
        </div>
      </div>

      {/* Search Bar */}
      <div className="rounded-xl border-2 border-accent bg-surface px-5 py-3 flex gap-3 items-center">
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search by Title, Author, or Category..."
          className="flex-1 bg-transparent outline-none text-ink text-base placeholder:text-muted"
          autoFocus
        />
        <select value={catFilter} onChange={(e) => setCatFilter(e.target.value)}
          className="bg-line border-none rounded-lg px-4 py-1.5 text-sm text-body outline-none">
          {categories.map(c => <option key={c}>{c}</option>)}
        </select>
      </div>

      {/* Results */}
      <div className="rounded-xl border border-line bg-surface-2 p-6 shadow-xl overflow-hidden">
        <div className="flex justify-between items-center mb-4">
          <h3 className="text-lg font-bold text-ink">Search Results</h3>
          <span className="text-sm text-muted">Found {filtered.length} books</span>
        </div>

        {loading ? <div className="py-12 flex justify-center"><div className="h-8 w-8 animate-spin rounded-full border-4 border-accent border-t-transparent" /></div> : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-body">
              <thead className="text-xs uppercase bg-surface/40 text-muted">
                <tr>
                  <th className="px-4 py-3">Title</th>
                  <th className="px-4 py-3">Author</th>
                  <th className="px-4 py-3">Category</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Digital</th>
                  <th className="px-4 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line/40">
                {filtered.map(b => (
                  <tr key={b.id} className="hover:bg-surface-2/10">
                    <td className="px-4 py-4 font-semibold text-ink">{b.title}</td>
                    <td className="px-4 py-4">{b.author || "—"}</td>
                    <td className="px-4 py-4"><span className="text-xs bg-surface-2/40 text-accent px-2 py-0.5 rounded font-bold">{b.category || "—"}</span></td>
                    <td className="px-4 py-4">
                      <span className={`text-xs font-black ${b.status === "Available" ? "text-positive" : "text-warning"}`}>{b.status || "Available"}</span>
                    </td>
                    <td className="px-4 py-4">
                      {b.isDigital && <span className="text-xs text-accent font-bold">📱 Available</span>}
                    </td>
                    <td className="px-4 py-4 text-right space-x-2">
                      {b.isDigital && b.digitalUrl && (
                        <a href={b.digitalUrl} target="_blank" rel="noopener noreferrer"
                          className="inline-block px-2 py-1 rounded bg-accent-bg/10 border border-accent/30 text-xs font-bold text-accent hover:bg-accent-bg/20 transition-colors">
                          📖 Read
                        </a>
                      )}
                      <button
                        onClick={() => handleReserve(b)}
                        disabled={b.status !== "Available" || reserving === b.id}
                        className={`px-3 py-1 rounded text-xs font-bold transition-colors ${
                          b.status === "Available"
                            ? "bg-accent-bg text-on-accent hover:bg-accent-bg"
                            : "bg-surface-2/40 text-muted cursor-not-allowed"
                        }`}
                      >
                        {reserving === b.id ? "..." : "Reserve"}
                      </button>
                    </td>
                  </tr>
                ))}
                {filtered.length === 0 && <tr><td colSpan={6} className="text-center py-12 text-muted">No books found. Try a different search term.</td></tr>}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
