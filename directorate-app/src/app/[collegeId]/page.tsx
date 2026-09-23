"use client";

import React, { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { collection, doc, getDoc, getDocs, DocumentData } from "firebase/firestore";
import { db } from "@/lib/firebase";
import { ROOT_COLLECTIONS, COLLECTIONS } from "@/lib/schema";
import { isStale, DirectorateSnapshot } from "@/lib/directorate";
import { scoreCompliance } from "@/lib/analytics";
import { useCollegeNote, saveCollegeNote, useFollowups, createFollowup, resolveFollowup, useInspections } from "@/lib/registry-admin";
import { useMyTier, tierCan } from "@/lib/staff";
import { PageHeader, Card, SectionTitle, StatTile, Badge, Meter, Button, Input, Textarea, Field, Spinner, numberFmt } from "@/components/ui";
import { IconCheck, IconFollowup, IconInspection } from "@/components/icons";

/**
 * Single-college drill-down.
 *
 * The directorate is a read-only oversight role and is deliberately NOT a
 * member of any college, so tenant collections (members, loans) stay closed
 * under the security rules. What it can legitimately see is:
 *
 *   - the college's published aggregate snapshot  (/directorate_index)
 *   - the college's public catalogue              (/institutions/{id}/books)
 *   - its own directorate_notes / followups / inspections about this college
 *
 * Anything requiring patron-level data stays with the college. That
 * boundary is enforced by the rules, not just by this page.
 */
export default function CollegeDetail() {
  const params = useParams<{ collegeId: string }>();
  const collegeId = decodeURIComponent(Array.isArray(params.collegeId) ? params.collegeId[0] : params.collegeId || "");
  const tier = useMyTier();
  const canWrite = tierCan(tier, "write");

  const [snapshot, setSnapshot] = useState<DirectorateSnapshot | null>(null);
  const [institution, setInstitution] = useState<DocumentData | null>(null);
  const [books, setBooks] = useState<DocumentData[]>([]);
  const [catalogueError, setCatalogueError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const { note } = useCollegeNote(collegeId);
  const { items: followups } = useFollowups(collegeId);
  const { items: inspections } = useInspections(collegeId);

  useEffect(() => {
    if (!collegeId) return;
    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const [idxSnap, instSnap] = await Promise.all([
          getDoc(doc(db, ROOT_COLLECTIONS.directorateIndex, collegeId)),
          getDoc(doc(db, ROOT_COLLECTIONS.institutions, collegeId)),
        ]);
        if (cancelled) return;
        if (idxSnap.exists()) {
          const d = idxSnap.data();
          setSnapshot({
            institutionId: collegeId,
            name: d.name || d.collegeName || collegeId,
            location: d.location || d.address || "",
            district: d.district || "",
            contactEmail: d.contactEmail || d.email || "",
            phone: d.phone || "",
            booksCount: Number(d.booksCount ?? 0),
            ebooksCount: Number(d.ebooksCount ?? 0),
            membersCount: Number(d.membersCount ?? 0),
            activeLoans: Number(d.activeLoans ?? 0),
            overdueCount: Number(d.overdueCount ?? 0),
            reservationsCount: Number(d.reservationsCount ?? 0),
            finesOutstanding: Number(d.finesOutstanding ?? 0),
            lastSyncAt: Number(d.lastSyncAt ?? d.lastSeen ?? 0),
            lastSyncPlatform: d.lastSyncPlatform || "unknown",
            schemaVersion: Number(d.schemaVersion ?? 1),
          });
        }
        if (instSnap.exists()) setInstitution(instSnap.data());
      } finally {
        if (!cancelled) setLoading(false);
      }
      try {
        const booksSnap = await getDocs(collection(db, ROOT_COLLECTIONS.institutions, collegeId, COLLECTIONS.books));
        if (cancelled) return;
        setBooks(booksSnap.docs.map((d) => ({ id: d.id, ...d.data() }) as DocumentData).filter((b) => !b.deleted));
      } catch (err) {
        if (!cancelled) setCatalogueError((err as Error).message);
      }
    })();
    return () => { cancelled = true; };
  }, [collegeId]);

  const categories = useMemo(() => {
    const counts = new Map<string, number>();
    for (const b of books) counts.set((b.category as string) || "Uncategorized", (counts.get((b.category as string) || "Uncategorized") || 0) + 1);
    return [...counts.entries()].sort((a, b) => b[1] - a[1]).slice(0, 8);
  }, [books]);

  const availability = useMemo(() => {
    const issued = books.filter((b) => b.status === "Issued").length;
    return { issued, available: books.length - issued };
  }, [books]);

  if (loading) return <Spinner label={`Loading ${collegeId}…`} />;

  const name = snapshot?.name || institution?.name || collegeId;
  const compliance = snapshot ? scoreCompliance(snapshot) : null;

  return (
    <div>
      <Link href="/registry" className="text-xs font-semibold text-blue-400 hover:text-blue-300">← Back to registry</Link>

      <PageHeader
        title={name}
        description={`${collegeId}${snapshot?.location ? ` · ${snapshot.location}` : ""}${snapshot?.district ? ` · ${snapshot.district} district` : ""}`}
        actions={
          snapshot && (
            <div className="text-right">
              <p className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Last published</p>
              <Badge tone={isStale(snapshot) ? "amber" : "emerald"}>
                {snapshot.lastSyncAt ? new Date(snapshot.lastSyncAt).toLocaleString("en-PK") : "Never"}
              </Badge>
            </div>
          )
        }
      />

      {!snapshot && (
        <Card className="mb-6 border-amber-900/60 bg-amber-500/5">
          <p className="text-sm font-semibold text-amber-200">This college has not published a snapshot yet.</p>
        </Card>
      )}

      {snapshot && (
        <div className="mb-6 grid grid-cols-2 gap-3 lg:grid-cols-4">
          <StatTile label="Books" value={snapshot.booksCount} />
          <StatTile label="Members" value={snapshot.membersCount} />
          <StatTile label="Active Loans" value={snapshot.activeLoans} tone="blue" />
          <StatTile label="Overdue" value={snapshot.overdueCount} tone={snapshot.overdueCount > 0 ? "red" : "default"} />
          <StatTile label="E-Books" value={snapshot.ebooksCount} />
          <StatTile label="Reservations" value={snapshot.reservationsCount} />
          <StatTile label="Fines Outstanding" value={snapshot.finesOutstanding} prefix="Rs " tone="amber" />
          <StatTile
            label="Utilisation"
            value={snapshot.booksCount > 0 ? Math.round((snapshot.activeLoans / snapshot.booksCount) * 100) : 0}
            suffix="%"
          />
        </div>
      )}

      {compliance && (
        <Card className="mb-6">
          <SectionTitle>Compliance — {compliance.score}%</SectionTitle>
          <div className="grid gap-3 sm:grid-cols-2">
            {compliance.checks.map((c) => (
              <div key={c.key} className="flex items-start gap-2.5">
                <Badge tone={c.pass ? "emerald" : "red"}>{c.pass ? "Pass" : "Fail"}</Badge>
                <div>
                  <p className="text-xs font-semibold text-slate-200">{c.label}</p>
                  <p className="text-[11px] text-slate-500">{c.detail}</p>
                </div>
              </div>
            ))}
          </div>
        </Card>
      )}

      <div className="mb-6 grid gap-5 lg:grid-cols-2">
        <Card>
          <SectionTitle>Catalogue by category</SectionTitle>
          {catalogueError ? (
            <p className="text-xs text-slate-500">Catalogue not readable ({catalogueError}).</p>
          ) : categories.length === 0 ? (
            <p className="text-xs text-slate-500">No catalogue records found.</p>
          ) : (
            <div className="space-y-3">
              {categories.map(([label, count]) => (
                <div key={label}>
                  <div className="mb-1 flex justify-between text-xs">
                    <span className="font-semibold text-slate-300">{label}</span>
                    <span className="text-slate-500">{numberFmt.format(count)} · {Math.round((count / books.length) * 100)}%</span>
                  </div>
                  <Meter pct={(count / books.length) * 100} tone="blue" />
                </div>
              ))}
            </div>
          )}
        </Card>

        <Card>
          <SectionTitle>Catalogue availability</SectionTitle>
          {catalogueError ? (
            <p className="text-xs text-slate-500">Not available.</p>
          ) : (
            <div className="grid grid-cols-2 gap-4">
              <StatTile label="On shelf" value={availability.available} tone="emerald" />
              <StatTile label="On loan" value={availability.issued} tone="blue" />
            </div>
          )}
        </Card>
      </div>

      <div className="mb-6 grid gap-5 lg:grid-cols-2">
        <FollowupsPanel collegeId={collegeId} collegeName={name} followups={followups} canWrite={canWrite} />
        <InspectionsPanel inspections={inspections} />
      </div>

      <NotesPanel collegeId={collegeId} note={note} canWrite={canWrite} />

      {(institution?.email || institution?.phone || snapshot?.contactEmail) && (
        <Card className="mt-6">
          <SectionTitle>Contact</SectionTitle>
          <dl className="grid gap-3 text-sm sm:grid-cols-3">
            {(snapshot?.contactEmail || institution?.email) && (
              <div><dt className="text-[10px] uppercase tracking-widest text-slate-600">Email</dt><dd className="text-slate-300">{snapshot?.contactEmail || institution?.email}</dd></div>
            )}
            {(snapshot?.phone || institution?.phone) && (
              <div><dt className="text-[10px] uppercase tracking-widest text-slate-600">Phone</dt><dd className="text-slate-300">{snapshot?.phone || institution?.phone}</dd></div>
            )}
            {(snapshot?.location || institution?.address) && (
              <div><dt className="text-[10px] uppercase tracking-widest text-slate-600">Address</dt><dd className="text-slate-300">{snapshot?.location || institution?.address}</dd></div>
            )}
          </dl>
        </Card>
      )}
    </div>
  );
}

