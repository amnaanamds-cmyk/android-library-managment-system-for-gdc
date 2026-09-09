"use client";
import React, { useState } from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";

const STATUS_COLORS: Record<string, string> = {
  requested: "text-warning", "in-transit": "text-accent",
  received: "text-positive", rejected: "text-danger"
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
      <table className="w-full text-left text-sm text-body">
        <thead className="text-xs uppercase bg-surface/40 text-muted">
          <tr>
            <th className="px-4 py-3">Book Title</th>
            <th className="px-4 py-3">ISBN</th>
            <th className="px-4 py-3">{isIncoming ? "From College" : "To College"}</th>
            <th className="px-4 py-3">Status</th>
            <th className="px-4 py-3">Requested</th>
            {isIncoming && <th className="px-4 py-3 text-right">Action</th>}
          </tr>
        </thead>
        <tbody className="divide-y divide-line/40">
          {rows.map(t => (
            <tr key={t.id} className="hover:bg-surface-2/10">
              <td className="px-4 py-4 font-semibold text-ink">{t.bookTitle}</td>
              <td className="px-4 py-4">{t.bookIsbn || "—"}</td>
              <td className="px-4 py-4">{isIncoming ? (t.fromCollege || "—") : (t.toCollege || "—")}</td>
              <td className="px-4 py-4"><span className={`text-xs font-black uppercase ${STATUS_COLORS[t.status] || "text-muted"}`}>{t.status}</span></td>
              <td className="px-4 py-4 text-xs">{t.requestedAt ? new Date(t.requestedAt).toLocaleDateString() : "—"}</td>
              {isIncoming && (
                <td className="px-4 py-4 text-right space-x-2">
                  {t.status === "requested" && (
                    <>
                      <button onClick={() => handleUpdateStatus(t.id, "in-transit")} className="px-2 py-1 rounded bg-positive/10 border border-positive/30 text-xs font-bold text-positive hover:bg-positive/20 transition-colors">✅ Accept</button>
                      <button onClick={() => handleUpdateStatus(t.id, "rejected")} className="px-2 py-1 rounded bg-danger/10 border border-danger/30 text-xs font-bold text-danger hover:bg-danger/20 transition-colors">❌ Reject</button>
                    </>
                  )}
                  {t.status === "in-transit" && (
                    <button onClick={() => handleUpdateStatus(t.id, "received")} className="px-2 py-1 rounded bg-accent-bg/10 border border-accent/30 text-xs font-bold text-accent hover:bg-accent-bg/20 transition-colors">📦 Mark Received</button>
                  )}
                </td>
              )}
            </tr>
          ))}
          {rows.length === 0 && <tr><td colSpan={isIncoming ? 6 : 5} className="text-center py-8 text-muted">No transfers found.</td></tr>}
        </tbody>
      </table>
    </div>
  );

  return (
    <div className="space-y-6 animate-in fade-in duration-500 max-w-6xl mx-auto">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold text-ink">🔄 Inter-Library Book Transfers</h1>
          <p className="text-sm text-muted">Request books from other colleges via the Directorate network and track transfer status</p>
        </div>
        <button onClick={() => setShowModal(true)} className="px-5 py-2.5 rounded-lg bg-positive text-on-accent font-bold text-sm hover:bg-positive transition-colors shadow">
          + Request Book
        </button>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 bg-surface rounded-xl p-1 w-fit">
        {(["outgoing", "incoming"] as const).map(t => (
          <button key={t} onClick={() => setTab(t)} className={`px-5 py-2 rounded-lg text-sm font-bold transition-all ${tab === t ? "bg-accent-bg text-on-accent shadow" : "text-muted hover:text-on-accent"}`}>
            {t === "outgoing" ? "📤 Outgoing Requests" : "📥 Incoming Requests"}
          </button>
        ))}
      </div>

      <div className="rounded-xl border border-line bg-surface-2 p-6 shadow-xl overflow-hidden">
        {loading ? <div className="py-12 flex justify-center"><div className="h-8 w-8 animate-spin rounded-full border-4 border-accent border-t-transparent" /></div> : (
          tab === "outgoing" ? renderTable(outgoing, false) : renderTable(incoming, true)
        )}
      </div>

      {/* Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/70 flex items-center justify-center z-50 p-4">
          <div className="rounded-2xl border border-line bg-app p-8 shadow-2xl w-full max-w-md">
            <h2 className="text-xl font-bold text-ink mb-6">📦 Request Book Transfer</h2>
            <div className="space-y-4">
              <div><label className="block text-xs font-bold uppercase tracking-wider text-muted mb-1">Book Title *</label><input type="text" value={bookTitle} onChange={(e) => setBookTitle(e.target.value)} placeholder="Enter book title to request" className="w-full rounded-lg border border-line bg-surface px-4 py-2.5 text-sm text-ink outline-none focus:border-accent" /></div>
              <div><label className="block text-xs font-bold uppercase tracking-wider text-muted mb-1">ISBN (optional)</label><input type="text" value={bookIsbn} onChange={(e) => setBookIsbn(e.target.value)} placeholder="ISBN" className="w-full rounded-lg border border-line bg-surface px-4 py-2.5 text-sm text-ink outline-none focus:border-accent" /></div>
              <div><label className="block text-xs font-bold uppercase tracking-wider text-muted mb-1">Request from College *</label><input type="text" value={toCollege} onChange={(e) => setToCollege(e.target.value)} placeholder="e.g. gdc-peshawar" className="w-full rounded-lg border border-line bg-surface px-4 py-2.5 text-sm text-ink outline-none focus:border-accent" /></div>
            </div>
            <div className="flex gap-3 mt-6">
              <button onClick={handleRequest} disabled={adding || !bookTitle.trim() || !toCollege.trim()} className="flex-1 py-2.5 rounded-lg bg-positive text-on-accent font-bold text-sm hover:bg-positive disabled:opacity-50 transition-colors">{adding ? "Sending..." : "📦 Send Request"}</button>
              <button onClick={() => setShowModal(false)} className="flex-1 py-2.5 rounded-lg border border-line text-muted font-bold text-sm hover:bg-surface-2 transition-colors">Cancel</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
