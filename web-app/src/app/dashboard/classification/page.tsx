"use client";
import React, { useState, useEffect } from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";

const DDC_SCHEDULE: Record<string, { name: string; subs: Record<string, string> }> = {
  "000": { name: "Computer Science & General Works", subs: { "004": "Data Processing", "005": "Computer Programming", "020": "Library Science" } },
  "100": { name: "Philosophy & Psychology", subs: { "150": "Psychology", "160": "Logic", "170": "Ethics" } },
  "200": { name: "Religion", subs: { "220": "Bible", "230": "Christian Theology", "297": "Islam" } },
  "300": { name: "Social Sciences", subs: { "320": "Political Science", "330": "Economics", "340": "Law", "370": "Education" } },
  "400": { name: "Language", subs: { "420": "English", "491": "Urdu", "492": "Arabic" } },
  "500": { name: "Natural Sciences", subs: { "510": "Mathematics", "530": "Physics", "540": "Chemistry", "570": "Biology" } },
  "600": { name: "Applied Sciences", subs: { "610": "Medicine", "620": "Engineering", "630": "Agriculture", "658": "Business Management" } },
  "700": { name: "Arts & Recreation", subs: { "780": "Music", "790": "Recreation", "796": "Sports" } },
  "800": { name: "Literature", subs: { "820": "English Literature", "823": "English Fiction", "891": "Urdu Literature" } },
  "900": { name: "History & Geography", subs: { "910": "Geography", "950": "History of Asia", "954.91": "Pakistan" } },
};

function autoClassify(title: string, category: string): string {
  const text = `${title} ${category}`.toLowerCase();
  const rules: [string, string][] = [
    ["computer", "004"], ["programming", "005"], ["software", "005"], ["library", "020"],
    ["islam", "297"], ["quran", "297.1"], ["philosophy", "100"], ["psychology", "150"],
    ["economics", "330"], ["law", "340"], ["education", "370"], ["political", "320"],
    ["math", "510"], ["physics", "530"], ["chemistry", "540"], ["biology", "570"],
    ["medicine", "610"], ["engineering", "620"], ["agriculture", "630"], ["management", "658"],
    ["music", "780"], ["sports", "796"], ["literature", "800"], ["urdu", "891"],
    ["history", "900"], ["pakistan", "954.91"], ["geography", "910"],
  ];
  for (const [kw, code] of rules) if (text.includes(kw)) return code;
  return "020";
}

