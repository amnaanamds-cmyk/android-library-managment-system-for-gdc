"use client";
import React, { useState } from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";

export default function MarcCatalogPage() {
  const { data: books, loading, updateRecord } = useTenantCollection("books");
  const [selectedBook, setSelectedBook] = useState<any>(null);
  const [marcRows, setMarcRows] = useState<{tag:string; ind:string; data:string}[]>([]);
  const [saving, setSaving] = useState(false);

  const openEditor = (book: any) => {
    setSelectedBook(book);
    let rows: {tag:string; ind:string; data:string}[] = [];
    if (book.marcData) { try { rows = JSON.parse(book.marcData); } catch {} }
    if (!rows.length) {
      rows = [
        { tag: "001", ind: "  ", data: book.id || "" },
        { tag: "020", ind: "  ", data: `$a ${book.isbn || ""}` },
        { tag: "100", ind: "1 ", data: `$a ${book.author || ""}` },
        { tag: "245", ind: "10", data: `$a ${book.title || ""}` },
        { tag: "260", ind: "  ", data: `$a ${book.publisher || ""}, $c ${book.publishDate || ""}` },
        { tag: "300", ind: "  ", data: `$a ${book.pages || ""} p.` },
        { tag: "650", ind: " #", data: `$a ${book.category || ""}` },
      ];
    }
    setMarcRows(rows);
  };

  const addRow = () => setMarcRows(prev => [...prev, { tag: "", ind: "", data: "" }]);
  const updateRow = (i: number, field: string, val: string) => setMarcRows(prev => prev.map((r, idx) => idx === i ? {...r, [field]: val} : r));
  const deleteRow = (i: number) => setMarcRows(prev => prev.filter((_, idx) => idx !== i));

  const saveMarc = async () => {
    if (!selectedBook) return;
    setSaving(true);
    await updateRecord(selectedBook.id, { marcData: JSON.stringify(marcRows) });
    setSaving(false);
    alert("MARC record saved successfully!");
    setSelectedBook(null);
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-500 max-w-7xl mx-auto">
      <div>
        <h1 className="text-3xl font-extrabold text-ink">📑 MARC 21 Cataloging</h1>
        <p className="text-sm text-muted">Standardized bibliographic data management for archival compliance</p>
      </div>
      {selectedBook ? (
        <div className="rounded-xl border border-warning/50 bg-surface-2 p-6 shadow-xl">
          <div className="flex justify-between items-center mb-6">
            <div>
              <h2 className="text-lg font-bold text-accent-strong">MARC 21 Metadata Grid</h2>
              <p className="text-sm text-muted">Editing: <span className="text-ink font-bold">{selectedBook.title}</span></p>
            </div>
            <button onClick={() => setSelectedBook(null)} className="text-sm text-muted hover:text-ink">← Back</button>
          </div>
          <div className="overflow-x-auto mb-4">
            <table className="w-full text-sm text-body">
              <thead className="text-xs uppercase bg-surface/40 text-muted">
                <tr>
                  <th className="px-4 py-3 w-24">Tag</th>
                  <th className="px-4 py-3 w-24">Indicators</th>
                  <th className="px-4 py-3">Data (Subfields)</th>
                  <th className="px-4 py-3 w-12"></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line/40">
                {marcRows.map((row, i) => (
                  <tr key={i}>
                    <td className="px-2 py-2"><input value={row.tag} onChange={(e) => updateRow(i, "tag", e.target.value)} className="w-full rounded border border-line bg-surface px-2 py-1.5 text-sm text-ink outline-none focus:border-accent font-mono" /></td>
                    <td className="px-2 py-2"><input value={row.ind} onChange={(e) => updateRow(i, "ind", e.target.value)} className="w-full rounded border border-line bg-surface px-2 py-1.5 text-sm text-ink outline-none focus:border-accent font-mono" /></td>
                    <td className="px-2 py-2"><input value={row.data} onChange={(e) => updateRow(i, "data", e.target.value)} className="w-full rounded border border-line bg-surface px-2 py-1.5 text-sm text-ink outline-none focus:border-accent font-mono" /></td>
                    <td className="px-2 py-2 text-center"><button onClick={() => deleteRow(i)} className="text-danger hover:text-danger text-xs font-bold">✕</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="flex justify-between">
            <button onClick={addRow} className="px-4 py-2 rounded-lg border border-line text-body text-sm font-bold hover:bg-line transition-colors">➕ Add Tag</button>
            <button onClick={saveMarc} disabled={saving} className="px-6 py-2 rounded-lg bg-positive text-on-accent font-bold text-sm hover:bg-positive disabled:opacity-50 transition-colors">{saving ? "Saving..." : "💾 Save MARC Record"}</button>
          </div>
        </div>
      ) : (
        <div className="rounded-xl border border-line bg-surface-2 p-6 shadow-xl">
          <h3 className="text-lg font-bold text-ink mb-4">Book Catalog — Double-click or click Edit to open MARC editor</h3>
          {loading ? <div className="py-12 flex justify-center"><div className="h-8 w-8 animate-spin rounded-full border-4 border-accent border-t-transparent" /></div> : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-body">
                <thead className="text-xs uppercase bg-surface/40 text-muted">
                  <tr>
                    <th className="px-4 py-3">Title</th>
                    <th className="px-4 py-3">Author</th>
                    <th className="px-4 py-3">ISBN</th>
                    <th className="px-4 py-3">MARC Status</th>
                    <th className="px-4 py-3 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line/40">
                  {books?.map((book) => (
                    <tr key={book.id} className="hover:bg-surface-2/10 cursor-pointer" onDoubleClick={() => openEditor(book)}>
                      <td className="px-4 py-4 font-semibold text-ink">{book.title}</td>
                      <td className="px-4 py-4">{book.author || "—"}</td>
                      <td className="px-4 py-4">{book.isbn || "—"}</td>
                      <td className="px-4 py-4">{book.marcData ? <span className="text-xs font-bold text-positive">✅ Completed</span> : <span className="text-xs font-bold text-warning">⏳ Draft</span>}</td>
                      <td className="px-4 py-4 text-right">
                        <button onClick={() => openEditor(book)} className="px-3 py-1 rounded bg-warning/10 border border-warning/30 text-xs font-bold text-warning hover:bg-warning/20 transition-colors">✏️ Edit MARC</button>
                      </td>
                    </tr>
                  ))}
                  {books?.length === 0 && <tr><td colSpan={5} className="text-center py-8 text-muted">No books in catalog.</td></tr>}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
