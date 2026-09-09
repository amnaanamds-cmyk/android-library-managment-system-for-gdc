"use client";
import React, { useState } from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";

const PO_STATUSES = ["Pending", "Approved", "Ordered", "Shipped", "Received", "Cancelled"];
const STATUS_COLORS: Record<string, string> = {
  Pending: "text-warning", Approved: "text-accent", Ordered: "text-accent",
  Shipped: "text-accent", Received: "text-positive", Cancelled: "text-danger"
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
          <h1 className="text-3xl font-extrabold text-ink">💰 Acquisitions & Budgeting</h1>
          <p className="text-sm text-muted">Create purchase orders, track vendor spending and acquisition workflow</p>
        </div>
        <button onClick={() => setShowModal(true)} className="bg-accent-bg px-5 py-2.5 rounded-lg text-on-accent font-bold text-sm shadow transition-all">
          ➕ New Purchase Order
        </button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        {[
          { label: "Total Orders", value: allOrders.length, color: "text-accent" },
          { label: "Total Spent", value: `Rs. ${totalSpent.toLocaleString()}`, color: "text-warning" },
          { label: "Pending", value: pendingCount, color: "text-danger" },
          { label: "Received", value: receivedCount, color: "text-positive" },
        ].map(s => (
          <div key={s.label} className="rounded-xl border border-line bg-surface p-4 shadow">
            <p className="text-xs font-bold uppercase tracking-wider text-muted">{s.label}</p>
            <p className={`text-2xl font-black mt-1 ${s.color}`}>{s.value}</p>
          </div>
        ))}
      </div>

      {/* Filters */}
      <div className="flex gap-4">
        <input type="text" value={search} onChange={(e) => setSearch(e.target.value)} placeholder="🔍 Search vendors..." className="flex-1 rounded-lg border border-line bg-surface px-4 py-2 text-sm text-ink outline-none focus:border-accent" />
        <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} className="rounded-lg border border-line bg-surface px-4 py-2 text-sm text-ink outline-none">
          <option>All Statuses</option>
          {PO_STATUSES.map(s => <option key={s}>{s}</option>)}
        </select>
      </div>

      {/* Table */}
      <div className="rounded-xl border border-line bg-surface-2 p-6 shadow-xl overflow-hidden">
        {loading ? <div className="py-12 flex justify-center"><div className="h-8 w-8 animate-spin rounded-full border-4 border-accent border-t-transparent" /></div> : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-body">
              <thead className="text-xs uppercase bg-surface/40 text-muted">
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
              <tbody className="divide-y divide-line/40">
                {filtered.map(o => (
                  <tr key={o.id} className="hover:bg-surface-2/10">
                    <td className="px-4 py-4 font-semibold text-ink">{o.vendorName}</td>
                    <td className="px-4 py-4">{o.bookTitle || "—"}</td>
                    <td className="px-4 py-4">{o.qty || "—"}</td>
                    <td className="px-4 py-4 text-warning font-bold">{(o.totalAmount || 0).toLocaleString()}</td>
                    <td className="px-4 py-4"><span className={`text-xs font-black ${STATUS_COLORS[o.status] || "text-muted"}`}>{o.status}</span></td>
                    <td className="px-4 py-4 text-xs">{o.orderDate ? new Date(o.orderDate).toLocaleDateString() : "—"}</td>
                    <td className="px-4 py-4 text-right space-x-2">
                      <button onClick={() => handleUpdateStatus(o.id, o.status)} className="px-2 py-1 rounded bg-positive/10 border border-positive/30 text-xs font-bold text-positive hover:bg-positive/20 transition-colors">✅ Update</button>
                      <button onClick={() => deleteRecord(o.id)} className="px-2 py-1 rounded bg-danger/10 border border-danger/30 text-xs font-bold text-danger hover:bg-danger/20 transition-colors">🗑️</button>
                    </td>
                  </tr>
                ))}
                {filtered.length === 0 && <tr><td colSpan={7} className="text-center py-8 text-muted">No purchase orders found.</td></tr>}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Modal */}
      {showModal && (
        <div className="fixed inset-0 bg-black/70 flex items-center justify-center z-50 p-4">
          <div className="rounded-2xl border border-line bg-app p-8 shadow-2xl w-full max-w-md">
            <h2 className="text-xl font-bold text-ink mb-6">📋 New Purchase Order</h2>
            <div className="space-y-4">
              <div><label className="block text-xs font-bold uppercase tracking-wider text-muted mb-1">Vendor *</label><input type="text" value={vendor} onChange={(e) => setVendor(e.target.value)} placeholder="e.g. Oxford University Press" className="w-full rounded-lg border border-line bg-surface px-4 py-2.5 text-sm text-ink outline-none focus:border-accent" /></div>
              <div><label className="block text-xs font-bold uppercase tracking-wider text-muted mb-1">Book / Item</label><input type="text" value={bookTitle} onChange={(e) => setBookTitle(e.target.value)} placeholder="e.g. Advanced Physics" className="w-full rounded-lg border border-line bg-surface px-4 py-2.5 text-sm text-ink outline-none focus:border-accent" /></div>
              <div className="grid grid-cols-2 gap-4">
                <div><label className="block text-xs font-bold uppercase tracking-wider text-muted mb-1">Quantity</label><input type="number" min="1" value={qty} onChange={(e) => setQty(parseInt(e.target.value))} className="w-full rounded-lg border border-line bg-surface px-4 py-2.5 text-sm text-ink outline-none focus:border-accent" /></div>
                <div><label className="block text-xs font-bold uppercase tracking-wider text-muted mb-1">Unit Price (Rs.)</label><input type="number" min="1" value={unitPrice} onChange={(e) => setUnitPrice(parseInt(e.target.value))} className="w-full rounded-lg border border-line bg-surface px-4 py-2.5 text-sm text-ink outline-none focus:border-accent" /></div>
              </div>
              <div className="bg-surface rounded-lg p-3 border border-line text-center">
                <p className="text-xs text-muted font-bold">Total Amount</p>
                <p className="text-xl font-black text-warning">Rs. {(qty * unitPrice).toLocaleString()}</p>
              </div>
            </div>
            <div className="flex gap-3 mt-6">
              <button onClick={handleCreate} disabled={adding || !vendor.trim()} className="flex-1 py-2.5 rounded-lg bg-accent-bg text-on-accent font-bold text-sm hover:bg-accent-bg disabled:opacity-50 transition-colors">{adding ? "Creating..." : "📋 Create PO"}</button>
              <button onClick={() => setShowModal(false)} className="flex-1 py-2.5 rounded-lg border border-line text-muted font-bold text-sm hover:bg-surface-2 transition-colors">Cancel</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
