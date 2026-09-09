"use client";

import React, { useState } from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";

export default function DigitalLibraryPage() {
  const { data: books, loading, addRecord } = useTenantCollection("books");
  const [search, setSearch] = useState("");
  const [catFilter, setCatFilter] = useState("All Categories");
  const [selected, setSelected] = useState<any>(null);

  // Show resource modal state
  const [showAddModal, setShowAddModal] = useState(false);
  const [newTitle, setNewTitle] = useState("");
  const [newAuthor, setNewAuthor] = useState("");
  const [newUrl, setNewUrl] = useState("");
  const [newCategory, setNewCategory] = useState("Digital Repository");
  const [adding, setAdding] = useState(false);

  const digitalBooks = books?.filter((b) => b.isDigital) ?? [];
  const categories = ["All Categories", ...Array.from(new Set(digitalBooks.map((b) => b.category).filter(Boolean)))];

  const filtered = digitalBooks.filter((b) => {
    const q = search.toLowerCase();
    const matchSearch = !q || b.title?.toLowerCase().includes(q) || b.author?.toLowerCase().includes(q);
    const matchCat = catFilter === "All Categories" || b.category === catFilter;
    return matchSearch && matchCat;
  });

  const handleAddResource = async () => {
    if (!newTitle.trim() || !newUrl.trim()) return;
    setAdding(true);
    await addRecord({
      title: newTitle.trim(),
      author: newAuthor.trim(),
      isDigital: true,
      digitalUrl: newUrl.trim(),
      category: newCategory.trim(),
      status: "Available",
      createdAt: Date.now(),
    });
    setNewTitle(""); setNewAuthor(""); setNewUrl(""); setNewCategory("Digital Repository");
    setAdding(false);
    setShowAddModal(false);
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-500 max-w-7xl mx-auto">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold text-ink">🌐 Digital Repository</h1>
          <p className="text-sm text-muted">Access institutional E-Books, Journals, and PDF resources</p>
        </div>
        <button onClick={() => setShowAddModal(true)} className="px-5 py-2.5 rounded-lg bg-accent-bg text-on-accent font-bold text-sm hover:bg-accent-bg transition-colors shadow">
          ➕ Upload New Resource
        </button>
      </div>

      {/* Search & Filter */}
      <div className="flex gap-4">
        <input
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="🔍 Search digital catalog..."
          className="flex-1 rounded-lg border border-line bg-surface px-4 py-2 text-sm text-ink outline-none focus:border-accent"
        />
        <select
          value={catFilter}
          onChange={(e) => setCatFilter(e.target.value)}
          className="rounded-lg border border-line bg-surface px-4 py-2 text-sm text-ink outline-none"
        >
          {categories.map((c) => <option key={c}>{c}</option>)}
        </select>
      </div>

      {/* Main Split View */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Resource List */}
        <div className="lg:col-span-2 rounded-xl border border-line bg-surface-2 p-6 shadow-xl overflow-hidden">
          {loading ? (
            <div className="py-12 flex justify-center"><div className="h-8 w-8 animate-spin rounded-full border-4 border-accent border-t-transparent" /></div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-body">
                <thead className="text-xs uppercase bg-surface/40 text-muted">
                  <tr>
                    <th className="px-4 py-3">Title</th>
                    <th className="px-4 py-3">Author</th>
                    <th className="px-4 py-3">Category</th>
                    <th className="px-4 py-3">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line/40">
                  {filtered.map((b) => (
                    <tr
                      key={b.id}
                      className={`hover:bg-surface-2/20 cursor-pointer ${selected?.id === b.id ? "bg-surface-2/20 border-l-2 border-accent" : ""}`}
                      onClick={() => setSelected(b)}
                    >
                      <td className="px-4 py-3 font-semibold text-ink">{b.title}</td>
                      <td className="px-4 py-3">{b.author || "—"}</td>
                      <td className="px-4 py-3"><span className="text-xs bg-surface-2/40 text-accent px-2 py-0.5 rounded font-bold">{b.category}</span></td>
                      <td className="px-4 py-3 text-positive text-xs font-bold">Available</td>
                    </tr>
                  ))}
                  {filtered.length === 0 && (
                    <tr><td colSpan={4} className="text-center py-8 text-muted">No digital resources found.</td></tr>
                  )}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Preview Panel */}
        <div className="rounded-xl border border-line bg-surface-2 p-6 shadow-xl flex flex-col justify-between min-h-[300px]">
          {selected ? (
            <div className="space-y-4">
              <h3 className="text-lg font-bold text-ink leading-snug">{selected.title}</h3>
              <div className="space-y-1 text-sm text-muted">
                <p>Author: <span className="text-body">{selected.author || "Unknown"}</span></p>
                <p>Category: <span className="text-body">{selected.category || "—"}</span></p>
                <p>ISBN: <span className="text-body">{selected.isbn || "—"}</span></p>
              </div>
              <div className="flex-1" />
              <a
                href={selected.digitalUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="block w-full text-center py-3 rounded-lg bg-positive text-on-accent font-black text-sm hover:bg-positive transition-colors shadow-lg"
              >
                📖 READ NOW
              </a>
            </div>
          ) : (
            <div className="flex-1 flex flex-col items-center justify-center text-center text-muted">
              <span className="text-5xl mb-4">🌐</span>
              <p className="font-bold">Select a resource</p>
              <p className="text-xs mt-1">Click any row to preview</p>
            </div>
          )}
        </div>
      </div>

      {/* Add Resource Modal */}
      {showAddModal && (
        <div className="fixed inset-0 bg-black/70 flex items-center justify-center z-50 p-4">
          <div className="rounded-2xl border border-line bg-app p-8 shadow-2xl w-full max-w-md">
            <h2 className="text-xl font-bold text-ink mb-6">➕ Upload New Digital Resource</h2>
            <div className="space-y-4">
              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-muted mb-1">Resource Title *</label>
                <input type="text" value={newTitle} onChange={(e) => setNewTitle(e.target.value)} className="w-full rounded-lg border border-line bg-surface px-4 py-2.5 text-sm text-ink outline-none focus:border-accent" placeholder="e.g. Physics Vol. 3" />
              </div>
              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-muted mb-1">Author</label>
                <input type="text" value={newAuthor} onChange={(e) => setNewAuthor(e.target.value)} className="w-full rounded-lg border border-line bg-surface px-4 py-2.5 text-sm text-ink outline-none focus:border-accent" placeholder="e.g. Dr. Khalid" />
              </div>
              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-muted mb-1">Digital URL / Link *</label>
                <input type="url" value={newUrl} onChange={(e) => setNewUrl(e.target.value)} className="w-full rounded-lg border border-line bg-surface px-4 py-2.5 text-sm text-ink outline-none focus:border-accent" placeholder="https://drive.google.com/..." />
              </div>
              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-muted mb-1">Category</label>
                <input type="text" value={newCategory} onChange={(e) => setNewCategory(e.target.value)} className="w-full rounded-lg border border-line bg-surface px-4 py-2.5 text-sm text-ink outline-none focus:border-accent" />
              </div>
            </div>
            <div className="flex gap-3 mt-6">
              <button onClick={handleAddResource} disabled={adding} className="flex-1 py-2.5 rounded-lg bg-accent-bg text-on-accent font-bold text-sm hover:bg-accent-bg disabled:opacity-50 transition-colors">
                {adding ? "Adding..." : "Add Resource"}
              </button>
              <button onClick={() => setShowAddModal(false)} className="flex-1 py-2.5 rounded-lg border border-line text-muted font-bold text-sm hover:bg-surface-2 transition-colors">
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
