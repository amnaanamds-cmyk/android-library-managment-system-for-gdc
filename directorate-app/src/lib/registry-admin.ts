// lib/registry-admin.ts
//
// Six directorate-internal working collections, consolidated into one file
// because they share the same shape (small, directorate-only or
// directorate-written CRUD) rather than because they are conceptually one
// thing. Each section is independent; split it out if it grows past this.
//
// Every write here also appends to the audit log (lib/audit.ts) — see each
// function. That is what makes the audit log a real record of what
// happened rather than a feature nobody's writes touch.

"use client";

import { useEffect, useState } from "react";
import {
  addDoc,
  collection,
  doc,
  deleteDoc,
  onSnapshot,
  orderBy,
  query,
  setDoc,
  updateDoc,
  where,
} from "firebase/firestore";
import { db, auth } from "./firebase";
import { MIS_COLLECTIONS } from "./schema";
import { logAction } from "./audit";

function currentEmail(): string {
  return auth.currentUser?.email || "";
}

// ── Administrative notes overlay, one document per college ─────────────────
//
// Deliberately separate from /institutions/{collegeId}, which the college
// itself owns and publishes. Nothing written here can be mistaken for
// something the college said, and writing a note never requires (or grants)
// write access to a single byte of tenant data.

export interface CollegeNote {
  collegeId: string;
  verifiedPrincipal: string;
  verifiedEnrollment: number | null;
  establishedYear: number | null;
  internalRemarks: string;
  updatedAt: number;
  updatedByEmail: string;
}

export function useCollegeNote(collegeId: string) {
  const [note, setNote] = useState<CollegeNote | null>(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    if (!collegeId) return;
    const unsub = onSnapshot(
      doc(db, MIS_COLLECTIONS.notes, collegeId),
      (snap) => {
        setNote(snap.exists() ? (snap.data() as CollegeNote) : null);
        setLoading(false);
      },
      () => setLoading(false),
    );
    return () => unsub();
  }, [collegeId]);
  return { note, loading };
}

export async function saveCollegeNote(
  collegeId: string,
  fields: Partial<Pick<CollegeNote, "verifiedPrincipal" | "verifiedEnrollment" | "establishedYear" | "internalRemarks">>,
): Promise<void> {
  await setDoc(
    doc(db, MIS_COLLECTIONS.notes, collegeId),
    { collegeId, ...fields, updatedAt: Date.now(), updatedByEmail: currentEmail() },
    { merge: true },
  );
  await logAction("note.save", collegeId, fields);
}

// ── Follow-ups: things the directorate is chasing with a college ───────────

export type FollowupStatus = "open" | "resolved";

export interface Followup {
  id: string;
  collegeId: string;
  collegeName: string;
  title: string;
  detail: string;
  status: FollowupStatus;
  dueDate: string; // "YYYY-MM-DD", or "" for none
  createdAt: number;
  createdByEmail: string;
  resolvedAt: number | null;
}

export function useFollowups(collegeId?: string) {
  const [items, setItems] = useState<Followup[]>([]);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    const base = collection(db, MIS_COLLECTIONS.followups);
    const q = collegeId
      ? query(base, where("collegeId", "==", collegeId), orderBy("createdAt", "desc"))
      : query(base, orderBy("createdAt", "desc"));
    const unsub = onSnapshot(
      q,
      (snap) => {
        setItems(snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<Followup, "id">) })));
        setLoading(false);
      },
      () => setLoading(false),
    );
    return () => unsub();
  }, [collegeId]);
  return { items, loading };
}

export async function createFollowup(
  collegeId: string,
  collegeName: string,
  title: string,
  detail: string,
  dueDate: string,
): Promise<void> {
  const ref = await addDoc(collection(db, MIS_COLLECTIONS.followups), {
    collegeId,
    collegeName,
    title,
    detail,
    status: "open" as FollowupStatus,
    dueDate,
    createdAt: Date.now(),
    createdByEmail: currentEmail(),
    resolvedAt: null,
  });
  await logAction("followup.create", collegeId, { title, followupId: ref.id });
}

export async function resolveFollowup(id: string, collegeId: string): Promise<void> {
  await updateDoc(doc(db, MIS_COLLECTIONS.followups, id), {
    status: "resolved" as FollowupStatus,
    resolvedAt: Date.now(),
  });
  await logAction("followup.resolve", collegeId, { followupId: id });
}

// ── Announcements / circulars ───────────────────────────────────────────────
//
// Readable by any signed-in user by design — the intent is for a college's
// own apps to eventually display these. No client does that read yet; this
// is the authoring side, ready for that to land without another rules
// deploy. Say that plainly in the UI rather than implying the loop is closed.

export interface Announcement {
  id: string;
  title: string;
  body: string;
  publishedAt: number;
  publishedByEmail: string;
  retracted: boolean;
}

export function useAnnouncements() {
  const [items, setItems] = useState<Announcement[]>([]);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    const q = query(collection(db, MIS_COLLECTIONS.announcements), orderBy("publishedAt", "desc"));
    const unsub = onSnapshot(
      q,
      (snap) => {
        setItems(snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<Announcement, "id">) })));
        setLoading(false);
      },
      () => setLoading(false),
    );
    return () => unsub();
  }, []);
  return { items, loading };
}

