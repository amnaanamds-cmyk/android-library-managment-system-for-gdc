// lib/staff.ts
//
// The directorate staff directory and its access tiers.
//
// Every account that can sign in here already passed the role gate in
// auth-context.tsx (role in DIRECTOR_ROLES). What this module adds is a
// SECOND, narrower gate on top: which of those accounts may approve or
// remove a college, enrol other staff, or only look.
//
// The tier model is intentionally simple and mirrors firestore.rules exactly
// (see isDirectorateSuperAdmin() there) — an account with NO /directorate_staff
// record is super_admin by default. This is a bootstrap-safety choice, not
// laziness: every account that existed before staff tiers shipped, including
// the original director@nexlib.com, must keep working without a migration
// step. A record only ever NARROWS what an account can do.

"use client";

import { useEffect, useState } from "react";
import {
  collection,
  doc,
  onSnapshot,
  setDoc,
  deleteDoc,
  serverTimestamp,
} from "firebase/firestore";
import { db } from "./firebase";
import { useAuth } from "./auth-context";
import { MIS_COLLECTIONS } from "./schema";
import { logAction } from "./audit";

export type StaffTier = "super_admin" | "regional" | "analyst";

export const TIER_LABEL: Record<StaffTier, string> = {
  super_admin: "Super Admin",
  regional: "Regional Coordinator",
  analyst: "Analyst (read-only)",
};

export const TIER_DESCRIPTION: Record<StaffTier, string> = {
  super_admin: "Full access: approve or remove colleges, manage staff, all sections.",
  regional: "Approve or remove colleges within assigned districts. Cannot manage staff.",
  analyst: "Read every section. Cannot approve, remove, or change anything.",
};

export interface StaffRecord {
  uid: string;
  email: string;
  tier: StaffTier;
  /** Only meaningful for tier === "regional". Empty = no district restriction. */
  districts: string[];
  addedAt: number;
  addedByEmail: string;
}

/** Live directory of every enrolled staff record (not every directorate
 *  account — an account with no record here is still a full super_admin,
 *  it simply has nothing to show in the directory beyond "default access"). */
export function useDirectorateStaff() {
  const { user } = useAuth();
  const [staff, setStaff] = useState<StaffRecord[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!user) {
      setStaff([]);
      setLoading(false);
      return;
    }
    const unsub = onSnapshot(
      collection(db, MIS_COLLECTIONS.staff),
      (snap) => {
        setStaff(
          snap.docs.map((d) => {
            const data = d.data();
            return {
              uid: d.id,
              email: data.email || "",
              tier: (data.tier as StaffTier) || "analyst",
              districts: Array.isArray(data.districts) ? data.districts : [],
              addedAt: Number(data.addedAt ?? 0),
              addedByEmail: data.addedByEmail || "",
            };
          }),
        );
        setLoading(false);
      },
      () => setLoading(false),
    );
    return () => unsub();
  }, [user]);

  return { staff, loading };
}

/** This signed-in user's own tier — 'super_admin' when they have no
 *  enrollment record, matching the rules' bootstrap default exactly. */
export function useMyTier(): StaffTier {
  const { profile } = useAuth();
  const { staff } = useDirectorateStaff();
  if (!profile) return "analyst";
  const record = staff.find((s) => s.uid === profile.uid);
  return record?.tier ?? "super_admin";
}

export function tierCan(tier: StaffTier, action: "approve" | "manageStaff" | "write") {
  if (tier === "super_admin") return true;
  if (tier === "regional") return action === "approve" || action === "write";
  return false; // analyst: read-only, full stop
}

export async function enrolStaff(
  targetUid: string,
  targetEmail: string,
  tier: StaffTier,
  districts: string[],
  byEmail: string,
): Promise<void> {
  await setDoc(doc(db, MIS_COLLECTIONS.staff, targetUid), {
    email: targetEmail,
    tier,
    districts,
    addedAt: Date.now(),
    addedByEmail: byEmail,
  });
  await logAction("staff.enrol", targetUid, { email: targetEmail, tier });
}

export async function removeStaffRecord(targetUid: string): Promise<void> {
  await deleteDoc(doc(db, MIS_COLLECTIONS.staff, targetUid));
  await logAction("staff.remove", targetUid, {});
}

// serverTimestamp import kept for callers that want a Firestore-native
// timestamp instead of Date.now(); unused here but part of this module's
// public surface for the staff detail views.
export { serverTimestamp };