function FollowupsPanel({ collegeId, collegeName, followups, canWrite }: { collegeId: string; collegeName: string; followups: ReturnType<typeof useFollowups>["items"]; canWrite: boolean }) {
  const [adding, setAdding] = useState(false);
  const [title, setTitle] = useState("");
  const open = followups.filter((f) => f.status === "open");

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    await createFollowup(collegeId, collegeName, title.trim(), "", "");
    setTitle("");
    setAdding(false);
  };

  return (
    <Card>
      <SectionTitle
        icon={<IconFollowup className="h-4 w-4" />}
        action={canWrite && <Button variant="ghost" size="sm" onClick={() => setAdding((v) => !v)}>+ Add</Button>}
      >
        Follow-ups ({open.length} open)
      </SectionTitle>
      {adding && (
        <form onSubmit={submit} className="mb-3 flex gap-2">
          <Input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="What needs following up?" autoFocus className="flex-1" />
          <Button variant="primary" size="sm" type="submit">Add</Button>
        </form>
      )}
      {open.length === 0 ? (
        <p className="text-xs text-slate-600">Nothing open for this college.</p>
      ) : (
        <div className="space-y-2">
          {open.map((f) => (
            <div key={f.id} className="flex items-center justify-between gap-2 text-xs">
              <span className="text-slate-300">{f.title}</span>
              {canWrite && (
                <button onClick={() => resolveFollowup(f.id, collegeId)} className="text-slate-500 hover:text-emerald-400">
                  <IconCheck className="h-3.5 w-3.5" />
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </Card>
  );
}

function InspectionsPanel({ inspections }: { inspections: ReturnType<typeof useInspections>["items"] }) {
  return (
    <Card>
      <SectionTitle icon={<IconInspection className="h-4 w-4" />}>Inspection history</SectionTitle>
      {inspections.length === 0 ? (
        <p className="text-xs text-slate-600">No visits recorded for this college.</p>
      ) : (
        <div className="space-y-2">
          {inspections.map((i) => (
            <div key={i.id} className="flex items-center justify-between text-xs">
              <span className="text-slate-300">{i.purpose}</span>
              <Badge tone={i.status === "completed" ? "emerald" : "blue"}>{i.scheduledDate}</Badge>
            </div>
          ))}
        </div>
      )}
    </Card>
  );
}

function NotesPanel({ collegeId, note, canWrite }: { collegeId: string; note: ReturnType<typeof useCollegeNote>["note"]; canWrite: boolean }) {
  const [editing, setEditing] = useState(false);
  const [principal, setPrincipal] = useState(note?.verifiedPrincipal || "");
  const [remarks, setRemarks] = useState(note?.internalRemarks || "");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    setPrincipal(note?.verifiedPrincipal || "");
    setRemarks(note?.internalRemarks || "");
  }, [note]);

  const save = async () => {
    setSaving(true);
    try {
      await saveCollegeNote(collegeId, { verifiedPrincipal: principal, internalRemarks: remarks });
      setEditing(false);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Card className="mt-6">
      <SectionTitle action={canWrite && !editing && <Button variant="ghost" size="sm" onClick={() => setEditing(true)}>Edit</Button>}>
        Directorate notes
      </SectionTitle>
      <p className="mb-3 text-[11px] text-slate-600">
        Internal to the directorate — never visible to the college itself, and separate from anything the college publishes.
      </p>
      {editing ? (
        <div className="space-y-3">
          <Field label="Verified principal / head of institution">
            <Input value={principal} onChange={(e) => setPrincipal(e.target.value)} />
          </Field>
          <Field label="Internal remarks">
            <Textarea value={remarks} onChange={(e) => setRemarks(e.target.value)} rows={3} />
          </Field>
          <div className="flex justify-end gap-2">
            <Button variant="ghost" size="sm" onClick={() => setEditing(false)}>Cancel</Button>
            <Button variant="primary" size="sm" onClick={save} disabled={saving}>{saving ? "Saving…" : "Save"}</Button>
          </div>
        </div>
      ) : note ? (
        <div className="space-y-2 text-xs">
          {note.verifiedPrincipal && <p><span className="text-slate-500">Principal: </span><span className="text-slate-200">{note.verifiedPrincipal}</span></p>}
          {note.internalRemarks && <p className="whitespace-pre-wrap text-slate-300">{note.internalRemarks}</p>}
          <p className="text-slate-600">Updated by {note.updatedByEmail} · {new Date(note.updatedAt).toLocaleDateString("en-PK")}</p>
        </div>
      ) : (
        <p className="text-xs text-slate-600">No notes recorded for this institution yet.</p>
      )}
    </Card>
  );
}
