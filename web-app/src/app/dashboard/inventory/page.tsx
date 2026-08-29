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
        <h1 className="text-3xl font-extrabold text-[#E8EEF8]">📦 Inventory & Stocktaking</h1>
        <p className="text-sm text-slate-400">Koha-style shelf reading. Scan book barcodes (Acc No or ISBN) to mark as Found.</p>
      </div>

      {/* Scanner Bar */}
      <div className="rounded-xl border border-blue-950 bg-[#070F1E] p-5 shadow-xl">
        <div className="flex gap-3 mb-3">
          <input
            ref={inputRef}
            type="text"
            value={scanInput}
            onChange={(e) => setScanInput(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && processScan()}
            placeholder="🔍 Scan Barcode / Acc No here..."
            className="flex-1 rounded-lg border-2 border-[#C8A84B] bg-[#0D1F38] px-4 py-3 text-base text-[#E8EEF8] outline-none focus:border-yellow-400"
            autoFocus
          />
          <button onClick={resetSession} className="px-4 py-2 rounded-lg bg-[#1E3050] text-slate-300 font-bold text-sm hover:bg-[#2A4166] transition-colors">🔄 Reset</button>
          <button onClick={exportMissing} className="px-4 py-2 rounded-lg bg-red-500/10 border border-red-500/30 text-red-400 font-bold text-sm hover:bg-red-500/20 transition-colors">📤 Export Missing</button>
        </div>
        {lastScan && <p className={`text-sm font-bold ${lastScan.ok ? "text-emerald-400" : "text-red-400"}`}>{lastScan.text}</p>}
      </div>

      {/* Progress */}
      <div className="rounded-xl border border-blue-950 bg-[#070F1E] p-5 shadow-xl">
        <div className="flex justify-between items-center mb-2">
          <span className="text-sm font-bold text-slate-400">Progress: {found} / {total} Found ({pct}%)</span>
          <span className={`text-xs font-bold px-3 py-1 rounded-full ${pct === 100 ? "bg-emerald-900/40 text-emerald-400" : "bg-blue-900/40 text-blue-300"}`}>
            {pct === 100 ? "✅ Complete" : "In Progress"}
          </span>
        </div>
        <div className="h-3 w-full bg-[#0D1F38] rounded-full overflow-hidden border border-blue-900/30">
          <div className="h-full bg-emerald-500 rounded-full transition-all duration-500" style={{ width: `${pct}%` }} />
        </div>
      </div>

      {/* Table */}
      <div className="rounded-xl border border-blue-950 bg-[#070F1E] p-6 shadow-xl overflow-hidden">
        {loading ? <div className="py-12 flex justify-center"><div className="h-8 w-8 animate-spin rounded-full border-4 border-[#C8A84B] border-t-transparent" /></div> : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-slate-300">
              <thead className="text-xs uppercase bg-[#0D1F38]/40 text-slate-400">
                <tr>
                  <th className="px-4 py-3">Acc No</th>
                  <th className="px-4 py-3">Title</th>
                  <th className="px-4 py-3">Author</th>
                  <th className="px-4 py-3">System Status</th>
                  <th className="px-4 py-3">Inventory Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-blue-950/40">
                {sorted.map(b => {
                  const isFnd = foundIds.has(b.id);
                  return (
                    <tr key={b.id} className={`hover:bg-blue-950/10 ${isFnd ? "opacity-60" : ""}`}>
                      <td className="px-4 py-3 font-mono text-xs">{b.accNo || "—"}</td>
                      <td className="px-4 py-3 font-semibold text-white">{b.title}</td>
                      <td className="px-4 py-3">{b.author || "—"}</td>
                      <td className="px-4 py-3"><span className={`text-xs font-bold ${b.status === "Available" ? "text-emerald-400" : "text-amber-400"}`}>{b.status || "—"}</span></td>
                      <td className="px-4 py-3"><span className={`text-xs font-black ${isFnd ? "text-emerald-400" : "text-red-400"}`}>{isFnd ? "✅ Found" : "❌ Missing"}</span></td>
                    </tr>
                  );
                })}
                {sorted.length === 0 && <tr><td colSpan={5} className="text-center py-8 text-slate-500">No books in catalog to audit.</td></tr>}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
