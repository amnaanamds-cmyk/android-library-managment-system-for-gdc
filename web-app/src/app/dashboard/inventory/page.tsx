"use client";
import React, { useState, useRef } from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";
import Papa from "papaparse";

export default function InventoryPage() {
  const { data: books, loading } = useTenantCollection("books");
  const [foundIds, setFoundIds] = useState<Set<string>>(new Set());
  const [scanInput, setScanInput] = useState("");
  const [lastScan, setLastScan] = useState<{text: string; ok: boolean} | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const allBooks = books ?? [];
  const found = foundIds.size;
  const total = allBooks.length;
  const pct = total ? Math.round((found / total) * 100) : 0;

  const processScan = () => {
    const val = scanInput.trim();
    setScanInput("");
    if (!val) return;
    const match = allBooks.find(b => b.accNo === val || b.isbn === val);
    if (!match) {
      setLastScan({ text: `Not found: "${val}"`, ok: false });
      return;
    }
    if (foundIds.has(match.id)) {
      setLastScan({ text: `Already scanned: ${match.title}`, ok: true });
      return;
    }
    setFoundIds(prev => new Set([...prev, match.id]));
    setLastScan({ text: `✅ Found: ${match.title}`, ok: true });
    inputRef.current?.focus();
  };

  const resetSession = () => { setFoundIds(new Set()); setLastScan(null); };

  const exportMissing = () => {
    const missing = allBooks.filter(b => !foundIds.has(b.id));
    if (!missing.length) { alert("No books missing! 100% Inventory match."); return; }
    const csv = Papa.unparse(missing.map(b => ({ AccNo: b.accNo, ISBN: b.isbn, Title: b.title, Author: b.author, Status: b.status })));
    const blob = new Blob([csv], { type: "text/csv" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a"); a.href = url; a.download = "Missing_Books_Report.csv"; a.click();
  };

  const sorted = [...allBooks].sort((a, b) => {
    if (foundIds.has(a.id) && !foundIds.has(b.id)) return 1;
    if (!foundIds.has(a.id) && foundIds.has(b.id)) return -1;
    return (a.title || "").localeCompare(b.title || "");
  });

  return (
    <div className="space-y-6 animate-in fade-in duration-500 max-w-6xl mx-auto">
      <div>
        <h1 className="text-3xl font-extrabold text-ink">📦 Inventory & Stocktaking</h1>
        <p className="text-sm text-muted">Koha-style shelf reading. Scan book barcodes (Acc No or ISBN) to mark as Found.</p>
      </div>

      {/* Scanner Bar */}
      <div className="rounded-xl border border-line bg-surface-2 p-5 shadow-xl">
        <div className="flex gap-3 mb-3">
          <input
            ref={inputRef}
            type="text"
            value={scanInput}
            onChange={(e) => setScanInput(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && processScan()}
            placeholder="🔍 Scan Barcode / Acc No here..."
            className="flex-1 rounded-lg border-2 border-accent bg-surface px-4 py-3 text-base text-ink outline-none focus:border-warning"
            autoFocus
          />
          <button onClick={resetSession} className="px-4 py-2 rounded-lg bg-line text-body font-bold text-sm hover:bg-line transition-colors">🔄 Reset</button>
          <button onClick={exportMissing} className="px-4 py-2 rounded-lg bg-danger/10 border border-danger/30 text-danger font-bold text-sm hover:bg-danger/20 transition-colors">📤 Export Missing</button>
        </div>
        {lastScan && <p className={`text-sm font-bold ${lastScan.ok ? "text-positive" : "text-danger"}`}>{lastScan.text}</p>}
      </div>

      {/* Progress */}
      <div className="rounded-xl border border-line bg-surface-2 p-5 shadow-xl">
        <div className="flex justify-between items-center mb-2">
          <span className="text-sm font-bold text-muted">Progress: {found} / {total} Found ({pct}%)</span>
          <span className={`text-xs font-bold px-3 py-1 rounded-full ${pct === 100 ? "bg-positive-soft/40 text-positive" : "bg-surface-2/40 text-accent"}`}>
            {pct === 100 ? "✅ Complete" : "In Progress"}
          </span>
        </div>
        <div className="h-3 w-full bg-surface rounded-full overflow-hidden border border-line/30">
          <div className="h-full bg-positive rounded-full transition-all duration-500" style={{ width: `${pct}%` }} />
        </div>
      </div>

      {/* Table */}
      <div className="rounded-xl border border-line bg-surface-2 p-6 shadow-xl overflow-hidden">
        {loading ? <div className="py-12 flex justify-center"><div className="h-8 w-8 animate-spin rounded-full border-4 border-accent border-t-transparent" /></div> : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-body">
              <thead className="text-xs uppercase bg-surface/40 text-muted">
                <tr>
                  <th className="px-4 py-3">Acc No</th>
                  <th className="px-4 py-3">Title</th>
                  <th className="px-4 py-3">Author</th>
                  <th className="px-4 py-3">System Status</th>
                  <th className="px-4 py-3">Inventory Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line/40">
                {sorted.map(b => {
                  const isFnd = foundIds.has(b.id);
                  return (
                    <tr key={b.id} className={`hover:bg-surface-2/10 ${isFnd ? "opacity-60" : ""}`}>
                      <td className="px-4 py-3 font-mono text-xs">{b.accNo || "—"}</td>
                      <td className="px-4 py-3 font-semibold text-ink">{b.title}</td>
                      <td className="px-4 py-3">{b.author || "—"}</td>
                      <td className="px-4 py-3"><span className={`text-xs font-bold ${b.status === "Available" ? "text-positive" : "text-warning"}`}>{b.status || "—"}</span></td>
                      <td className="px-4 py-3"><span className={`text-xs font-black ${isFnd ? "text-positive" : "text-danger"}`}>{isFnd ? "✅ Found" : "❌ Missing"}</span></td>
                    </tr>
                  );
                })}
                {sorted.length === 0 && <tr><td colSpan={5} className="text-center py-8 text-muted">No books in catalog to audit.</td></tr>}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
