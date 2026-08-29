"use client";
import React, { useState } from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";

const PO_STATUSES = ["Pending", "Approved", "Ordered", "Shipped", "Received", "Cancelled"];
const STATUS_COLORS: Record<string, string> = {
  Pending: "text-amber-400", Approved: "text-blue-400", Ordered: "text-purple-400",
  Shipped: "text-cyan-400", Received: "text-emerald-400", Cancelled: "text-red-400"
};

export default function AcquisitionsPage() {
  const { data: orders, loading, addRecord, updateRecord, deleteRecord } = useTenantCollection("purchase_orders");
  const [showModal, setShowModal] = useState(false);
  const [vendor, setVendor] = useState("");
  const [bookTitle, setBookTitle] = useState("");
  const [qty, setQty] = useState(1);
  const [unitPrice, setUnitPrice] = useState(500);
  const [adding, setAdding] = useState(false);
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState("All Statuses");

  const allOrders = orders ?? [];
  const totalSpent = allOrders.reduce((s, o) => s + (o.totalAmount || 0), 0);
  const pendingCount = allOrders.filter(o => o.status === "Pending").length;
  const receivedCount = allOrders.filter(o => o.status === "Received").length;

  const filtered = allOrders.filter(o => {
    const matchSearch = !search || (o.vendorName || "").toLowerCase().includes(search.toLowerCase());
    const matchStatus = statusFilter === "All Statuses" || o.status === statusFilter;
    return matchSearch && matchStatus;
  });

  const handleCreate = async () => {
    if (!vendor.trim()) return;
    setAdding(true);
    await addRecord({
      vendorName: vendor.trim(),
      bookTitle: bookTitle.trim(),
      qty,
      unitPrice,
      totalAmount: qty * unitPrice,
      status: "Pending",
      orderDate: Date.now(),
    });
    setVendor(""); setBookTitle(""); setQty(1); setUnitPrice(500);
    setAdding(false);
    setShowModal(false);
  };

  const handleUpdateStatus = async (id: string, currentStatus: string) => {
    const idx = PO_STATUSES.indexOf(currentStatus);
    const nextStatus = PO_STATUSES[Math.min(idx + 1, PO_STATUSES.length - 1)];
    const chosen = window.prompt(`Current: ${currentStatus}\nEnter new status (${PO_STATUSES.join(", ")}):`, nextStatus);
    if (chosen && PO_STATUSES.includes(chosen)) {
      await updateRecord(id, { status: chosen });
    }
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-500 max-w-6xl mx-auto">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold text-[#E8EEF8]">💰 Acquisitions & Budgeting</h1>
          <p className="text-sm text-slate-400">Create purchase orders, track vendor spending and acquisition workflow</p>
        </div>
        <button onClick={() => setShowModal(true)} className="px-5 py-2.5 rounded-lg bg-gradient-to-r from-[#1E5FD4] to-[#2872F0] text-white font-bold text-sm hover:from-[#2872F0] hover:to-[#3D8EFF] shadow transition-all">
          ➕ New Purchase Order
        </button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {[
          { label: "Total Orders", value: allOrders.length, color: "text-blue-400" },
          { label: "Total Spent", value: `Rs. ${totalSpent.toLocaleString()}`, color: "text-amber-400" },
          { label: "Pending", value: pendingCount, color: "text-red-400" },
          { label: "Received", value: receivedCount, color: "text-emerald-400" },
        ].map(s => (
          <div key={s.label} className="rounded-xl border border-blue-950 bg-[#0D1F38] p-4 shadow">
            <p className="text-xs font-bold uppercase tracking-wider text-slate-500">{s.label}</p>
            <p className={`text-2xl font-black mt-1 ${s.color}`}>{s.value}</p>
          </div>
        ))}
      </div>

      {/* Filters */}
      <div className="flex gap-4">
        <input type="text" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="🔍 Search vendors..." className="flex-1 rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]" />
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} className="rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2 text-sm text-[#E8EEF8] outline-none">
          <option>All Statuses</option>
          {PO_STATUSES.map(s => <option key={s}>{s}</option>)}
        </select>
      </div>

      {/* Table */}
      <div className="rounded-xl border border-blue-950 bg-[#070F1E] p-6 shadow-xl overflow-hidden">
        {loading ? <div className="py-12 flex justify-center"><div className="h-8 w-8 animate-spin rounded-full border-4 border-[#C8A84B] border-t-transparent" /></div> : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-slate-300">
              <thead className="text-xs uppercase bg-[#0D1F38]/40 text-slate-400">
                <tr>
                  <th className="px-4 py-3">Vendor</th>
                  <th className="px-4 py-3">Book / Item</th>
                  <th className="px-4 py-3">Qty</th>
                  <th className="px-4 py-3">Amount (Rs.)</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3">Date</th>
                  <th className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-blue-950/40">
                {filtered.map(o => (
                  <tr key={o.id} className="hover:bg-blue-950/10">
                    <td className="px-4 py-4 font-semibold text-white">{o.vendorName}</td>
                    <td className="px-4 py-4">{o.bookTitle || "—"}</td>
                    <td className="px-4 py-4">{o.qty || "—"}</td>
                    <td className="px-4 py-4 text-amber-400 font-bold">{(o.totalAmount || 0).toLocaleString()}</td>
                    <td className="px-4 py-4"><span className={`text-xs font-black ${STATUS_COLORS[o.status] || "text-slate-400"}`}>{o.status}</span></td>
                    <td className="px-4 py-4 text-xs">{o.orderDate ? new Date(o.orderDate).toLocaleDateString() : "—"}</td>
                    <td className="px-4 py-4 text-right space-x-2">
                      <button onClick={() => handleUpdateStatus(o.id, o.status)} className="px-2 py-1 rounded bg-emerald-500/10 border border-emerald-500/30 text-xs font-bold text-emerald-400 hover:bg-emerald-500/20 transition-colors">✅ Update</button>
                      <button onClick={() => deleteRecord(o.id)} className="px-2 py-1 rounded bg-red-500/10 border border-red-500/30 text-xs font-bold text-red-400 hover:bg-red-500/20 transition-colors">🗑️</button>
                    </td>
                  </tr>
                ))}
                {filtered.length === 0 && <tr><td colSpan={7} className="text-center py-8 text-slate-500">No purchase orders found.</td></tr>}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/70 flex items-center justify-center z-50 p-4">
          <div className="rounded-2xl border border-blue-900 bg-[#0A1428] p-8 shadow-2xl w-full max-w-md">
            <h2 className="text-xl font-bold text-white mb-6">📋 New Purchase Order</h2>
            <div className="space-y-4">
              <div><label className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-1">Vendor *</label><input type="text" value={vendor} onChange={(e) => setVendor(e.target.value)} placeholder="e.g. Oxford University Press" className="w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2.5 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]" /></div>
              <div><label className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-1">Book / Item</label><input type="text" value={bookTitle} onChange={(e) => setBookTitle(e.target.value)} placeholder="e.g. Advanced Physics" className="w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2.5 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]" /></div>
              <div className="grid grid-cols-2 gap-4">
                <div><label className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-1">Quantity</label><input type="number" min="1" value={qty} onChange={(e) => setQty(parseInt(e.target.value))} className="w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2.5 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]" /></div>
                <div><label className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-1">Unit Price (Rs.)</label><input type="number" min="1" value={unitPrice} onChange={(e) => setUnitPrice(parseInt(e.target.value))} className="w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2.5 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]" /></div>
              </div>
              <div className="bg-[#0D1F38] rounded-lg p-3 border border-[#1E3050] text-center">
                <p className="text-xs text-slate-400 font-bold">Total Amount</p>
                <p className="text-xl font-black text-amber-400">Rs. {(qty * unitPrice).toLocaleString()}</p>
              </div>
            </div>
            <div className="flex gap-3 mt-6">
              <button onClick={handleCreate} disabled={adding || !vendor.trim()} className="flex-1 py-2.5 rounded-lg bg-blue-600 text-white font-bold text-sm hover:bg-blue-500 disabled:opacity-50 transition-colors">{adding ? "Creating..." : "📋 Create PO"}</button>
              <button onClick={() => setShowModal(false)} className="flex-1 py-2.5 rounded-lg border border-slate-700 text-slate-400 font-bold text-sm hover:bg-slate-800 transition-colors">Cancel</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
