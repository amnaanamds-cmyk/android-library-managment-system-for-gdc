"use client";

/**
 * Institution onboarding form (spec section 3).
 *
 * The internal form that drives the registerInstitution callable. Creating a
 * college by hand means touching four places consistently — auth account,
 * claims, institution document, registry entry — and missing any one leaves a
 * college that half-exists. This is the single path that does all four.
 *
 * Deliberately inside /director, behind the directorate access gate, rather
 * than public: registration creates a Firebase Auth account, and an
 * unauthenticated form that does that is an open account factory.
 */

import React, { useState } from "react";
import Link from "next/link";
import { httpsCallable } from "firebase/functions";
import { functions } from "@/lib/firebase";
import { useInstitutionRegistry } from "@/lib/directorate";

/** The 25 districts of Khyber Pakhtunkhwa, for consistent district rollups. */
const KPK_DISTRICTS = [
  "Abbottabad", "Bajaur", "Bannu", "Battagram", "Buner", "Charsadda", "Chitral",
  "Dera Ismail Khan", "Hangu", "Haripur", "Karak", "Khyber", "Kohat", "Kohistan",
  "Kurram", "Lakki Marwat", "Lower Dir", "Malakand", "Mansehra", "Mardan",
  "Nowshera", "Orakzai", "Peshawar", "Shangla", "Swabi", "Swat", "Tank",
  "Torghar", "Upper Dir", "North Waziristan", "South Waziristan",
];

interface RegisterResult {
  institutionId: string;
  name: string;
  district: string;
  status: string;
  inviteCode: string;
  adminEmail: string;
  createdAccount: boolean;
  temporaryPassword: string | null;
}

