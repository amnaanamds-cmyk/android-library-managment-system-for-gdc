"use client";
import React, { useState } from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";

const DDC_CLASSES: Record<string, string> = {
  "000": "General Works", "100": "Philosophy", "200": "Religion",
  "300": "Social Sciences", "400": "Language", "500": "Natural Sciences",
  "600": "Applied Sciences", "700": "Arts", "800": "Literature", "900": "History",
};

function suggestDDC(category: string): string {
  const cat = (category || "").toLowerCase();
  const rules: [string, string][] = [
    ["computer", "004"], ["software", "005"], ["library", "020"],
    ["islam", "297"], ["religion", "200"], ["philosophy", "100"],
    ["psychology", "150"], ["economics", "330"], ["law", "340"],
    ["education", "370"], ["math", "510"], ["physics", "530"],
    ["chemistry", "540"], ["biology", "570"], ["medicine", "610"],
    ["engineering", "620"], ["management", "658"], ["agriculture", "630"],
    ["art", "700"], ["music", "780"], ["literature", "800"],
    ["history", "900"], ["pakistan", "954.91"], ["geography", "910"],
  ];
  for (const [kw, code] of rules) if (cat.includes(kw)) return code;
  return "020";
}

function buildCutter(author: string): string {
  const parts = (author || "").trim().split(" ");
  if (!parts.length) return "";
  let cutter = parts[parts.length - 1].substring(0, 3).toUpperCase();
  if (parts.length > 1) cutter += parts[0][0].toUpperCase();
  return cutter;
}

function SpineLabelPreview({ callNo, cutter, year, title }: { callNo: string; cutter: string; year: string; title: string }) {
  return (
    <div className="w-24 bg-white rounded border-2 border-slate-600 overflow-hidden text-center select-none" style={{ height: 140 }}>
      <div className="bg-blue-800 px-1 py-1">
        <p className="text-white text-[8px] font-bold truncate">{(title || "").substring(0, 12)}</p>
      </div>
      <div className="py-2">
        <p className="text-slate-900 text-base font-extrabold font-mono leading-tight">{callNo || "---"}</p>
        <p className="text-slate-700 text-xs font-mono">{cutter || ""}</p>
        <p className="text-slate-500 text-xs font-mono">{year || ""}</p>
      </div>
      <div className="bg-blue-800 px-1 py-1 mt-auto">
        <p className="text-white text-[7px]">NEXLIB</p>
      </div>
    </div>
  );
}

