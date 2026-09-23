"use client";

import React, { useState } from "react";
import { useAnnouncements, publishAnnouncement, retractAnnouncement } from "@/lib/registry-admin";
import { useMyTier, tierCan } from "@/lib/staff";
import { PageHeader, Card, SectionTitle, Badge, Button, Input, Textarea, Field, EmptyState, Spinner } from "@/components/ui";
import { IconAnnouncement, IconPlus } from "@/components/icons";

export default function Announcements() {
  const { items, loading } = useAnnouncements();
  const tier = useMyTier();
  const canWrite = tierCan(tier, "write");
  const [showForm, setShowForm] = useState(false);

  if (loading) return <Spinner label="Loading announcements…" />;

  const active = items.filter((a) => !a.retracted);

  return (
    <div>
      <PageHeader
        title="Announcements"
        description="Circulars authored by the directorate. Readable by any signed-in account by design — no college app displays them on a screen yet, so treat this as the authoring side of a loop that is not fully closed until one does."
        actions={canWrite && (
          <Button variant="primary" size="sm" icon={<IconPlus className="h-3.5 w-3.5" />} onClick={() => setShowForm((v) => !v)}>
            New announcement
          </Button>
        )}
      />

      {showForm && <NewAnnouncementForm onDone={() => setShowForm(false)} />}

      {active.length === 0 ? (
        <EmptyState icon={<IconAnnouncement className="h-8 w-8" />} title="No announcements published" />
      ) : (
        <div className="space-y-3">
          {active.map((a) => (
            <Card key={a.id}>
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-sm font-bold text-white">{a.title}</p>
                  <p className="mt-1.5 whitespace-pre-wrap text-xs text-slate-400">{a.body}</p>
                  <p className="mt-2 text-[11px] text-slate-600">
                    {a.publishedByEmail} · {new Date(a.publishedAt).toLocaleString("en-PK")}
                  </p>
                </div>
                {canWrite && (
                  <Button variant="danger" size="sm" onClick={() => retractAnnouncement(a.id)}>Retract</Button>
                )}
              </div>
            </Card>
          ))}
        </div>
      )}

      {items.some((a) => a.retracted) && (
        <details className="mt-6 rounded-lg border border-slate-800 bg-[#0B1220] p-5">
          <summary className="cursor-pointer text-xs font-semibold text-slate-500">
            Retracted ({items.filter((a) => a.retracted).length})
          </summary>
          <div className="mt-3 space-y-2">
            {items.filter((a) => a.retracted).map((a) => (
              <div key={a.id} className="flex items-center gap-2 text-xs text-slate-500">
                <Badge>Retracted</Badge> {a.title}
              </div>
            ))}
          </div>
        </details>
      )}
    </div>
  );
}

function NewAnnouncementForm({ onDone }: { onDone: () => void }) {
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [saving, setSaving] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !body.trim() || saving) return;
    setSaving(true);
    try {
      await publishAnnouncement(title.trim(), body.trim());
      setTitle(""); setBody("");
      onDone();
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card className="mb-6">
      <SectionTitle icon={<IconAnnouncement className="h-4 w-4" />}>New announcement</SectionTitle>
      <form onSubmit={submit} className="space-y-3">
        <Field label="Title">
          <Input value={title} onChange={(e) => setTitle(e.target.value)} required />
        </Field>
        <Field label="Body">
          <Textarea value={body} onChange={(e) => setBody(e.target.value)} rows={4} required />
        </Field>
        <div className="flex justify-end gap-2">
          <Button variant="ghost" type="button" onClick={onDone}>Cancel</Button>
          <Button variant="primary" type="submit" disabled={saving}>{saving ? "Publishing…" : "Publish"}</Button>
        </div>
      </form>
    </Card>
  );
}
