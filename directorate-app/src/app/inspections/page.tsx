"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useDirectorateNetwork } from "@/lib/directorate";
import { useInspections, scheduleInspection, completeInspection, Inspection } from "@/lib/registry-admin";
import { useMyTier, tierCan } from "@/lib/staff";
import { PageHeader, Card, SectionTitle, Badge, Button, Input, Textarea, Select, Field, EmptyState, Spinner } from "@/components/ui";
import { IconInspection, IconPlus } from "@/components/icons";

export default function Inspections() {
  const { colleges } = useDirectorateNetwork();
  const { items, loading } = useInspections();
  const tier = useMyTier();
  const canWrite = tierCan(tier, "write");
  const [showForm, setShowForm] = useState(false);
  const [completing, setCompleting] = useState<string | null>(null);

  if (loading) return <Spinner label="Loading inspections…" />;

  const scheduled = items.filter((i) => i.status === "scheduled");
  const completed = items.filter((i) => i.status === "completed");

  return (
    <div>
      <PageHeader
        title="Site Inspections"
        description="Scheduling and findings for directorate visits to a college."
        actions={canWrite && (
          <Button variant="primary" size="sm" icon={<IconPlus className="h-3.5 w-3.5" />} onClick={() => setShowForm((v) => !v)}>
            Schedule visit
          </Button>
        )}
      />

      {showForm && <NewInspectionForm colleges={colleges} onDone={() => setShowForm(false)} />}

      <SectionTitle icon={<IconInspection className="h-4 w-4" />}>Scheduled ({scheduled.length})</SectionTitle>
      {scheduled.length === 0 ? (
        <EmptyState title="Nothing scheduled" />
      ) : (
        <div className="mb-8 space-y-2.5">
          {scheduled.map((i) => (
            <Card key={i.id} padded={false} className="p-4">
              {completing === i.id ? (
                <CompleteForm inspection={i} onDone={() => setCompleting(null)} />
              ) : (
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div>
                    <Badge tone="blue">{i.scheduledDate}</Badge>
                    <Link href={`/${encodeURIComponent(i.collegeId)}`} className="ml-2 text-sm font-semibold text-white hover:text-blue-400">
                      {i.collegeName}
                    </Link>
                    <p className="mt-1.5 text-xs text-slate-400">{i.purpose}</p>
                  </div>
                  {canWrite && <Button variant="secondary" size="sm" onClick={() => setCompleting(i.id)}>Log findings</Button>}
                </div>
              )}
            </Card>
          ))}
        </div>
      )}

      <SectionTitle icon={<IconInspection className="h-4 w-4" />}>Completed ({completed.length})</SectionTitle>
      {completed.length === 0 ? (
        <EmptyState title="No completed visits yet" />
      ) : (
        <div className="space-y-2.5">
          {completed.map((i) => (
            <Card key={i.id}>
              <div className="flex items-center gap-2">
                <Badge tone="emerald">Completed</Badge>
                <Link href={`/${encodeURIComponent(i.collegeId)}`} className="text-sm font-semibold text-white hover:text-blue-400">
                  {i.collegeName}
                </Link>
                <span className="text-xs text-slate-600">{i.scheduledDate}</span>
              </div>
              <p className="mt-1.5 text-xs text-slate-400">{i.purpose}</p>
              {i.findings && <p className="mt-2 rounded-md bg-slate-900/50 p-2.5 text-xs text-slate-300">{i.findings}</p>}
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}

function CompleteForm({ inspection, onDone }: { inspection: Inspection; onDone: () => void }) {
  const [findings, setFindings] = useState("");
  const [saving, setSaving] = useState(false);
  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (saving) return;
    setSaving(true);
    try {
      await completeInspection(inspection.id, inspection.collegeId, findings.trim());
      onDone();
    } finally {
      setSaving(false);
    }
  };
  return (
    <form onSubmit={submit} className="space-y-3">
      <p className="text-xs font-semibold text-white">{inspection.collegeName} — {inspection.purpose}</p>
      <Textarea value={findings} onChange={(e) => setFindings(e.target.value)} rows={3} placeholder="Findings from the visit…" autoFocus />
      <div className="flex justify-end gap-2">
        <Button variant="ghost" type="button" onClick={onDone}>Cancel</Button>
        <Button variant="primary" type="submit" disabled={saving}>{saving ? "Saving…" : "Mark completed"}</Button>
      </div>
    </form>
  );
}

function NewInspectionForm({ colleges, onDone }: { colleges: { institutionId: string; name: string }[]; onDone: () => void }) {
  const [collegeId, setCollegeId] = useState(colleges[0]?.institutionId || "");
  const [scheduledDate, setScheduledDate] = useState("");
  const [purpose, setPurpose] = useState("");
  const [saving, setSaving] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const college = colleges.find((c) => c.institutionId === collegeId);
    if (!college || !scheduledDate || !purpose.trim() || saving) return;
    setSaving(true);
    try {
      await scheduleInspection(college.institutionId, college.name, scheduledDate, purpose.trim());
      setScheduledDate(""); setPurpose("");
      onDone();
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card className="mb-6">
      <SectionTitle icon={<IconInspection className="h-4 w-4" />}>Schedule a visit</SectionTitle>
      <form onSubmit={submit} className="grid gap-3 sm:grid-cols-2">
        <Field label="Institution">
          <Select value={collegeId} onChange={(e) => setCollegeId(e.target.value)} required>
            {colleges.map((c) => <option key={c.institutionId} value={c.institutionId}>{c.name}</option>)}
          </Select>
        </Field>
        <Field label="Date">
          <Input type="date" value={scheduledDate} onChange={(e) => setScheduledDate(e.target.value)} required />
        </Field>
        <div className="sm:col-span-2">
          <Field label="Purpose">
            <Input value={purpose} onChange={(e) => setPurpose(e.target.value)} placeholder="e.g. Annual compliance review" required />
          </Field>
        </div>
        <div className="sm:col-span-2 flex justify-end gap-2">
          <Button variant="ghost" type="button" onClick={onDone}>Cancel</Button>
          <Button variant="primary" type="submit" disabled={saving}>{saving ? "Scheduling…" : "Schedule"}</Button>
        </div>
      </form>
    </Card>
  );
}
