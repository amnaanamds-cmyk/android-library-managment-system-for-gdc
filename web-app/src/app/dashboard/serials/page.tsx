"use client";

import React, { useMemo, useState } from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";
import { COLLECTIONS, STATUSES, SERIAL_FREQUENCIES } from "@/lib/schema";

/**
 * Serials — periodicals the library subscribes to.
 *
 * The sidebar previously linked this to "#" and marked it WIP. It reads and
 * writes /institutions/{id}/serials, the same collection the desktop Serials
 * screen and the Android Serials screen use.
 */
export default function SerialsPage() {
  const { data, loading, error, addRecord, updateRecord, deleteRecord } =
    useTenantCollection(COLLECTIONS.serials);

  const [query, setQuery] = useState("");
  const [frequency, setFrequency] = useState("All");
  const [showAdd, setShowAdd] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);

  const serials = useMemo(() => {
    const q = query.trim().toLowerCase();
    return data
      .filter((s) => (frequency === "All" ? true : s.frequency === frequency))
      .filter((s) =>
        !q ||
        String(s.title ?? "").toLowerCase().includes(q) ||
        String(s.issn ?? "").toLowerCase().includes(q) ||
        String(s.publisher ?? "").toLowerCase().includes(q),
      )
      .sort((a, b) => String(a.title ?? "").localeCompare(String(b.title ?? "")));
  }, [data, query, frequency]);

  const active = data.filter((s) => s.status === "Active").length;

  /** Receipting an issue stamps today and increments the count, matching the
   *  desktop "Receive Latest Issue" action and the Android screen. */
  const receiveIssue = async (s: Record<string, unknown>) => {
    setBusy(true);
    try {
      await updateRecord(String(s.id), {
        lastIssueReceived: new Date().toISOString().slice(0, 10),
        issuesReceived: (Number(s.issuesReceived) || 0) + 1,
      });
      setNotice(`Issue receipted for ${s.title}.`);
    } catch (e) {
      setNotice((e as Error).message);
    }
    setBusy(false);
  };

  return (
    <div className="space-y-6 duration-500 animate-in fade-in">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight text-[#E8EEF8]">
            📰 Serials &amp; Periodicals
          </h1>
          <p className="mt-1 text-sm text-slate-400">
            {data.length} subscription{data.length === 1 ? "" : "s"} · {active} active
          </p>
        </div>
        <button
          onClick={() => setShowAdd(true)}
          className="rounded-lg bg-blue-600 px-4 py-2 text-sm font-bold text-white hover:bg-blue-500"
        >
          + New subscription
        </button>
      </div>

      {error && (
        <div className="rounded-lg border border-red-500/30 bg-red-500/10 px-4 py-3 text-xs text-red-300">
          {error}
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
          placeholder="Search title, ISSN or publisher…"
          className="flex-1 rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]"
        />
        <select
          value={frequency}
          onChange={(e) => setFrequency(e.target.value)}
          className="rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2 text-sm text-[#E8EEF8] outline-none"
        >
          <option value="All">All frequencies</option>
          {SERIAL_FREQUENCIES.map((f) => (
            <option key={f} value={f}>{f}</option>
          ))}
        </select>
      </div>

      <div className="overflow-x-auto rounded-xl border border-blue-950 bg-[#070F1E]">
        <table className="w-full text-left text-sm">
          <thead className="bg-[#0D1F38]/50 text-[10px] uppercase tracking-widest text-slate-400">
            <tr>
              <th className="px-4 py-3">Title</th>
              <th className="px-4 py-3">ISSN</th>
              <th className="px-4 py-3">Frequency</th>
              <th className="px-4 py-3">Publisher</th>
              <th className="px-4 py-3 text-center">Issues</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3 text-right">Actions</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-blue-950/40">
            {serials.map((s) => (
              <tr key={String(s.id)} className="hover:bg-blue-950/10">
                <td className="px-4 py-3 font-bold text-white">{String(s.title ?? "—")}</td>
                <td className="px-4 py-3 font-mono text-xs text-slate-400">
                  {String(s.issn ?? "—")}
                </td>
                <td className="px-4 py-3 text-slate-300">{String(s.frequency ?? "—")}</td>
                <td className="px-4 py-3 text-slate-400">{String(s.publisher ?? "—")}</td>
                <td className="px-4 py-3 text-center">
                  <span className="font-bold text-emerald-400">
                    {Number(s.issuesReceived) || 0}
                  </span>
                  {s.lastIssueReceived ? (
                    <p className="text-[10px] text-slate-500">last {String(s.lastIssueReceived)}</p>
                  ) : null}
                </td>
                <td className="px-4 py-3">
                  <select
                    value={String(s.status ?? "Active")}
                    onChange={(e) => updateRecord(String(s.id), { status: e.target.value })}
                    className="rounded bg-[#1E3050] px-2 py-1 text-xs font-bold text-[#E8EEF8] outline-none"
                  >
                    {STATUSES.serial.map((st) => (
                      <option key={st} value={st}>{st}</option>
                    ))}
                  </select>
                </td>
                <td className="px-4 py-3 text-right">
                  <button
                    onClick={() => receiveIssue(s)}
                    disabled={busy}
                    className="rounded border border-emerald-600/40 bg-emerald-600/20 px-3 py-1 text-xs font-bold text-emerald-300 hover:bg-emerald-600/40 disabled:opacity-40"
                  >
                    Receive issue
                  </button>
                  <button
                    onClick={() => {
                      if (confirm(`Remove the subscription to "${s.title}"?`)) {
                        deleteRecord(String(s.id));
                      }
                    }}
                    className="ml-2 rounded border border-red-600/30 bg-red-600/10 px-3 py-1 text-xs font-bold text-red-400 hover:bg-red-600/20"
                  >
                    Remove
                  </button>
                </td>
              </tr>
            ))}
            {!loading && serials.length === 0 && (
              <tr>
                <td colSpan={7} className="py-16 text-center">
                  <div className="flex flex-col items-center gap-2 opacity-40">
                    <span className="text-4xl">📰</span>
                    <p className="text-sm font-bold uppercase tracking-wider text-slate-400">
                      {data.length === 0 ? "No subscriptions yet" : "Nothing matches"}
                    </p>
                  </div>
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {showAdd && (
        <AddSerialDialog
          onClose={() => setShowAdd(false)}
          onSave={async (fields) => {
            await addRecord({ ...fields, issuesReceived: 0, lastIssueReceived: "" });
            setShowAdd(false);
            setNotice("Subscription added.");
          }}
        />
      )}
    </div>
  );
}

function AddSerialDialog({
  onClose,
  onSave,
}: {
  onClose: () => void;
  onSave: (fields: Record<string, unknown>) => Promise<void>;
}) {
  const [title, setTitle] = useState("");
  const [issn, setIssn] = useState("");
  const [frequency, setFrequency] = useState<string>("Monthly");
  const [publisher, setPublisher] = useState("");
  const [subscriptionEnd, setSubscriptionEnd] = useState("");
  const [saving, setSaving] = useState(false);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4">
      <div className="w-full max-w-md space-y-4 rounded-2xl border border-blue-900/50 bg-[#0D1C37] p-6">
        <h2 className="text-lg font-bold text-white">New subscription</h2>
        <Field label="Title" value={title} onChange={setTitle} />
        <Field label="ISSN (optional)" value={issn} onChange={setIssn} />
        <div>
          <label className="block text-xs font-bold uppercase tracking-wider text-[#A0B4CC]">
            Frequency
          </label>
          <select
            value={frequency}
            onChange={(e) => setFrequency(e.target.value)}
            className="mt-2 w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2 text-sm text-[#E8EEF8] outline-none"
          >
            {SERIAL_FREQUENCIES.map((f) => (
              <option key={f} value={f}>{f}</option>
            ))}
          </select>
        </div>
        <Field label="Publisher (optional)" value={publisher} onChange={setPublisher} />
        <Field
          label="Subscription ends (YYYY-MM-DD, optional)"
          value={subscriptionEnd}
          onChange={setSubscriptionEnd}
        />
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
                issn: issn.trim(),
                frequency,
                publisher: publisher.trim(),
                status: "Active",
                subscriptionEnd: subscriptionEnd.trim(),
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

function Field({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
}) {
  return (
    <div>
      <label className="block text-xs font-bold uppercase tracking-wider text-[#A0B4CC]">
        {label}
      </label>
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className="mt-2 w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]"
      />
    </div>
  );
}
