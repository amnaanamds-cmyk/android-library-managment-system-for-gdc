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
          <h1 className="text-3xl font-extrabold text-[#E8EEF8]">Online Public Access Catalog</h1>
          <p className="text-sm text-slate-400">Search the GDC Library database and reserve books</p>
        </div>
      </div>

      {/* Search Bar */}
      <div className="rounded-xl border-2 border-[#1E5FD4] bg-[#0D1F38] px-5 py-3 flex gap-3 items-center">
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search by Title, Author, or Category..."
          className="flex-1 bg-transparent outline-none text-[#E8EEF8] text-base placeholder:text-slate-500"
          autoFocus
        />
        <select value={catFilter} onChange={(e) => setCatFilter(e.target.value)}
          className="bg-[#1E3050] border-none rounded-lg px-4 py-1.5 text-sm text-slate-300 outline-none">
          {categories.map(c => <option key={c}>{c}</option>)}
        </select>
      </div>

      {/* Results */}
      <div className="rounded-xl border border-blue-950 bg-[#070F1E] p-6 shadow-xl overflow-hidden">
        <div className="flex justify-between items-center mb-4">
          <h3 className="text-lg font-bold text-white">Search Results</h3>
          <span className="text-sm text-slate-400">Found {filtered.length} books</span>
        </div>

        {loading ? <div className="py-12 flex justify-center"><div className="h-8 w-8 animate-spin rounded-full border-4 border-[#C8A84B] border-t-transparent" /></div> : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-slate-300">
              <thead className="text-xs uppercase bg-[#0D1F38]/40 text-slate-400">
                <tr>
                  <th className="px-4 py-3">Title</th>
                  <th className="px-4 py-3">Author</th>
                  <th className="px-4 py-3">Category</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Digital</th>
                  <th className="px-4 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-blue-950/40">
                {filtered.map(b => (
                  <tr key={b.id} className="hover:bg-blue-950/10">
                    <td className="px-4 py-4 font-semibold text-white">{b.title}</td>
                    <td className="px-4 py-4">{b.author || "—"}</td>
                    <td className="px-4 py-4"><span className="text-xs bg-blue-900/40 text-blue-300 px-2 py-0.5 rounded font-bold">{b.category || "—"}</span></td>
                    <td className="px-4 py-4">
                      <span className={`text-xs font-black ${b.status === "Available" ? "text-emerald-400" : "text-amber-400"}`}>{b.status || "Available"}</span>
                    </td>
                    <td className="px-4 py-4">
                      {b.isDigital && <span className="text-xs text-blue-400 font-bold">📱 Available</span>}
                    </td>
                    <td className="px-4 py-4 text-right space-x-2">
                      {b.isDigital && b.digitalUrl && (
                        <a href={b.digitalUrl} target="_blank" rel="noopener noreferrer"
                          className="inline-block px-2 py-1 rounded bg-blue-500/10 border border-blue-500/30 text-xs font-bold text-blue-400 hover:bg-blue-500/20 transition-colors">
                          📖 Read
                        </a>
                      )}
                      <button
                        onClick={() => handleReserve(b)}
                        disabled={b.status !== "Available" || reserving === b.id}
                        className={`px-3 py-1 rounded text-xs font-bold transition-colors ${
                          b.status === "Available"
                            ? "bg-blue-600 text-white hover:bg-blue-500"
                            : "bg-slate-700/40 text-slate-500 cursor-not-allowed"
                        }`}
                      >
                        {reserving === b.id ? "..." : "Reserve"}
                      </button>
                    </td>
                  </tr>
                ))}
                {filtered.length === 0 && <tr><td colSpan={6} className="text-center py-12 text-slate-500">No books found. Try a different search term.</td></tr>}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
