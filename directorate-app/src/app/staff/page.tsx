"use client";

import React, { useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { useDirectorateStaff, enrolStaff, removeStaffRecord, useMyTier, tierCan, StaffTier, TIER_LABEL, TIER_DESCRIPTION } from "@/lib/staff";
import { PageHeader, Card, SectionTitle, Badge, Button, Input, Select, Field, EmptyState, Spinner } from "@/components/ui";
import { IconStaff, IconPlus } from "@/components/icons";

const TIERS: StaffTier[] = ["super_admin", "regional", "analyst"];
const TIER_TONE: Record<StaffTier, "emerald" | "blue" | "neutral"> = {
  super_admin: "emerald",
  regional: "blue",
  analyst: "neutral",
};

export default function Staff() {
  const { profile } = useAuth();
  const { staff, loading } = useDirectorateStaff();
  const myTier = useMyTier();
  const canManage = tierCan(myTier, "manageStaff");
  const [showForm, setShowForm] = useState(false);

  if (loading) return <Spinner label="Loading staff directory…" />;

  return (
    <div>
      <PageHeader
        title="Staff & Roles"
        description="Who at the Higher Education Department can access this portal, and what each tier can do."
        actions={canManage && (
          <Button variant="primary" size="sm" icon={<IconPlus className="h-3.5 w-3.5" />} onClick={() => setShowForm((v) => !v)}>
            Enrol account
          </Button>
        )}
      />

      <Card className="mb-6">
        <SectionTitle icon={<IconStaff className="h-4 w-4" />}>Access tiers</SectionTitle>
        <div className="grid gap-3 sm:grid-cols-3">
          {TIERS.map((t) => (
            <div key={t} className="rounded-md border border-slate-800/80 p-3.5">
              <Badge tone={TIER_TONE[t]}>{TIER_LABEL[t]}</Badge>
              <p className="mt-2 text-xs text-slate-400">{TIER_DESCRIPTION[t]}</p>
            </div>
          ))}
        </div>
        <p className="mt-4 text-[11px] text-slate-600">
          An account with no record below is treated as Super Admin by default — this is a deliberate bootstrap
          rule, not an oversight, so a rules deploy can never lock out an existing account on its own.
        </p>
      </Card>

      {!canManage && (
        <Card className="mb-6 border-amber-900/60 bg-amber-500/5">
          <p className="text-xs text-amber-200">
            Your account ({TIER_LABEL[myTier]}) can view this directory but cannot enrol or remove staff.
          </p>
        </Card>
      )}

      {showForm && <EnrolForm byEmail={profile?.email || ""} onDone={() => setShowForm(false)} />}

      {staff.length === 0 ? (
        <EmptyState icon={<IconStaff className="h-8 w-8" />} title="No enrolled staff records" detail="Every directorate account currently has full Super Admin access by default." />
      ) : (
        <Card padded={false}>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-900/40 text-[10px] font-bold uppercase tracking-wider text-slate-500">
                <tr>
                  <th className="px-4 py-3">Email</th>
                  <th className="px-4 py-3">Tier</th>
                  <th className="px-4 py-3">Enrolled by</th>
                  <th className="px-4 py-3">Enrolled</th>
                  {canManage && <th className="px-4 py-3 text-right">Actions</th>}
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/60">
                {staff.map((s) => (
                  <tr key={s.uid}>
                    <td className="px-4 py-3.5 font-semibold text-white">{s.email}</td>
                    <td className="px-4 py-3.5"><Badge tone={TIER_TONE[s.tier]}>{TIER_LABEL[s.tier]}</Badge></td>
                    <td className="px-4 py-3.5 text-slate-400">{s.addedByEmail}</td>
                    <td className="px-4 py-3.5 text-slate-500">{new Date(s.addedAt).toLocaleDateString("en-PK")}</td>
                    {canManage && (
                      <td className="px-4 py-3.5 text-right">
                        <Button variant="danger" size="sm" onClick={() => removeStaffRecord(s.uid)}>Remove record</Button>
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </Card>
      )}
    </div>
  );
}

function EnrolForm({ byEmail, onDone }: { byEmail: string; onDone: () => void }) {
  const [uid, setUid] = useState("");
  const [email, setEmail] = useState("");
  const [tier, setTier] = useState<StaffTier>("analyst");
  const [saving, setSaving] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!uid.trim() || !email.trim() || saving) return;
    setSaving(true);
    try {
      await enrolStaff(uid.trim(), email.trim(), tier, [], byEmail);
      setUid(""); setEmail("");
      onDone();
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card className="mb-6">
      <SectionTitle icon={<IconStaff className="h-4 w-4" />}>Enrol an account</SectionTitle>
      <p className="mb-3 text-xs text-slate-500">
        The account must already exist in Firebase Auth with the directorate_admin role on its /users profile —
        this form only sets its ACCESS TIER, it does not create the login itself.
      </p>
      <form onSubmit={submit} className="grid gap-3 sm:grid-cols-3">
        <Field label="Firebase Auth UID">
          <Input value={uid} onChange={(e) => setUid(e.target.value)} placeholder="From the Firebase console" required />
        </Field>
        <Field label="Email (for display only)">
          <Input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </Field>
        <Field label="Tier">
          <Select value={tier} onChange={(e) => setTier(e.target.value as StaffTier)}>
            {TIERS.map((t) => <option key={t} value={t}>{TIER_LABEL[t]}</option>)}
          </Select>
        </Field>
        <div className="sm:col-span-3 flex justify-end gap-2">
          <Button variant="ghost" type="button" onClick={onDone}>Cancel</Button>
          <Button variant="primary" type="submit" disabled={saving}>{saving ? "Enrolling…" : "Enrol"}</Button>
        </div>
      </form>
    </Card>
  );
}