export default function ClassificationPage() {
  const { data: books, loading, updateRecord } = useTenantCollection("books");
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState("All");
  const [editingBook, setEditingBook] = useState<any>(null);
  const [classNo, setClassNo] = useState("");
  const [subject, setSubject] = useState("");
  const [system, setSystem] = useState("DDC");
  const [saving, setSaving] = useState(false);
  const [autoRunning, setAutoRunning] = useState(false);
  const [expandedClass, setExpandedClass] = useState<string | null>(null);

  const filtered = (books || []).filter((b: any) => {
    const q = search.toLowerCase();
    const matchQ = !q || (b.title || "").toLowerCase().includes(q) || (b.author || "").toLowerCase().includes(q);
    const matchF = filter === "All" || (filter === "Classified" && b.classificationNo) || (filter === "Unclassified" && !b.classificationNo);
    return matchQ && matchF;
  });

  const classified = (books || []).filter((b: any) => b.classificationNo).length;

  const openEdit = (book: any) => {
    setEditingBook(book);
    setClassNo(book.classificationNo || autoClassify(book.title || "", book.category || ""));
    setSubject(book.subjectHeading || "");
    setSystem(book.classificationSystem || "DDC");
  };

  const saveClassification = async () => {
    if (!editingBook) return;
    setSaving(true);
    await updateRecord(editingBook.id, { classificationNo: classNo, subjectHeading: subject, classificationSystem: system });
    setSaving(false);
    setEditingBook(null);
  };

  const autoClassifyAll = async () => {
    const unclassified = (books || []).filter((b: any) => !b.classificationNo);
    if (!unclassified.length) { alert("All books are already classified!"); return; }
    if (!confirm(`Auto-classify ${unclassified.length} unclassified books using AI keyword analysis?`)) return;
    setAutoRunning(true);
    for (const book of unclassified) {
      const code = autoClassify(book.title || "", book.category || "");
      await updateRecord(book.id, { classificationNo: code, classificationSystem: "DDC" });
    }
    setAutoRunning(false);
    alert(`✅ Auto-classified ${unclassified.length} books!`);
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-500">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold text-[#E8EEF8]">🗂️ Book Classification</h1>
          <p className="text-sm text-slate-400">Assign DDC / LC classification numbers. Auto-suggest based on keywords.</p>
        </div>
        <div className="flex gap-2">
          <button onClick={autoClassifyAll} disabled={autoRunning}
            className="px-4 py-2 rounded-lg bg-violet-600 text-white font-bold text-sm hover:bg-violet-500 disabled:opacity-50 transition-colors">
            {autoRunning ? "🤖 Running..." : "🤖 Auto-Classify All"}
          </button>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-3 gap-4">
        {[
          { label: "Total Books", value: books?.length || 0, color: "text-blue-400" },
          { label: "Classified", value: classified, color: "text-emerald-400" },
          { label: "Pending", value: (books?.length || 0) - classified, color: "text-amber-400" },
        ].map(s => (
          <div key={s.label} className="rounded-xl border border-blue-950 bg-[#070F1E] p-4 text-center">
            <div className={`text-3xl font-extrabold ${s.color}`}>{s.value}</div>
            <div className="text-xs text-slate-400 mt-1">{s.label}</div>
          </div>
        ))}
      </div>

      {/* Edit Panel */}
      {editingBook && (
        <div className="rounded-xl border border-violet-900/50 bg-[#070F1E] p-6 shadow-xl">
          <div className="flex justify-between items-center mb-4">
            <h2 className="text-lg font-bold text-violet-300">Classify: <span className="text-white">{editingBook.title}</span></h2>
            <button onClick={() => setEditingBook(null)} className="text-slate-400 hover:text-white text-sm">✕ Cancel</button>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-4">
            <div>
              <label className="text-xs text-slate-400 font-bold block mb-1">Classification No. *</label>
              <input value={classNo} onChange={e => setClassNo(e.target.value)}
                className="w-full rounded border border-[#1E3050] bg-[#0D1F38] px-3 py-2 text-sm text-[#E8EEF8] outline-none focus:border-violet-500 font-mono" />
            </div>
            <div>
              <label className="text-xs text-slate-400 font-bold block mb-1">Subject Heading</label>
              <input value={subject} onChange={e => setSubject(e.target.value)}
                className="w-full rounded border border-[#1E3050] bg-[#0D1F38] px-3 py-2 text-sm text-[#E8EEF8] outline-none focus:border-violet-500" />
            </div>
            <div>
              <label className="text-xs text-slate-400 font-bold block mb-1">System</label>
              <select value={system} onChange={e => setSystem(e.target.value)}
                className="w-full rounded border border-[#1E3050] bg-[#0D1F38] px-3 py-2 text-sm text-[#E8EEF8] outline-none">
                <option>DDC</option><option>LC</option>
              </select>
            </div>
          </div>
          <div className="mb-4">
            <p className="text-xs text-slate-400 mb-2 font-bold">🤖 AI Suggested: <span className="text-blue-400 font-mono">{autoClassify(editingBook.title, editingBook.category)}</span>
              <button onClick={() => setClassNo(autoClassify(editingBook.title, editingBook.category))}
                className="ml-2 px-2 py-0.5 rounded bg-blue-900/40 text-blue-400 text-xs font-bold hover:bg-blue-800/40">Use</button>
            </p>
            {/* DDC Quick Picker */}
            <p className="text-xs text-slate-400 font-bold mb-2">Quick Pick from DDC Schedule:</p>
            <div className="flex flex-wrap gap-2 max-h-32 overflow-y-auto">
              {Object.entries(DDC_SCHEDULE).map(([code, data]) => (
                <div key={code}>
                  <button onClick={() => setExpandedClass(expandedClass === code ? null : code)}
                    className="px-2 py-1 rounded bg-[#1E3050] text-xs font-bold text-slate-300 hover:bg-blue-900/40">
                    {code} {data.name.split(" ")[0]}
                  </button>
                  {expandedClass === code && Object.entries(data.subs).map(([sc, sn]) => (
                    <button key={sc} onClick={() => { setClassNo(sc); setExpandedClass(null); }}
                      className="ml-1 px-2 py-0.5 rounded bg-violet-900/40 text-xs text-violet-300 hover:bg-violet-800/40">
                      {sc}
                    </button>
                  ))}
                </div>
              ))}
            </div>
          </div>
          <div className="flex justify-end">
            <button onClick={saveClassification} disabled={saving}
              className="px-6 py-2 rounded-lg bg-emerald-600 text-white font-bold text-sm hover:bg-emerald-500 disabled:opacity-50 transition-colors">
              {saving ? "Saving…" : "✅ Save Classification"}
            </button>
          </div>
        </div>
      )}

      {/* Search & Filter */}
      <div className="flex gap-3">
        <input value={search} onChange={e => setSearch(e.target.value)} placeholder="🔍 Search books…"
          className="flex-1 rounded-lg border border-[#1E3050] bg-[#070F1E] px-4 py-2 text-sm text-[#E8EEF8] outline-none focus:border-violet-500" />
        <select value={filter} onChange={e => setFilter(e.target.value)}
          className="rounded-lg border border-[#1E3050] bg-[#070F1E] px-3 py-2 text-sm text-[#E8EEF8] outline-none">
          <option>All</option><option>Classified</option><option>Unclassified</option>
        </select>
      </div>

      {/* Table */}
      <div className="rounded-xl border border-blue-950 bg-[#070F1E] overflow-hidden shadow-xl">
        {loading ? (
          <div className="py-12 flex justify-center"><div className="h-8 w-8 animate-spin rounded-full border-4 border-violet-500 border-t-transparent" /></div>
        ) : (
          <table className="w-full text-left text-sm text-slate-300">
            <thead className="text-xs uppercase bg-[#0D1F38]/40 text-slate-400">
              <tr>
                <th className="px-4 py-3">Title</th>
                <th className="px-4 py-3">Author</th>
                <th className="px-4 py-3">Category</th>
                <th className="px-4 py-3">Class No</th>
                <th className="px-4 py-3">System</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3 text-right">Action</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-blue-950/40">
              {filtered.map((book: any) => (
                <tr key={book.id} className="hover:bg-blue-950/10 cursor-pointer" onDoubleClick={() => openEdit(book)}>
                  <td className="px-4 py-3 font-semibold text-white">{book.title}</td>
                  <td className="px-4 py-3">{book.author || "—"}</td>
                  <td className="px-4 py-3">{book.category || "—"}</td>
                  <td className="px-4 py-3 font-mono text-blue-300">{book.classificationNo || "—"}</td>
                  <td className="px-4 py-3">{book.classificationSystem || "—"}</td>
                  <td className="px-4 py-3">
                    {book.classificationNo
                      ? <span className="text-xs font-bold text-emerald-400">✅ Done</span>
                      : <span className="text-xs font-bold text-amber-400">⏳ Pending</span>}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button onClick={() => openEdit(book)}
                      className="px-3 py-1 rounded bg-violet-500/10 border border-violet-500/30 text-xs font-bold text-violet-400 hover:bg-violet-500/20 transition-colors">
                      🗂️ Classify
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