export default function SpineLabelPage() {
  const { data: books, loading, updateRecord } = useTenantCollection("books");
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState("All");
  const [editingBook, setEditingBook] = useState<any>(null);
  const [callNo, setCallNo] = useState("");
  const [cutter, setCutter] = useState("");
  const [year, setYear] = useState(new Date().getFullYear().toString());
  const [saving, setSaving] = useState(false);

  const filtered = (books || []).filter((b: any) => {
    const q = search.toLowerCase();
    const matchQ = !q || (b.title || "").toLowerCase().includes(q) || (b.author || "").toLowerCase().includes(q) || (b.accNo || "").toLowerCase().includes(q);
    const matchF = filter === "All" || (filter === "Done" && b.callNumber) || (filter === "Missing" && !b.callNumber);
    return matchQ && matchF;
  });

  const done = (books || []).filter((b: any) => b.callNumber).length;

  const openEdit = (book: any) => {
    setEditingBook(book);
    setCallNo(book.callNumber || suggestDDC(book.category || ""));
    setCutter(book.authorCutter || buildCutter(book.author || ""));
    setYear(book.publishDate?.substring(0, 4) || new Date().getFullYear().toString());
  };

  const saveLabel = async () => {
    if (!editingBook) return;
    setSaving(true);
    await updateRecord(editingBook.id, { callNumber: callNo, authorCutter: cutter });
    setSaving(false);
    setEditingBook(null);
  };

  const handlePrint = () => {
    window.print();
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-500">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold text-[#E8EEF8]">🏷️ Spine Label Generator</h1>
          <p className="text-sm text-slate-400">Generate and print DDC call number spine labels for physical books.</p>
        </div>
        <button onClick={handlePrint}
          className="px-4 py-2 rounded-lg bg-emerald-600 text-white font-bold text-sm hover:bg-emerald-500 transition-colors">
          🖨️ Print All Labels
        </button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-3 gap-4">
        {[
          { label: "Total Books", value: books?.length || 0, color: "text-blue-400" },
          { label: "Labels Done", value: done, color: "text-emerald-400" },
          { label: "Labels Missing", value: (books?.length || 0) - done, color: "text-amber-400" },
        ].map(s => (
          <div key={s.label} className="rounded-xl border border-blue-950 bg-[#070F1E] p-4 text-center">
            <div className={`text-3xl font-extrabold ${s.color}`}>{s.value}</div>
            <div className="text-xs text-slate-400 mt-1">{s.label}</div>
          </div>
        ))}
      </div>

      {/* DDC Reference */}
      <div className="rounded-xl border border-blue-950 bg-[#070F1E] p-4">
        <p className="text-xs font-bold text-slate-400 mb-2">📖 DDC Quick Reference</p>
        <div className="flex flex-wrap gap-2">
          {Object.entries(DDC_CLASSES).map(([code, name]) => (
            <span key={code} className="px-2 py-1 rounded bg-[#1E3050] text-xs text-slate-300 font-mono">
              <b>{code}</b> — {name}
            </span>
          ))}
        </div>
      </div>

      {/* Edit Panel */}
      {editingBook && (
        <div className="rounded-xl border border-amber-900/50 bg-[#070F1E] p-6 shadow-xl">
          <div className="flex justify-between items-center mb-4">
            <h2 className="text-lg font-bold text-amber-300">Edit Label: <span className="text-white">{editingBook.title}</span></h2>
            <button onClick={() => setEditingBook(null)} className="text-slate-400 hover:text-white text-sm">✕ Cancel</button>
          </div>
          <div className="flex gap-8 items-start">
            <div className="flex-1 space-y-3">
              <div>
                <label className="text-xs text-slate-400 font-bold block mb-1">DDC Call Number *</label>
                <input value={callNo} onChange={e => setCallNo(e.target.value)}
                  className="w-full rounded border border-[#1E3050] bg-[#0D1F38] px-3 py-2 text-sm text-[#E8EEF8] outline-none focus:border-amber-500 font-mono" />
                <p className="text-xs text-slate-500 mt-1">Suggested: <span className="font-mono text-amber-400">{suggestDDC(editingBook.category || "")}</span>
                  <button onClick={() => setCallNo(suggestDDC(editingBook.category || ""))}
                    className="ml-2 text-xs px-1.5 py-0.5 rounded bg-amber-900/30 text-amber-400 hover:bg-amber-800/30">Use</button>
                </p>
              </div>
              <div>
                <label className="text-xs text-slate-400 font-bold block mb-1">Author Cutter</label>
                <input value={cutter} onChange={e => setCutter(e.target.value)}
                  className="w-full rounded border border-[#1E3050] bg-[#0D1F38] px-3 py-2 text-sm text-[#E8EEF8] outline-none focus:border-amber-500 font-mono" />
              </div>
              <div>
                <label className="text-xs text-slate-400 font-bold block mb-1">Year</label>
                <input value={year} onChange={e => setYear(e.target.value)}
                  className="w-full rounded border border-[#1E3050] bg-[#0D1F38] px-3 py-2 text-sm text-[#E8EEF8] outline-none focus:border-amber-500 font-mono" />
              </div>
              <button onClick={saveLabel} disabled={saving}
                className="w-full py-2 rounded-lg bg-emerald-600 text-white font-bold text-sm hover:bg-emerald-500 disabled:opacity-50 transition-colors">
                {saving ? "Saving…" : "💾 Save Label"}
              </button>
            </div>
            {/* Preview */}
            <div className="flex flex-col items-center gap-2">
              <p className="text-xs text-slate-400 font-bold">Preview</p>
              <SpineLabelPreview callNo={callNo} cutter={cutter} year={year} title={editingBook.title} />
            </div>
          </div>
        </div>
      )}

      {/* Search & Filter */}
      <div className="flex gap-3">
        <input value={search} onChange={e => setSearch(e.target.value)} placeholder="🔍 Search books…"
          className="flex-1 rounded-lg border border-[#1E3050] bg-[#070F1E] px-4 py-2 text-sm text-[#E8EEF8] outline-none focus:border-amber-500" />
        <select value={filter} onChange={e => setFilter(e.target.value)}
          className="rounded-lg border border-[#1E3050] bg-[#070F1E] px-3 py-2 text-sm text-[#E8EEF8] outline-none">
          <option>All</option><option>Done</option><option>Missing</option>
        </select>
      </div>

      {/* Table */}
      <div className="rounded-xl border border-blue-950 bg-[#070F1E] overflow-hidden shadow-xl">
        {loading ? (
          <div className="py-12 flex justify-center"><div className="h-8 w-8 animate-spin rounded-full border-4 border-amber-500 border-t-transparent" /></div>
        ) : (
          <table className="w-full text-left text-sm text-slate-300">
            <thead className="text-xs uppercase bg-[#0D1F38]/40 text-slate-400">
              <tr>
                <th className="px-4 py-3">Acc No</th>
                <th className="px-4 py-3">Title</th>
                <th className="px-4 py-3">Author</th>
                <th className="px-4 py-3">Call No</th>
                <th className="px-4 py-3">Cutter</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-blue-950/40">
              {filtered.map((book: any) => (
                <tr key={book.id} className="hover:bg-blue-950/10 cursor-pointer" onDoubleClick={() => openEdit(book)}>
                  <td className="px-4 py-3 font-mono text-slate-400">{book.accNo || "—"}</td>
                  <td className="px-4 py-3 font-semibold text-white">{book.title}</td>
                  <td className="px-4 py-3">{book.author || "—"}</td>
                  <td className="px-4 py-3 font-mono text-amber-300">{book.callNumber || "—"}</td>
                  <td className="px-4 py-3 font-mono text-slate-400">{book.authorCutter || "—"}</td>
                  <td className="px-4 py-3">
                    {book.callNumber
                      ? <span className="text-xs font-bold text-emerald-400">✅ Done</span>
                      : <span className="text-xs font-bold text-amber-400">⏳ Missing</span>}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button onClick={() => openEdit(book)}
                      className="px-3 py-1 rounded bg-amber-500/10 border border-amber-500/30 text-xs font-bold text-amber-400 hover:bg-amber-500/20 transition-colors">
                      🏷️ Edit Label
                    </button>
                  </td>
                </tr>
              ))}
              {filtered.length === 0 && <tr><td colSpan={7} className="text-center py-8 text-slate-500">No books found.</td></tr>}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
