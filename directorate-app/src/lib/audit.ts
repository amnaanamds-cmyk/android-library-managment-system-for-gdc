// lib/audit.ts
//
// The directorate's own audit trail — every approval, removal, restoration
// and staff change made from this portal, recorded permanently.
//
// firestore.rules enforces two things this module exists to satisfy:
//   1. `byUid` must equal the caller's own auth uid — an entry can never be
//      forged in someone else's name, so logAction() always reads the
//      current user from Firebase Auth directly rather than trusting a
//      caller-supplied value.
//   2. update/delete are unconditionally false — this file has no update or
//      delete function on purpose. There is nothing here to expose.

"use client";

import { useEffect, useState } from "react";
import { addDoc, collection, onSnapshot, orderBy, query, limit as fsLimit } from "firebase/firestore";
import { db, auth } from "./firebase";
import { MIS_COLLECTIONS } from "./schema";

export type AuditAction =
  | "college.approve"
  | "college.hide"
  | "college.restore"
  | "staff.enrol"
  | "staff.remove"
  | "note.save"
  | "followup.create"
  | "followup.resolve"
  | "announcement.publish"
  | "announcement.retract"
  | "document.add"
  | "document.remove"
  | "inspection.schedule"
  | "inspection.complete"
  | "snapshot.capture";

export interface AuditEntry {
  id: string;
  action: AuditAction | string;
  target: string;
  meta: Record<string, unknown>;
  byUid: string;
  byEmail: string;
  at: number;
}

/** Append one entry. Never throws into the caller's UI flow on failure —
 *  a failed audit write must not block the underlying action from having
 *  already happened; it is logged to the console for anyone reviewing logs. */
export async function logAction(
  action: AuditAction | string,
  target: string,
  meta: Record<string, unknown> = {},
): Promise<void> {
  const user = auth.currentUser;
  if (!user) return;
  try {
    await addDoc(collection(db, MIS_COLLECTIONS.auditLog), {
      action,
      target,
      meta,
      byUid: user.uid,
      byEmail: user.email || "",
      at: Date.now(),
    });
  } catch (err) {
    // eslint-disable-next-line no-console
    console.error("Audit log write failed (the underlying action still happened):", err);
  }
}

export function useAuditLog(rows = 200) {
  const [entries, setEntries] = useState<AuditEntry[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const q = query(collection(db, MIS_COLLECTIONS.auditLog), orderBy("at", "desc"), fsLimit(rows));
    const unsub = onSnapshot(
      q,
      (snap) => {
        setEntries(
          snap.docs.map((d) => {
            const data = d.data();
            return {
              id: d.id,
              action: data.action || "",
              target: data.target || "",
              meta: data.meta || {},
              byUid: data.byUid || "",
              byEmail: data.byEmail || "",
              at: Number(data.at ?? 0),
            };
          }),
        );
        setLoading(false);
      },
      () => setLoading(false),
    );
    return () => unsub();
  }, [rows]);

  return { entries, loading };
}
