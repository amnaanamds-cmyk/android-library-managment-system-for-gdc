"use client";

import React, { useEffect, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { useTenantCollection } from "@/lib/firestore-hooks";
import { useLibrarySettings, LibrarySettings, computeFine } from "@/lib/settings";
import { COLLECTIONS } from "@/lib/schema";

export default function SettingsPage() {
  const [activeTab, setActiveTab] = useState("general");

  const tabs = [
    { id: "general", label: "Circulation Policy" },
    { id: "auditlogs", label: "Audit Logs" },
    { id: "developertools", label: "Developer Tools" },
  ];

  return (
    <div className="mx-auto max-w-5xl space-y-8 duration-500 animate-in fade-in">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight text-ink">⚙️ System Settings</h1>
        <p className="mt-1 text-sm text-muted">
          Policy set here applies to the Android and Windows apps too.
        </p>
      </div>

      <div className="flex space-x-2 border-b border-line/50 pb-px">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`rounded-t-lg px-4 py-2 text-sm font-bold transition-colors ${
              activeTab === tab.id
                ? "border-b-2 border-accent bg-line text-accent-strong"
                : "text-muted hover:bg-surface-2 hover:text-ink"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="min-h-[400px] rounded-b-xl rounded-tr-xl border border-line bg-app p-6 shadow-xl">
        {activeTab === "general" && <CirculationPolicy />}
        {activeTab === "auditlogs" && <AuditLogs />}
        {activeTab === "developertools" && <DeveloperTools />}
      </div>
    </div>
  );
}

// ─── Circulation policy ──────────────────────────────────────────────────────

function CirculationPolicy() {
  const { settings, loading, error, save } = useLibrarySettings();
  const [draft, setDraft] = useState<LibrarySettings>(settings);
  const [saving, setSaving] = useState(false);
  const [result, setResult] = useState<{ ok: boolean; message: string } | null>(null);

  // Adopt incoming values when the document loads or another device changes it,
  // but never clobber edits the user is in the middle of typing.
  const [dirty, setDirty] = useState(false);
  useEffect(() => {
    if (!dirty) setDraft(settings);
  }, [settings, dirty]);

  const update = <K extends keyof LibrarySettings>(key: K, value: LibrarySettings[K]) => {
    setDirty(true);
    setResult(null);
    setDraft((d) => ({ ...d, [key]: value }));
  };

  const onSave = async () => {
    setSaving(true);
    const r = await save(draft);
    setResult(r);
    setSaving(false);
    if (r.ok) setDirty(false);
  };

  if (loading) {
    return (
      <div className="flex items-center gap-3 py-12 text-sm text-muted">
        <span className="h-4 w-4 animate-spin rounded-full border-2 border-accent border-t-transparent" />
        Loading policy…
      </div>
    );
  }

  // Worked example, so the effect of the numbers is visible before saving.
  const exampleDays = 10;
  const exampleFine = computeFine(draft, exampleDays);

  return (
    <div className="max-w-2xl space-y-8">
      {error && (
        <div className="rounded-lg border border-danger/30 bg-danger/10 px-4 py-3 text-xs text-danger">
          Could not read the current policy: {error}
        </div>
      )}

      <section className="space-y-4">
        <h3 className="text-lg font-bold text-accent">Loans</h3>
        <NumberField
          label="Loan period (days)"
          hint="Used to compute the due date when a book is issued."
          value={draft.borrowDurationDays}
          min={1}
          max={365}
          onChange={(v) => update("borrowDurationDays", v)}
        />
        <NumberField
          label="Maximum books per member"
          value={draft.maxBooksPerMember}
          min={1}
          max={100}
          onChange={(v) => update("maxBooksPerMember", v)}
        />
        <NumberField
          label="Reservation hold (days)"
          hint="How long a fulfilled reservation is held before it expires."
          value={draft.reservationHoldDays}
          min={0}
          max={90}
          onChange={(v) => update("reservationHoldDays", v)}
        />
      </section>

      <section className="space-y-4 border-t border-line/40 pt-6">
        <h3 className="text-lg font-bold text-accent">Overdue fines</h3>
        <NumberField
          label={`Fine per day (${draft.currencySymbol})`}
          value={draft.fineRatePerDay}
          min={0}
          step={0.5}
          onChange={(v) => update("fineRatePerDay", v)}
        />
        <NumberField
          label="Grace period (days)"
          hint="Days after the due date before a fine starts accruing."
          value={draft.fineGraceDays}
          min={0}
          max={90}
          onChange={(v) => update("fineGraceDays", v)}
        />
        <NumberField
          label={`Maximum fine per loan (${draft.currencySymbol})`}
          hint="0 means no cap."
          value={draft.maxFinePerLoan}
          min={0}
          step={0.5}
          onChange={(v) => update("maxFinePerLoan", v)}
        />

        <div className="rounded-lg border border-line/40 bg-surface/50 px-4 py-3 text-xs text-body">
          <span className="font-bold text-accent-strong">Worked example:</span> a book returned{" "}
          {exampleDays} days late is charged{" "}
          <span className="font-bold text-ink">
            {draft.currencySymbol} {exampleFine.toFixed(2)}
          </span>
          {draft.fineGraceDays > 0 && ` (first ${draft.fineGraceDays} day(s) free)`}
          {draft.maxFinePerLoan > 0 &&
            exampleFine >= draft.maxFinePerLoan &&
            " — at the cap"}
          .
        </div>
      </section>

      <div className="flex items-center gap-4 border-t border-line/40 pt-6">
        <button
          onClick={onSave}
          disabled={saving || !dirty}
          className="rounded-lg bg-accent-bg px-6 py-3 font-bold text-on-accent shadow-lg transition-colors hover:bg-accent-bg disabled:opacity-40"
        >
          {saving ? "Saving…" : "💾 Save policy"}
        </button>
        {dirty && !saving && (
          <span className="text-xs font-bold text-warning">Unsaved changes</span>
        )}
        {result && (
          <span
            className={`text-xs font-bold ${result.ok ? "text-positive" : "text-danger"}`}
          >
            {result.message}
          </span>
        )}
      </div>

      {settings.lastUpdated > 0 && (
        <p className="text-[11px] text-muted">
          Last changed {new Date(settings.lastUpdated).toLocaleString("en-PK")}
          {settings.updatedBy && ` by ${settings.updatedBy}`}
          {settings.updatedByPlatform && ` (${settings.updatedByPlatform})`}.
        </p>
      )}
    </div>
  );
}

function NumberField({
  label,
  hint,
  value,
  min,
  max,
  step = 1,
  onChange,
}: {
  label: string;
  hint?: string;
  value: number;
  min?: number;
  max?: number;
  step?: number;
  onChange: (v: number) => void;
}) {
  return (
    <div>
      <label className="block text-sm font-bold text-body">{label}</label>
      {hint && <p className="mt-0.5 text-xs text-muted">{hint}</p>}
      <input
        type="number"
        value={Number.isFinite(value) ? value : 0}
        min={min}
        max={max}
        step={step}
        onChange={(e) => {
          const n = parseFloat(e.target.value);
          onChange(Number.isFinite(n) ? n : 0);
        }}
        className="mt-2 w-full rounded-lg border border-line bg-surface px-4 py-3 text-ink outline-none focus:border-accent"
      />
    </div>
  );
}

// ─── Audit logs ──────────────────────────────────────────────────────────────

function AuditLogs() {
  // Reads the same audit_log collection the desktop and Android apps write to.
  // This panel previously rendered a hardcoded "No Recent Audit Logs Found".
  const { data, loading, error } = useTenantCollection(COLLECTIONS.auditLog);

  const entries = [...data]
    .sort((a, b) => (Number(b.timestamp) || 0) - (Number(a.timestamp) || 0))
    .slice(0, 100);

  return (
    <div className="space-y-4">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-lg font-bold text-ink">System Audit Trail</h3>
        <span className="text-xs text-muted">
          {loading ? "Loading…" : `${entries.length} most recent`}
        </span>
      </div>

      {error && (
        <div className="rounded-lg border border-danger/30 bg-danger/10 px-4 py-3 text-xs text-danger">
          {error}
        </div>
      )}

      <div className="max-h-[26rem] overflow-y-auto rounded-lg border border-line">
        <table className="w-full text-left text-sm">
          <thead className="sticky top-0 bg-surface text-[10px] uppercase tracking-widest text-muted">
            <tr>
              <th className="px-4 py-3">When</th>
              <th className="px-4 py-3">User</th>
              <th className="px-4 py-3">Action</th>
              <th className="px-4 py-3">Detail</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-line/40">
            {entries.map((e, i) => (
              <tr key={e.id || i} className="hover:bg-surface-2/10">
                <td className="whitespace-nowrap px-4 py-3 text-xs text-muted">
                  {e.timestampStr ||
                    (e.timestamp ? new Date(Number(e.timestamp)).toLocaleString("en-PK") : "—")}
                </td>
                <td className="px-4 py-3 text-xs text-body">{e.userEmail || "system"}</td>
                <td className="px-4 py-3">
                  <span className="rounded bg-surface-2 px-2 py-0.5 font-mono text-[11px] text-accent">
                    {e.action || "—"}
                  </span>
                </td>
                <td className="px-4 py-3 text-xs text-muted">{e.detail || ""}</td>
              </tr>
            ))}
            {!loading && entries.length === 0 && (
              <tr>
                <td colSpan={4} className="py-16 text-center">
                  <p className="text-xs font-bold uppercase tracking-widest text-muted">
                    No audit entries yet
                  </p>
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

// ─── Developer tools ─────────────────────────────────────────────────────────

function DeveloperTools() {
  const { profile } = useAuth();
  const books = useTenantCollection(COLLECTIONS.books);
  const members = useTenantCollection(COLLECTIONS.members);
  const issues = useTenantCollection(COLLECTIONS.issuedBooks);

  const [report, setReport] = useState<string[] | null>(null);

  // Integrity check reconciles circulation against the catalogue. These are the
  // inconsistencies that actually cause trouble in day-to-day use.
  const runIntegrityCheck = () => {
    const problems: string[] = [];

    const openLoans = issues.data.filter(
      (i) => String(i.status).toLowerCase() === "issued",
    );
    const bookBySyncId = new Map(books.data.map((b) => [b.syncId ?? b.id, b]));
    const memberIds = new Set(members.data.map((m) => m.id ?? m.syncId));

    for (const loan of openLoans) {
      const book = bookBySyncId.get(loan.bookSyncId ?? loan.bookId);
      if (!book) {
        problems.push(`Open loan "${loan.bookTitle || loan.id}" references a book that no longer exists.`);
      } else if (String(book.status) !== "Issued") {
        problems.push(
          `"${book.title}" is on loan but its catalogue status reads "${book.status}".`,
        );
      }
      if (loan.memberId != null && !memberIds.has(loan.memberId)) {
        problems.push(`Open loan "${loan.bookTitle || loan.id}" references a member who no longer exists.`);
      }
      if (!loan.dueDate) {
        problems.push(`Open loan "${loan.bookTitle || loan.id}" has no due date, so no fine can be calculated.`);
      }
    }

    const issuedBooksWithNoLoan = books.data.filter(
      (b) =>
        String(b.status) === "Issued" &&
        !openLoans.some((l) => (l.bookSyncId ?? l.bookId) === (b.syncId ?? b.id)),
    );
    for (const b of issuedBooksWithNoLoan) {
      problems.push(`"${b.title}" is marked Issued but has no open loan record.`);
    }

    const seen = new Map<string, number>();
    for (const b of books.data) {
      const key = String(b.accNo || "").trim();
      if (!key) continue;
      seen.set(key, (seen.get(key) || 0) + 1);
    }
    for (const [accNo, count] of seen) {
      if (count > 1) problems.push(`Accession number "${accNo}" is used by ${count} books.`);
    }

    setReport(problems);
  };

  const busy = books.loading || members.loading || issues.loading;

  return (
    <div className="max-w-2xl space-y-8">
      <div className="space-y-4 rounded-xl border border-line/50 bg-surface/30 p-6">
        <div>
          <h3 className="text-lg font-bold text-accent">Data integrity check</h3>
          <p className="mt-1 text-sm text-muted">
            Reconciles open loans against the catalogue and member register.
          </p>
        </div>
        <button
          onClick={runIntegrityCheck}
          disabled={busy}
          className="rounded-lg bg-warning px-6 py-3 font-bold text-warning shadow-lg transition-colors hover:bg-warning disabled:opacity-40"
        >
          {busy ? "Loading data…" : "🩺 Run integrity check"}
        </button>

        {report && (
          <div
            className={`rounded-lg border px-4 py-3 text-xs ${
              report.length === 0
                ? "border-positive/30 bg-positive/10 text-positive"
                : "border-warning/30 bg-warning/10 text-warning"
            }`}
          >
            {report.length === 0 ? (
              <p className="font-bold">No inconsistencies found.</p>
            ) : (
              <>
                <p className="mb-2 font-bold">
                  {report.length} issue{report.length === 1 ? "" : "s"} found:
                </p>
                <ul className="max-h-56 list-inside list-disc space-y-1 overflow-y-auto">
                  {report.map((p, i) => (
                    <li key={i}>{p}</li>
                  ))}
                </ul>
              </>
            )}
          </div>
        )}
      </div>

      <div className="space-y-3 rounded-xl border border-line/50 bg-surface/30 p-6">
        <h3 className="text-lg font-bold text-accent">Session</h3>
        <dl className="grid grid-cols-2 gap-3 text-xs">
          <div>
            <dt className="uppercase tracking-widest text-muted">Institution</dt>
            <dd className="font-mono text-body">{profile?.institutionId || "—"}</dd>
          </div>
          <div>
            <dt className="uppercase tracking-widest text-muted">Role</dt>
            <dd className="font-mono text-body">{profile?.role || "—"}</dd>
          </div>
          <div>
            <dt className="uppercase tracking-widest text-muted">Books</dt>
            <dd className="text-body">{books.data.length}</dd>
          </div>
          <div>
            <dt className="uppercase tracking-widest text-muted">Members</dt>
            <dd className="text-body">{members.data.length}</dd>
          </div>
        </dl>
      </div>

      {/*
        The previous build had a "Reset Database" button whose only action was a
        confirm() dialog — it deleted nothing regardless of the answer. Rather
        than wire a one-click irreversible wipe of an entire college's catalogue
        into a web page, the destructive path stays with the desktop app, which
        takes a local backup first.
      */}
      <div className="space-y-2 rounded-xl border border-danger/50 bg-danger-soft/20 p-6">
        <h3 className="text-lg font-bold text-danger">⚠️ Destructive actions</h3>
        <p className="text-sm text-muted">
          Resetting a library&apos;s data is done from the Windows app, under Settings → Reset
          Database, which takes a local backup before clearing anything. It is deliberately not
          offered here: this page has no backup step, and the action cannot be undone.
        </p>
      </div>
    </div>
  );
}
