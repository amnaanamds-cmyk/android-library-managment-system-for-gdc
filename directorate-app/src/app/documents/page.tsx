"use client";

import React, { useState } from "react";
import { useDocuments, addDocument, removeDocument } from "@/lib/registry-admin";
import { useMyTier, tierCan } from "@/lib/staff";
import { PageHeader, Card, SectionTitle, Button, Input, Textarea, Field, EmptyState, Spinner } from "@/components/ui";
import { IconDocument, IconPlus } from "@/components/icons";

export default function Documents() {
  const { items, loading } = useDocuments();
  const tier = useMyTier();
  const canWrite = tierCan(tier, "write");
  const [showForm, setShowForm] = useState(false);

  if (loading) return <Spinner label="Loading documents…" />;

  return (
    <div>
      <PageHeader
        title="Policy Documents"
        description="A repository of links, not uploaded files — this project has no Storage bucket rules reviewed for directorate use, so a document here points to something already hosted elsewhere."
        actions={canWrite && (
          <Button variant="primary" size="sm" icon={<IconPlus className="h-3.5 w-3.5" />} onClick={() => setShowForm((v) => !v)}>
            Add document
          </Button>
        )}
      />

      {showForm && <NewDocumentForm onDone={() => setShowForm(false)} />}

      {items.length === 0 ? (
        <EmptyState icon={<IconDocument className="h-8 w-8" />} title="No documents added yet" />
      ) : (
        <div className="grid gap-3 sm:grid-cols-2">
          {items.map((d) => (
            <Card key={d.id}>
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <a href={d.url} target="_blank" rel="noopener noreferrer" className="text-sm font-bold text-white hover:text-amber-400">
                    {d.title}
                  </a>
                  {d.description && <p className="mt-1 text-xs text-slate-400">{d.description}</p>}
                  <p className="mt-1.5 truncate font-mono text-[11px] text-slate-600">{d.url}</p>
                </div>
                {canWrite && <Button variant="danger" size="sm" onClick={() => removeDocument(d.id)}>Remove</Button>}
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}

function NewDocumentForm({ onDone }: { onDone: () => void }) {
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [url, setUrl] = useState("");
  const [saving, setSaving] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !url.trim() || saving) return;
    setSaving(true);
    try {
      await addDocument(title.trim(), description.trim(), url.trim());
      setTitle(""); setDescription(""); setUrl("");
      onDone();
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card className="mb-6">
      <SectionTitle icon={<IconDocument className="h-4 w-4" />}>Add document</SectionTitle>
      <form onSubmit={submit} className="space-y-3">
        <Field label="Title">
          <Input value={title} onChange={(e) => setTitle(e.target.value)} required />
        </Field>
        <Field label="Link (URL)">
          <Input type="url" value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://…" required />
        </Field>
        <Field label="Description (optional)">
          <Textarea value={description} onChange={(e) => setDescription(e.target.value)} rows={2} />
        </Field>
        <div className="flex justify-end gap-2">
          <Button variant="ghost" type="button" onClick={onDone}>Cancel</Button>
          <Button variant="primary" type="submit" disabled={saving}>{saving ? "Adding…" : "Add"}</Button>
        </div>
      </form>
    </Card>
  );
}
