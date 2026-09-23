"use client";

import React, { useState } from "react";
import Link from "next/link";
import { useDirectorateNetwork } from "@/lib/directorate";
import { useFollowups, createFollowup, resolveFollowup, FollowupStatus } from "@/lib/registry-admin";
import { useMyTier, tierCan } from "@/lib/staff";
import { PageHeader, Card, SectionTitle, Badge, Button, Input, Select, Textarea, Field, EmptyState, Spinner } from "@/components/ui";
import { IconFollowup, IconPlus, IconCheck } from "@/components/icons";

export default function Followups() {
  const { colleges } = useDirectorateNetwork();
  const { items, loading } = useFollowups();
  const tier = useMyTier();
  const canWrite = tierCan(tier, "write");
  const [status, setStatus] = useState<FollowupStatus | "">("open");
  const [showForm, setShowForm] = useState(false);

  const filtered = items.filter((f) => !status || f.status === status);

  if (loading) return <Spinner label="Loading follow-ups…" />;

  return (
    <div>
      <PageHeader
        title="Follow-ups"
        description="Things the directorate is chasing with a specific college — internal to this portal, never visible to the college itself."
        actions={
          canWrite && (
            <Button variant="primary" size="sm" icon={<IconPlus className="h-3.5 w-3.5" />} onClick={() => setShowForm((v) => !v)}>
              New follow-up
            </Button>
          )
        }
      />

      {showForm && <NewFollowupForm colleges={colleges} onDone={() => setShowForm(false)} />}

      <div className="mb-4 flex justify-end">
        <Select value={status} onChange={(e) => setStatus(e.target.value as FollowupStatus | "")}>
          <option value="open">Open only</option>
          <option value="resolved">Resolved only</option>
          <option value="">All</option>
        </Select>
      </div>

      {filtered.length === 0 ? (
        <EmptyState icon={<IconFollowup className="h-8 w-8" />} title="Nothing here" />
      ) : (
        <div className="space-y-2.5">
          {filtered.map((f) => (
            <Card key={f.id} padded={false} className="p-4">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <div className="flex items-center gap-2">
                    <Badge tone={f.status === "open" ? "amber" : "emerald"}>{f.status}</Badge>
                    <p className="text-sm font-semibold text-white">{f.title}</p>
                  </div>
                  <Link href={`/${encodeURIComponent(f.collegeId)}`} className="mt-1 block text-xs text-blue-400 hover:text-blue-300">
                    {f.collegeName}
                  </Link>
                  {f.detail && <p className="mt-1.5 text-xs text-slate-400">{f.detail}</p>}
                  <p className="mt-1.5 text-[11px] text-slate-600">
                    Raised by {f.createdByEmail} · {new Date(f.createdAt).toLocaleDateString("en-PK")}
                    {f.dueDate && ` · due ${f.dueDate}`}
                  </p>
                </div>
                {canWrite && f.status === "open" && (
                  <Button variant="secondary" size="sm" icon={<IconCheck className="h-3.5 w-3.5" />} onClick={() => resolveFollowup(f.id, f.collegeId)}>
                    Resolve
                  </Button>
                )}
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}

function NewFollowupForm({ colleges, onDone }: { colleges: { institutionId: string; name: string }[]; onDone: () => void }) {
  const [collegeId, setCollegeId] = useState(colleges[0]?.institutionId || "");
  const [title, setTitle] = useState("");
  const [detail, setDetail] = useState("");
  const [dueDate, setDueDate] = useState("");
  const [saving, setSaving] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const college = colleges.find((c) => c.institutionId === collegeId);
    if (!college || !title.trim() || saving) return;
    setSaving(true);
    try {
      await createFollowup(college.institutionId, college.name, title.trim(), detail.trim(), dueDate);
      setTitle(""); setDetail(""); setDueDate("");
      onDone();
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card className="mb-6">
      <SectionTitle icon={<IconFollowup className="h-4 w-4" />}>New follow-up</SectionTitle>
      <form onSubmit={submit} className="grid gap-3 sm:grid-cols-2">
        <Field label="Institution">
          <Select value={collegeId} onChange={(e) => setCollegeId(e.target.value)} required>
            {colleges.map((c) => <option key={c.institutionId} value={c.institutionId}>{c.name}</option>)}
          </Select>
        </Field>
        <Field label="Due date (optional)">
          <Input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} />
        </Field>
        <div className="sm:col-span-2">
          <Field label="Title">
            <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Requested catalogue cleanup" required />
          </Field>
        </div>
        <div className="sm:col-span-2">
          <Field label="Detail (optional)">
            <Textarea value={detail} onChange={(e) => setDetail(e.target.value)} rows={2} />
          </Field>
        </div>
        <div className="sm:col-span-2 flex justify-end gap-2">
          <Button variant="ghost" type="button" onClick={onDone}>Cancel</Button>
          <Button variant="primary" type="submit" disabled={saving}>{saving ? "Saving…" : "Create follow-up"}</Button>
        </div>
      </form>
    </Card>
  );
}
