"use client";
import React, { useState } from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";

const STATUS_COLORS: Record<string, string> = {
  requested: "text-amber-400", "in-transit": "text-blue-400",
  received: "text-emerald-400", rejected: "text-red-400"
};

export default function BookTransfersPage() {
  const { data: transfers, loading, addRecord, updateRecord } = useTenantCollection("book_transfers");
  const [tab, setTab] = useState<"outgoing" | "incoming">("outgoing");
  const [showModal, setShowModal] = useState(false);
  const [bookTitle, setBookTitle] = useState("");
  const [bookIsbn, setBookIsbn] = useState("");
  const [toCollege, setToCollege] = useState("");
  const [adding, setAdding] = useState(false);

  // Simulate this college's ID
  const myCollegeId = "gdc11";

  const outgoing = (transfers ?? []).filter(t => t.fromCollege === myCollegeId);
  const incoming = (transfers ?? []).filter(t => t.toCollege === myCollegeId);

  const handleRequest = async () => {
    if (!bookTitle.trim() || !toCollege.trim()) return;
    setAdding(true);
    await addRecord({
      fromCollege: myCollegeId,
      toCollege: toCollege.trim(),
      bookTitle: bookTitle.trim(),
      bookIsbn: bookIsbn.trim() || null,
      status: "requested",
      requestedAt: Date.now(),
    });
    setBookTitle(""); setBookIsbn(""); setToCollege("");
    setAdding(false);
    setShowModal(false);
  };

  const handleUpdateStatus = async (id: string, status: string) => {
    await updateRecord(id, { status });
  };

  const renderTable = (rows: any[], isIncoming: boolean) => (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm text-slate-300">
        <thead className="text-xs uppercase bg-[#0D1F38]/40 text-slate-400">
          <tr>
            <th className="px-4 py-3">Book Title</th>
            <th className="px-4 py-3">ISBN</th>
            <th className="px-4 py-3">{isIncoming ? "From College" : "To College"}</th>
            <th className="px-4 py-3">Status</th>
            <th className="px-4 py-3">Requested</th>
            {isIncoming && <th className="px-4 py-3 text-right">Action</th>}
          </tr>
        </thead>
        <tbody className="divide-y divide-blue-950/40">
          {rows.map(t => (
            <tr key={t.id} className="hover:bg-blue-950/10">
              <td className="px-4 py-4 font-semibold text-white">{t.bookTitle}</td>
              <td className="px-4 py-4">{t.bookIsbn || "—"}</td>
              <td className="px-4 py-4">{isIncoming ? (t.fromCollege || "—") : (t.toCollege || "—")}</td>
              <td className="px-4 py-4"><span className={`text-xs font-black uppercase ${STATUS_COLORS[t.status] || "text-slate-400"}`}>{t.status}</span></td>
              <td className="px-4 py-4 text-xs">{t.requestedAt ? new Date(t.requestedAt).toLocaleDateString() : "—"}</td>
              {isIncoming && (
                <td className="px-4 py-4 text-right space-x-2">
                  {t.status === "requested" && (
                    <>
                      <button onClick={() => handleUpdateStatus(t.id, "in-transit")} className="px-2 py-1 rounded bg-emerald-500/10 border border-emerald-500/30 text-xs font-bold text-emerald-400 hover:bg-emerald-500/20 transition-colors">✅ Accept</button>
                      <button onClick={() => handleUpdateStatus(t.id, "rejected")} className="px-2 py-1 rounded bg-red-500/10 border border-red-500/30 text-xs font-bold text-red-400 hover:bg-red-500/20 transition-colors">❌ Reject</button>
                    </>
                  )}
                  {t.status === "in-transit" && (
                    <button onClick={() => handleUpdateStatus(t.id, "received")} className="px-2 py-1 rounded bg-blue-500/10 border border-blue-500/30 text-xs font-bold text-blue-400 hover:bg-blue-500/20 transition-colors">📦 Mark Received</button>
                  )}
                </td>
              )}
            </tr>
          ))}
          {rows.length === 0 && <tr><td colSpan={isIncoming ? 6 : 5} className="text-center py-8 text-slate-500">No transfers found.</td></tr>}
        </tbody>
      </table>
    </div>
  );

  return (
    <div className="space-y-6 animate-in fade-in duration-500 max-w-6xl mx-auto">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold text-[#E8EEF8]">🔄 Inter-Library Book Transfers</h1>
          <p className="text-sm text-slate-400">Request books from other colleges via the Directorate network and track transfer status</p>
        </div>
        <button onClick={() => setShowModal(true)} className="px-5 py-2.5 rounded-lg bg-emerald-600 text-white font-bold text-sm hover:bg-emerald-500 transition-colors shadow">
          + Request Book
        </button>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-[#0D1F38] rounded-xl p-1 w-fit">
        {(["outgoing", "incoming"] as const).map(t => (
          <button key={t} onClick={() => setTab(t)} className={`px-5 py-2 rounded-lg text-sm font-bold transition-all ${tab === t ? "bg-blue-600 text-white shadow" : "text-slate-400 hover:text-white"}`}>
            {t === "outgoing" ? "📤 Outgoing Requests" : "📥 Incoming Requests"}
          </button>
        ))}
      </div>

      <div className="rounded-xl border border-blue-950 bg-[#070F1E] p-6 shadow-xl overflow-hidden">
        {loading ? <div className="py-12 flex justify-center"><div className="h-8 w-8 animate-spin rounded-full border-4 border-[#C8A84B] border-t-transparent" /></div> : (
          tab === "outgoing" ? renderTable(outgoing, false) : renderTable(incoming, true)
        )}
      </div>

      {/* Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/70 flex items-center justify-center z-50 p-4">
          <div className="rounded-2xl border border-blue-900 bg-[#0A1428] p-8 shadow-2xl w-full max-w-md">
            <h2 className="text-xl font-bold text-white mb-6">📦 Request Book Transfer</h2>
            <div className="space-y-4">
              <div><label className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-1">Book Title *</label><input type="text" value={bookTitle} onChange={(e) => setBookTitle(e.target.value)} placeholder="Enter book title to request" className="w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2.5 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]" /></div>
              <div><label className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-1">ISBN (optional)</label><input type="text" value={bookIsbn} onChange={(e) => setBookIsbn(e.target.value)} placeholder="ISBN" className="w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2.5 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]" /></div>
              <div><label className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-1">Request from College *</label><input type="text" value={toCollege} onChange={(e) => setToCollege(e.target.value)} placeholder="e.g. gdc-peshawar" className="w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2.5 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]" /></div>
            </div>
            <div className="flex gap-3 mt-6">
              <button onClick={handleRequest} disabled={adding || !bookTitle.trim() || !toCollege.trim()} className="flex-1 py-2.5 rounded-lg bg-emerald-600 text-white font-bold text-sm hover:bg-emerald-500 disabled:opacity-50 transition-colors">{adding ? "Sending..." : "📦 Send Request"}</button>
              <button onClick={() => setShowModal(false)} className="flex-1 py-2.5 rounded-lg border border-slate-700 text-slate-400 font-bold text-sm hover:bg-slate-800 transition-colors">Cancel</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
