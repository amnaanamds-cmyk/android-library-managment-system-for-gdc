/**
 * Auth custom claims.
 *
 * firestore.rules decides access from `request.auth.token.institutionId` and
 * `request.auth.token.role`. Claims live inside the ID token, so a rule that
 * reads them costs nothing; the profile-document fallback the rules still carry
 * costs one document read per evaluation, on every request, forever. This
 * module is what lets that fallback eventually be deleted.
 *
 * The profile document at /users/{uid} remains the source of truth — it is what
 * an administrator edits — and claims are a derived cache of it. Rules prevent a
 * user changing their own role or institutionId, so mirroring that document
 * into claims cannot escalate anyone.
 */

import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";

import { REGION, ROLES, ROOT, normaliseRole, type Role } from "./config";

export interface NexlibClaims {
  role: Role;
  /** Absent for directorate accounts, which are deliberately cross-tenant. */
  institutionId?: string;
}

/** Build the claims an account should carry, given its profile document. */
export function claimsForProfile(data: FirebaseFirestore.DocumentData): NexlibClaims {
  const role = normaliseRole(data.role);

  // Directorate oversight is province-wide by design and must NOT be pinned to
  // an institution: an institutionId claim would scope its reads to one college
  // and silently empty the director dashboard.
  if (role === ROLES.directorate) return { role };

  const institutionId =
    (typeof data.institutionId === "string" && data.institutionId) ||
    (typeof data.collegeId === "string" && data.collegeId) ||
    "";

  return institutionId ? { role, institutionId } : { role };
}

function claimsEqual(a: NexlibClaims, b: Partial<NexlibClaims> | undefined): boolean {
  if (!b) return false;
  return a.role === b.role && (a.institutionId ?? "") === (b.institutionId ?? "");
}

/**
 * Apply claims to an account, and return whether anything actually changed.
 *
 * Writing identical claims is not free: it invalidates the user's refresh token
 * state and forces every one of their devices to re-fetch a token. The
 * equality check keeps a no-op profile edit from logging everyone out of a
 * college at once.
 */
export async function applyClaims(uid: string, claims: NexlibClaims): Promise<boolean> {
  const user = await getAuth().getUser(uid);
  const existing = user.customClaims as Partial<NexlibClaims> | undefined;
  if (claimsEqual(claims, existing)) return false;

  // Preserve any claims another system owns; only our two keys are replaced.
  await getAuth().setCustomUserClaims(uid, {
    ...(existing ?? {}),
    role: claims.role,
    institutionId: claims.institutionId ?? null,
  });
  logger.info("Claims updated", { uid, ...claims });
  return true;
}

/**
 * Mirror /users/{uid} into custom claims whenever the profile changes.
 *
 * The `claimsSyncedAt` write-back at the end is what tells a signed-in client
 * its token is stale, so it can call getIdToken(true) rather than waiting up to
 * an hour for natural refresh. That write re-triggers this function, so the
 * guard below is load-bearing: without it this is an infinite loop that bills
 * per invocation.
 */
export const syncUserClaims = onDocumentWritten(
  { document: `${ROOT.users}/{uid}`, region: REGION },
  async (event) => {
    const uid = event.params.uid;
    const after = event.data?.after;

    if (!after?.exists) {
      // Profile deleted. Strip claims so a re-created account cannot inherit
      // the previous holder's institution.
      try {
        await getAuth().setCustomUserClaims(uid, null);
      } catch (err) {
        logger.warn("Could not clear claims for deleted profile", { uid, err });
      }
      return;
    }

    const data = after.data() ?? {};
    const before = event.data?.before?.exists ? event.data.before.data() ?? {} : {};

    // Ignore our own write-back. Compare only the fields claims derive from:
    // if neither changed there is nothing to mint, and re-minting would loop.
    const roleUnchanged = normaliseRole(before.role) === normaliseRole(data.role);
    const institutionUnchanged =
      (before.institutionId ?? before.collegeId ?? "") ===
      (data.institutionId ?? data.collegeId ?? "");
    if (event.data?.before?.exists && roleUnchanged && institutionUnchanged) return;

    const claims = claimsForProfile(data);
    let changed = false;
    try {
      changed = await applyClaims(uid, claims);
    } catch (err) {
      // An orphaned profile (no matching auth account) is not an error worth
      // retrying — it happens when a profile is seeded before signup.
      logger.warn("Could not apply claims", { uid, err });
      return;
    }

    if (!changed) return;

    await getFirestore()
      .doc(`${ROOT.users}/${uid}`)
      .set({ claimsSyncedAt: FieldValue.serverTimestamp() }, { merge: true });
  },
);

/**
 * Mint claims for the caller from their own profile document.
 *
 * The migration path for accounts that predate claims: a client calls this once
 * after sign-in, then refreshes its token. Safe to expose because it grants
 * exactly what the caller's profile document already says — it cannot be used
 * to request a role.
 */
export const refreshMyClaims = onCall({ region: REGION }, async (request) => {
  const uid = request.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Sign in first.");

  const snap = await getFirestore().doc(`${ROOT.users}/${uid}`).get();
  if (!snap.exists) {
    throw new HttpsError(
      "failed-precondition",
      "No profile document for this account. Complete onboarding first.",
    );
  }

  const claims = claimsForProfile(snap.data() ?? {});
  await applyClaims(uid, claims);

  // The caller must refresh its ID token for these to take effect.
  return { ...claims, tokenRefreshRequired: true };
});

/**
 * Directorate-only: set another account's role and institution.
 *
 * Writes the profile document rather than the claims directly, so the profile
 * stays the source of truth and syncUserClaims does the minting. That keeps a
 * single path for claims and avoids the two drifting.
 */
export const setUserRole = onCall({ region: REGION }, async (request) => {
  const callerRole = normaliseRole(request.auth?.token?.role);
  if (callerRole !== ROLES.directorate) {
    throw new HttpsError("permission-denied", "Directorate access required.");
  }

  const { uid, role, institutionId } = (request.data ?? {}) as {
    uid?: string;
    role?: string;
    institutionId?: string;
  };
  if (!uid || !role) throw new HttpsError("invalid-argument", "uid and role are required.");

  const canonical = normaliseRole(role);
  if (canonical !== ROLES.directorate && !institutionId) {
    throw new HttpsError(
      "invalid-argument",
      "institutionId is required for every role except directorate.",
    );
  }

  await getFirestore()
    .doc(`${ROOT.users}/${uid}`)
    .set(
      {
        role: canonical,
        institutionId: canonical === ROLES.directorate ? "" : institutionId,
        updatedAt: FieldValue.serverTimestamp(),
        updatedBy: request.auth?.uid ?? "",
      },
      { merge: true },
    );

  return { uid, role: canonical, institutionId: institutionId ?? null };
});