export default function RegisterInstitution() {
  const { entries } = useInstitutionRegistry();

  const [form, setForm] = useState({
    collegeName: "",
    district: "",
    region: "",
    address: "",
    phone: "",
    adminName: "",
    adminEmail: "",
    adminPhone: "",
  });
  const [autoApprove, setAutoApprove] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<RegisterResult | null>(null);

  const set = (k: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) =>
    setForm((f) => ({ ...f, [k]: e.target.value }));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setResult(null);
    try {
      const res = await httpsCallable(functions, "registerInstitution")({ ...form, autoApprove });
      setResult(res.data as RegisterResult);
      setForm({
        collegeName: "", district: "", region: "", address: "",
        phone: "", adminName: "", adminEmail: "", adminPhone: "",
      });
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  };

  const ready = form.collegeName && form.district && form.adminName && form.adminEmail;

  return (
    <div className="space-y-8">
      <div>
        <Link href="/director" className="text-xs font-bold text-blue-400 hover:text-blue-300">
          ← Back to network overview
        </Link>
        <h1 className="mt-3 text-3xl font-extrabold tracking-tight text-white">
          Onboard a College
        </h1>
        <p className="mt-1 text-sm text-slate-400">
          Creates the institution, its administrator account, and its registry entry in one step.
          {" "}
          {entries.length > 0 && `${entries.length} institution${entries.length === 1 ? "" : "s"} registered so far.`}
        </p>
      </div>

      {result && (
        <div className="rounded-2xl border border-emerald-500/30 bg-emerald-500/5 p-5">
          <h2 className="text-sm font-black uppercase tracking-widest text-emerald-400">
            {result.name} registered
          </h2>
          <dl className="mt-4 grid gap-3 sm:grid-cols-2">
            <Field label="Institution ID" value={result.institutionId} mono />
            <Field label="Status" value={result.status} />
            <Field label="Invite code" value={result.inviteCode} mono />
            <Field label="Administrator" value={result.adminEmail} />
          </dl>

          {result.temporaryPassword ? (
            <div className="mt-4 rounded-xl border border-amber-500/40 bg-amber-500/10 p-4">
              <p className="text-xs font-bold uppercase tracking-widest text-amber-300">
                Temporary password — shown once
              </p>
              <p className="mt-2 font-mono text-lg tracking-wider text-white">
                {result.temporaryPassword}
              </p>
              <p className="mt-2 text-xs text-amber-200/80">
                It is not stored anywhere and cannot be retrieved again. Send it to the college
                over a trusted channel and have them change it at first sign-in. If it is lost,
                use a Firebase password reset rather than re-registering the college.
              </p>
            </div>
          ) : (
            <p className="mt-4 text-xs text-slate-400">
              An account already existed for {result.adminEmail}; it has been made administrator of
              this college. Its existing password is unchanged.
            </p>
          )}

          <p className="mt-4 text-xs text-slate-400">
            {result.status === "pending"
              ? "The college can sign in and run its library now, but will not report to the directorate until approved on the network overview."
              : "Approved. Its figures appear on the network overview after the next rollup."}
          </p>
        </div>
      )}

      {error && (
        <div className="rounded-xl border border-red-500/30 bg-red-500/10 p-4 text-sm text-red-300">
          <p className="font-bold">Could not register this institution.</p>
          <p className="mt-1 font-mono text-xs opacity-80">{error}</p>
        </div>
      )}

      <form
        onSubmit={submit}
        className="space-y-6 rounded-2xl border border-blue-950 bg-[#070F1E] p-6 shadow-xl"
      >
        <fieldset className="space-y-4" disabled={busy}>
          <legend className="text-xs font-black uppercase tracking-widest text-blue-400">
            College
          </legend>
          <Input
            label="College name"
            required
            value={form.collegeName}
            onChange={set("collegeName")}
            placeholder="Government Degree College Ziam Sherpao"
          />
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block">
              <span className="mb-1 block text-[11px] font-bold uppercase tracking-wider text-slate-400">
                District <span className="text-red-400">*</span>
              </span>
              <select
                required
                value={form.district}
                onChange={set("district")}
                className="w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-3 py-2 text-sm text-slate-200 outline-none focus:border-[#C8A84B]"
              >
                <option value="">Select a district…</option>
                {KPK_DISTRICTS.map((d) => (
                  <option key={d} value={d}>
                    {d}
                  </option>
                ))}
              </select>
            </label>
            <Input
              label="Region / division"
              value={form.region}
              onChange={set("region")}
              placeholder="Peshawar"
            />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <Input label="Address" value={form.address} onChange={set("address")} />
            <Input label="College phone" value={form.phone} onChange={set("phone")} />
          </div>
        </fieldset>

        <fieldset className="space-y-4" disabled={busy}>
          <legend className="text-xs font-black uppercase tracking-widest text-blue-400">
            Administrator account
          </legend>
          <div className="grid gap-4 sm:grid-cols-2">
            <Input label="Full name" required value={form.adminName} onChange={set("adminName")} />
            <Input
              label="Email"
              type="email"
              required
              value={form.adminEmail}
              onChange={set("adminEmail")}
              placeholder="librarian@gdc.edu.pk"
            />
          </div>
          <Input label="Phone" value={form.adminPhone} onChange={set("adminPhone")} />
        </fieldset>

        <label className="flex items-start gap-3 rounded-xl border border-[#1E3050] bg-[#0D1F38] p-4">
          <input
            type="checkbox"
            checked={autoApprove}
            onChange={(e) => setAutoApprove(e.target.checked)}
            className="mt-0.5"
          />
          <span className="text-xs text-slate-300">
            <span className="font-bold">Approve immediately.</span> Leave unchecked to place the
            college in the pending queue — the approval step is what keeps growth deliberate rather
            than letting colleges appear on the network unreviewed.
          </span>
        </label>

        <button
          type="submit"
          disabled={busy || !ready}
          className="w-full rounded-lg bg-[#C8A84B] px-4 py-3 text-sm font-black uppercase tracking-widest text-[#1a1400] transition-colors hover:bg-[#E6C96E] disabled:opacity-40"
        >
          {busy ? "Registering…" : "Register institution"}
        </button>
      </form>
    </div>
  );
}

function Input({
  label,
  required,
  ...props
}: React.InputHTMLAttributes<HTMLInputElement> & { label: string }) {
  return (
    <label className="block">
      <span className="mb-1 block text-[11px] font-bold uppercase tracking-wider text-slate-400">
        {label} {required && <span className="text-red-400">*</span>}
      </span>
      <input
        {...props}
        required={required}
        className="w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-3 py-2 text-sm text-slate-200 outline-none focus:border-[#C8A84B]"
      />
    </label>
  );
}

function Field({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <dt className="text-[10px] font-black uppercase tracking-widest text-slate-500">{label}</dt>
      <dd className={`mt-0.5 text-sm text-white ${mono ? "font-mono" : ""}`}>{value}</dd>
    </div>
  );
}