export async function publishAnnouncement(title: string, body: string): Promise<void> {
  const ref = await addDoc(collection(db, MIS_COLLECTIONS.announcements), {
    title,
    body,
    publishedAt: Date.now(),
    publishedByEmail: currentEmail(),
    retracted: false,
  });
  await logAction("announcement.publish", ref.id, { title });
}

export async function retractAnnouncement(id: string): Promise<void> {
  await updateDoc(doc(db, MIS_COLLECTIONS.announcements, id), { retracted: true });
  await logAction("announcement.retract", id, {});
}

// ── Document / circular repository ──────────────────────────────────────────
//
// Metadata + an external link, not a file upload — this project has no
// Storage bucket rules reviewed for directorate use, and a link to a
// document already hosted (e.g. the department's own site) is both simpler
// and avoids opening a new upload surface without that review.

export interface PolicyDocument {
  id: string;
  title: string;
  description: string;
  url: string;
  addedAt: number;
  addedByEmail: string;
}

export function useDocuments() {
  const [items, setItems] = useState<PolicyDocument[]>([]);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    const q = query(collection(db, MIS_COLLECTIONS.documents), orderBy("addedAt", "desc"));
    const unsub = onSnapshot(
      q,
      (snap) => {
        setItems(snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<PolicyDocument, "id">) })));
        setLoading(false);
      },
      () => setLoading(false),
    );
    return () => unsub();
  }, []);
  return { items, loading };
}

export async function addDocument(title: string, description: string, url: string): Promise<void> {
  const ref = await addDoc(collection(db, MIS_COLLECTIONS.documents), {
    title,
    description,
    url,
    addedAt: Date.now(),
    addedByEmail: currentEmail(),
  });
  await logAction("document.add", ref.id, { title });
}

export async function removeDocument(id: string): Promise<void> {
  await deleteDoc(doc(db, MIS_COLLECTIONS.documents, id));
  await logAction("document.remove", id, {});
}

// ── Site inspections ─────────────────────────────────────────────────────

export type InspectionStatus = "scheduled" | "completed";

export interface Inspection {
  id: string;
  collegeId: string;
  collegeName: string;
  scheduledDate: string; // "YYYY-MM-DD"
  purpose: string;
  status: InspectionStatus;
  findings: string;
  createdAt: number;
  createdByEmail: string;
  completedAt: number | null;
}

export function useInspections(collegeId?: string) {
  const [items, setItems] = useState<Inspection[]>([]);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    const base = collection(db, MIS_COLLECTIONS.inspections);
    const q = collegeId
      ? query(base, where("collegeId", "==", collegeId), orderBy("scheduledDate", "desc"))
      : query(base, orderBy("scheduledDate", "desc"));
    const unsub = onSnapshot(
      q,
      (snap) => {
        setItems(snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<Inspection, "id">) })));
        setLoading(false);
      },
      () => setLoading(false),
    );
    return () => unsub();
  }, [collegeId]);
  return { items, loading };
}

export async function scheduleInspection(
  collegeId: string,
  collegeName: string,
  scheduledDate: string,
  purpose: string,
): Promise<void> {
  const ref = await addDoc(collection(db, MIS_COLLECTIONS.inspections), {
    collegeId,
    collegeName,
    scheduledDate,
    purpose,
    status: "scheduled" as InspectionStatus,
    findings: "",
    createdAt: Date.now(),
    createdByEmail: currentEmail(),
    completedAt: null,
  });
  await logAction("inspection.schedule", collegeId, { purpose, inspectionId: ref.id });
}

export async function completeInspection(id: string, collegeId: string, findings: string): Promise<void> {
  await updateDoc(doc(db, MIS_COLLECTIONS.inspections, id), {
    status: "completed" as InspectionStatus,
    findings,
    completedAt: Date.now(),
  });
  await logAction("inspection.complete", collegeId, { inspectionId: id });
}

// ── Manually captured network snapshots, for trend charts ──────────────────
//
// There is no Cloud Functions plan behind this project (RUNBOOK.md), so
// there is no nightly scheduled capture — a directorate user triggers one.
// Immutable once written (see firestore.rules): a trend line that can be
// edited after the fact is not a trend line.

export interface HistorySnapshot {
  id: string;
  at: number;
  capturedByEmail: string;
  totals: {
    colleges: number;
    books: number;
    members: number;
    activeLoans: number;
    overdue: number;
  };
}

export function useSnapshotHistory() {
  const [items, setItems] = useState<HistorySnapshot[]>([]);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    const q = query(collection(db, MIS_COLLECTIONS.snapshotsHistory), orderBy("at", "asc"));
    const unsub = onSnapshot(
      q,
      (snap) => {
        setItems(snap.docs.map((d) => ({ id: d.id, ...(d.data() as Omit<HistorySnapshot, "id">) })));
        setLoading(false);
      },
      () => setLoading(false),
    );
    return () => unsub();
  }, []);
  return { items, loading };
}

export async function captureSnapshot(totals: HistorySnapshot["totals"]): Promise<void> {
  const user = auth.currentUser;
  const ref = await addDoc(collection(db, MIS_COLLECTIONS.snapshotsHistory), {
    at: Date.now(),
    capturedByUid: user?.uid || "",
    capturedByEmail: currentEmail(),
    totals,
  });
  await logAction("snapshot.capture", "network", { snapshotId: ref.id, totals });
}
