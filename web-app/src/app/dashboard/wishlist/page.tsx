"use client";

import React, { useMemo, useState } from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";
import { COLLECTIONS, STATUSES } from "@/lib/schema";

/**
 * Purchase wishlist — titles members have asked the library to acquire.
 *
 * Reads and writes /institutions/{id}/wishlist, matching the Android Wishlist
 * screen and the desktop Wishlist screen. Approving a title hands it to
 * Acquisitions as a purchase order, which is the same flow the desktop uses.
 */
export default function WishlistPage() {
  const wishlist = useTenantCollection(COLLECTIONS.wishlist);
  const purchaseOrders = useTenantCollection(COLLECTIONS.purchaseOrders);

  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("All");
  const [showAdd, setShowAdd] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const items = useMemo(() => {
    const q = query.trim().toLowerCase();
    return wishlist.data
      .filter((w) => (statusFilter === "All" ? true : w.status === statusFilter))
      .filter((w) =>
        !q ||
        String(w.title ?? "").toLowerCase().includes(q) ||
        String(w.author ?? "").toLowerCase().includes(q),
      )
      // Most-requested first: the vote count is what tells an acquisitions
      // librarian which titles are actually in demand.
      .sort((a, b) => (Number(b.votes) || 0) - (Number(a.votes) || 0));
  }, [wishlist.data, query, statusFilter]);

  const pending = wishlist.data.filter((w) => w.status === "Requested").length;

  /**
   * Add a request, upvoting an existing entry for the same title rather than
   * creating a duplicate — this mirrors OperationsService.add_wishlist_item on
   * the desktop, so the two cannot diverge on what counts as "the same title".
   */
  const addOrUpvote = async (fields: Record<string, unknown>) => {
    const wanted = String(fields.title ?? "").trim().toLowerCase();
    const existing = wishlist.data.find(
      (w) => String(w.title ?? "").trim().toLowerCase() === wanted,
    );
    if (existing) {
      await wishlist.updateRecord(String(existing.id), {
        votes: (Number(existing.votes) || 1) + 1,
      });
      setNotice(`Already requested — upvoted "${existing.title}".`);
      return;
    }
    await wishlist.addRecord({
      ...fields,
      status: "Requested",
      votes: 1,
      requestedAt: Date.now(),
    });
    setNotice("Request added.");
  };

  const approveAndOrder = async (item: Record<string, unknown>) => {
    const vendor = prompt(`Vendor for "${item.title}"?`);
    if (!vendor?.trim()) return;
    const qty = Number(prompt("Quantity?", "1")) || 1;
    const unitPrice = Number(prompt("Unit price?", "0")) || 0;

    await purchaseOrders.addRecord({
      vendorName: vendor.trim(),
      bookTitle: item.title,
      isbn: item.isbn ?? "",
      quantity: qty,
      unitPrice,
      totalAmount: qty * unitPrice,
      status: "Pending",
      orderDate: Date.now(),
      notes: `Raised from wishlist request (${Number(item.votes) || 1} votes).`,
    });
    await wishlist.updateRecord(String(item.id), { status: "Ordered" });
    setNotice(`Purchase order raised for "${item.title}".`);
  };

  return (
    <div className="space-y-6 duration-500 animate-in fade-in">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight text-[#E8EEF8]">
            ⭐ Purchase Wishlist
          </h1>
          <p className="mt-1 text-sm text-slate-400">
            {wishlist.data.length} requested title{wishlist.data.length === 1 ? "" : "s"} ·{" "}
            {pending} awaiting review
          </p>
        </div>
        <button
          onClick={() => setShowAdd(true)}
          className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-bold text-white hover:bg-blue-500"
        >
          + Add request
        </button>
      </div>

      {wishlist.error && (
        <div className="rounded-lg border border-red-500/30 bg-red-500/10 px-4 py-3 text-xs text-red-300">
          {wishlist.error}
        </div>
      )}
      {notice && (
        <div className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-4 py-3 text-xs text-emerald-300">
          {notice}
        </div>
      )}

      <div className="flex flex-wrap gap-3">
        <input
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search title or author…"
          className="flex-1 rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]"
        />
        <select
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value)}
          className="rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2 text-sm text-[#E8EEF8] outline-none"
        >
          <option value="All">All statuses</option>
          {STATUSES.wishlist.map((s) => (
            <option key={s} value={s}>{s}</option>
          ))}
        </select>
      </div>

      <div className="overflow-x-auto rounded-xl border border-blue-950 bg-[#070F1E]">
        <table className="w-full text-left text-sm">
          <thead className="bg-[#0D1F38]/50 text-[10px] uppercase tracking-widest text-slate-400">
            <tr>
              <th className="px-4 py-3">Title</th>
              <th className="px-4 py-3">Author</th>
              <th className="px-4 py-3">Requested by</th>
              <th className="px-4 py-3 text-center">Votes</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-blue-950/40">
            {items.map((w) => (
              <tr key={String(w.id)} className="hover:bg-blue-950/10">
                <td className="px-4 py-3 font-bold text-white">{String(w.title ?? "—")}</td>
                <td className="px-4 py-3 text-slate-400">{String(w.author ?? "—")}</td>
                <td className="px-4 py-3 text-slate-400">
                  {String(w.requestedByName ?? "—")}
                </td>
                <td className="px-4 py-3 text-center">
                  <button
                    onClick={() =>
                      wishlist.updateRecord(String(w.id), {
                        votes: (Number(w.votes) || 1) + 1,
                      })
                    }
                    title="Another member asked for this title"
                    className="rounded bg-[#C8A84B]/15 px-3 py-1 font-bold text-[#E6C96E] hover:bg-[#C8A84B]/30"
                  >
                    ▲ {Number(w.votes) || 1}
                  </button>
                </td>
                <td className="px-4 py-3">
                  <select
                    value={String(w.status ?? "Requested")}
                    onChange={(e) =>
                      wishlist.updateRecord(String(w.id), { status: e.target.value })
                    }
                    className="rounded bg-[#1E3050] px-2 py-1 text-xs font-bold text-[#E8EEF8] outline-none"
                  >
                    {STATUSES.wishlist.map((s) => (
                      <option key={s} value={s}>{s}</option>
                    ))}
                  </select>
                </td>
                <td className="px-4 py-3 text-right">
                  <button
                    onClick={() => approveAndOrder(w)}
                    className="rounded border border-emerald-600/40 bg-emerald-600/20 px-3 py-1 text-xs font-bold text-emerald-300 hover:bg-emerald-600/40"
                  >
                    Raise PO
                  </button>
                  <button
                    onClick={() => {
                      if (confirm(`Remove "${w.title}" from the wishlist?`)) {
                        wishlist.deleteRecord(String(w.id));
                      }
                    }}
                    className="ml-2 rounded border border-red-600/30 bg-red-600/10 px-3 py-1 text-xs font-bold text-red-400 hover:bg-red-600/20"
                  >
                    Remove
                  </button>
                </td>
              </tr>
            ))}
            {!wishlist.loading && items.length === 0 && (
              <tr>
                <td colSpan={6} className="py-16 text-center">
                  <div className="flex flex-col items-center gap-2 opacity-40">
                    <span className="text-4xl">⭐</span>
                    <p className="text-sm font-bold uppercase tracking-wider text-slate-400">
                      {wishlist.data.length === 0 ? "No requests yet" : "Nothing matches"}
                    </p>
                  </div>
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {showAdd && (
        <AddWishlistDialog
          onClose={() => setShowAdd(false)}
          onSave={async (fields) => {
            await addOrUpvote(fields);
            setShowAdd(false);
          }}
        />
      )}
    </div>
  );
}

function AddWishlistDialog({
  onClose,
  onSave,
}: {
  onClose: () => void;
  onSave: (fields: Record<string, unknown>) => Promise<void>;
}) {
  const [title, setTitle] = useState("");
  const [author, setAuthor] = useState("");
  const [isbn, setIsbn] = useState("");
  const [requestedByName, setRequestedByName] = useState("");
  const [reason, setReason] = useState("");
  const [saving, setSaving] = useState(false);

  const field = (label: string, value: string, set: (v: string) => void) => (
    <div>
      <label className="block text-xs font-bold uppercase tracking-wider text-[#A0B4CC]">
        {label}
      </label>
      <input
        value={value}
        onChange={(e) => set(e.target.value)}
        className="mt-2 w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]"
      />
    </div>
  );

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
      <div className="w-full max-w-md space-y-4 rounded-2xl border border-blue-900/50 bg-[#0D1C37] p-6">
        <h2 className="text-lg font-bold text-white">Add a request</h2>
        {field("Title", title, setTitle)}
        {field("Author (optional)", author, setAuthor)}
        {field("ISBN (optional)", isbn, setIsbn)}
        {field("Requested by (optional)", requestedByName, setRequestedByName)}
        {field("Reason (optional)", reason, setReason)}
        <div className="flex justify-end gap-3 pt-2">
          <button onClick={onClose} className="px-4 py-2 text-sm text-slate-400 hover:text-white">
            Cancel
          </button>
          <button
            disabled={!title.trim() || saving}
            onClick={async () => {
              setSaving(true);
              await onSave({
                title: title.trim(),
                author: author.trim(),
                isbn: isbn.trim(),
                requestedByName: requestedByName.trim(),
                requestedByMemberId: "",
                reason: reason.trim(),
              });
              setSaving(false);
            }}
            className="rounded-lg bg-blue-600 px-5 py-2 text-sm font-bold text-white hover:bg-blue-500 disabled:opacity-40"
          >
            {saving ? "Saving…" : "Add"}
          </button>
        </div>
      </div>
    </div>
  );
}
