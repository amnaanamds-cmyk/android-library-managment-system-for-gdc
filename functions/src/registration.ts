/**
 * Institution onboarding (spec section 3).
 *
 * Manual creation does not scale past a handful of colleges, and creating one
 * by hand means touching four places consistently — auth account, claims,
 * institution document, registry entry. Missing any one leaves a college that
 * half-exists: it can sign in but reports nothing to the directorate, or it
 * appears on the dashboard but nobody can log into it.
 *
 * `registerInstitution` does all four atomically-ish and always lands the
 * college in `status: "pending"`. Approval is a separate, directorate-only
 * step, and that gate is the control point that stops unmanaged growth before
 * the province is ready for it.
 */

import { HttpsError, onCall } from "firebase-functions/v2/https";
import { logger } from "firebase-functions/v2";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, FieldValue } from "firebase-admin/firestore";

import { applyClaims } from "./claims";
import { REGION, ROLES, ROOT, STATUS, normaliseRole } from "./config";

// ─── Identifiers ─────────────────────────────────────────────────────────────

/**
 * Derive a readable institution id, e.g. "GDC-ZIAM-SHERPAO-CHARSADDA".
 *
 * Readable ids matter operationally: they appear in support tickets, in the
 * desktop .env, and in every sync log line. A random id would make every one of
 * those unreadable.
 */
function slugify(collegeName: string, district: string): string {
  const clean = (s: string) =>
    s
      .normalize("NFKD")
      .replace(/[^\w\s-]/g, "")
      .trim()
      .replace(/[\s_]+/g, "-")
      .replace(/-+/g, "-")
      .toUpperCase();

  const name = clean(collegeName).replace(/^GOVERNMENT-DEGREE-COLLEGE-?/, "GDC-");
  const base = name.startsWith("GDC") ? name : `GDC-${name}`;
  const withDistrict = district ? `${base}-${clean(district)}` : base;
  return withDistrict.slice(0, 60).replace(/-+$/, "");
}

/** Six-digit invite code used by the QR/code join flow on desktop and Android. */
function inviteCode(): string {
  return String(Math.floor(100000 + Math.random() * 900000));
}

/**
 * Reserve an unused id. Two colleges with similar names in the same district
 * would otherwise collide and the second would silently overwrite the first.
 */
async function reserveId(base: string): Promise<string> {
  const db = getFirestore();
  for (let n = 0; n < 25; n++) {
    const candidate = n === 0 ? base : `${base}-${n + 1}`;
    const [inst, reg] = await Promise.all([
      db.doc(`${ROOT.institutions}/${candidate}`).get(),
      db.doc(`${ROOT.registry}/${candidate}`).get(),
    ]);
    if (!inst.exists && !reg.exists) return candidate;
  }
  throw new HttpsError("resource-exhausted", `Could not allocate an id from "${base}".`);
}

// ─── Input ───────────────────────────────────────────────────────────────────

interface RegisterInput {
  collegeName?: string;
  district?: string;
  region?: string;
  address?: string;
  phone?: string;
  adminName?: string;
  adminEmail?: string;
  adminPhone?: string;
  /** Optional explicit id, for migrating a college that already has one. */
  institutionId?: string;
}

function requireText(value: unknown, field: string, max = 200): string {
  if (typeof value !== "string" || !value.trim()) {
    throw new HttpsError("invalid-argument", `${field} is required.`);
  }
  const trimmed = value.trim();
  if (trimmed.length > max) {
    throw new HttpsError("invalid-argument", `${field} must be ${max} characters or fewer.`);
  }
  return trimmed;
}

/**
 * Create an institution. The whole of registration, minus the callable wrapper.
 *
 * Split out from the callable so the end-to-end test can drive it directly
 * against the emulators rather than only through an authenticated HTTPS call.
 */
export async function createInstitution(
  input: RegisterInput & { autoApprove?: boolean },
  callerUid: string,
  callerIsDirectorate: boolean,
) {

  const collegeName = requireText(input.collegeName, "collegeName");
  const district = requireText(input.district, "district", 80);
  const adminName = requireText(input.adminName, "adminName", 120);
  const adminEmail = requireText(input.adminEmail, "adminEmail", 200).toLowerCase();
  const adminPhone = typeof input.adminPhone === "string" ? input.adminPhone.trim() : "";

  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(adminEmail)) {
    throw new HttpsError("invalid-argument", "adminEmail is not a valid email address.");
  }

  const status =
    callerIsDirectorate && input.autoApprove === true ? STATUS.active : STATUS.pending;

  const db = getFirestore();
  const institutionId = input.institutionId
    ? await reserveId(slugify(input.institutionId, ""))
    : await reserveId(slugify(collegeName, district));

  // ── The admin account ─────────────────────────────────────────────────────
  //
  // Reuse an existing account with this email rather than failing: a librarian
  // who already has a student account at another college must not be blocked
  // from being made an admin here, and creating a duplicate auth user for the
  // same email is impossible anyway.
  let adminUid: string;
  let createdAccount = false;
  let temporaryPassword: string | null = null;

  try {
    adminUid = (await getAuth().getUserByEmail(adminEmail)).uid;
  } catch {
    // 32 hex chars from a CSPRNG. Delivered once in the response and never
    // stored — a password persisted in Firestore would be readable by anyone
    // who could read the registry.
    temporaryPassword = [...crypto.getRandomValues(new Uint8Array(16))]
      .map((b) => b.toString(16).padStart(2, "0"))
      .join("");
    const created = await getAuth().createUser({
      email: adminEmail,
      emailVerified: false,
      password: temporaryPassword,
      displayName: adminName,
      ...(adminPhone ? {} : {}),
    });
    adminUid = created.uid;
    createdAccount = true;
  }

  const now = FieldValue.serverTimestamp();
  const code = inviteCode();

  // ── The four documents, written together ──────────────────────────────────
  //
  // A batch so a college is never half-created. If this fails the caller can
  // retry with the same input and get a fresh id; nothing is left behind that
  // would let them sign in to a college the directorate has not seen.
  const batch = db.batch();

  batch.set(db.doc(`${ROOT.institutions}/${institutionId}`), {
    institutionId,
    name: collegeName,
    district,
    region: input.region?.trim() ?? "",
    address: input.address?.trim() ?? "",
    phone: input.phone?.trim() ?? "",
    contactEmail: adminEmail,
    inviteCode: code,
    ownerUid: adminUid,
    status,
    createdAt: now,
    createdBy: callerUid,
  });

  batch.set(db.doc(`${ROOT.institutions}/${institutionId}/meta/profile`), {
    institutionId,
    name: collegeName,
    district,
    region: input.region?.trim() ?? "",
    address: input.address?.trim() ?? "",
    phone: input.phone?.trim() ?? "",
    contactEmail: adminEmail,
    logoUrl: "",
    tier: "standard",
    createdAt: now,
  });

  batch.set(db.doc(`${ROOT.registry}/${institutionId}`), {
    institutionId,
    name: collegeName,
    district,
    // Populated now even though district rollups are the only current consumer,
    // so adding regional rollups later needs no schema migration (spec 8).
    region: input.region?.trim() ?? "",
    status,
    ownerUid: adminUid,
    contactEmail: adminEmail,
    adminName,
    adminPhone,
    createdAt: now,
    createdBy: callerUid,
    approvedAt: status === STATUS.active ? now : null,
  });

  batch.set(
    db.doc(`${ROOT.users}/${adminUid}`),
    {
      uid: adminUid,
      email: adminEmail,
      displayName: adminName,
      phone: adminPhone,
      role: ROLES.admin,
      institutionId,
      createdAt: now,
    },
    { merge: true },
  );

  await batch.commit();

  // Claims are minted here as well as by the syncUserClaims trigger. The
  // trigger is eventually consistent and the new admin will try to sign in
  // immediately; waiting on it is what makes a fresh registration look broken.
  try {
    await applyClaims(adminUid, { role: ROLES.admin, institutionId });
  } catch (err) {
    logger.error("Institution created but claims not minted", { institutionId, adminUid, err });
  }

  logger.info("Institution registered", { institutionId, district, status, adminUid });

  return {
    institutionId,
    name: collegeName,
    district,
    status,
    inviteCode: code,
    adminUid,
    adminEmail,
    createdAccount,
    // Present only when a brand-new account was created. Deliver it to the
    // college over a trusted channel and have them change it on first sign-in.
    temporaryPassword,
  };
}

/**
 * Register a new institution.
 *
 * Callable by any signed-in user — the college always lands in `pending`, so an
 * unapproved registration grants nothing until the directorate approves it. A
 * directorate caller may pass `autoApprove` to skip the queue.
 */
export const registerInstitution = onCall({ region: REGION }, async (request) => {
  const callerUid = request.auth?.uid;
  if (!callerUid) {
    throw new HttpsError("unauthenticated", "Sign in before registering an institution.");
  }
  return createInstitution(
    (request.data ?? {}) as RegisterInput & { autoApprove?: boolean },
    callerUid,
    normaliseRole(request.auth?.token?.role) === ROLES.directorate,
  );
});

/**
 * Approve, suspend or re-activate an institution. Directorate only.
 *
 * `status` is what the summary function pages over, so this is also the switch
 * that starts and stops a college's figures reaching the director dashboard.
 */
export const setInstitutionStatus = onCall({ region: REGION }, async (request) => {
  if (normaliseRole(request.auth?.token?.role) !== ROLES.directorate) {
    throw new HttpsError("permission-denied", "Directorate access required.");
  }

  const { institutionId, status } = (request.data ?? {}) as {
    institutionId?: string;
    status?: string;
  };
  if (!institutionId) throw new HttpsError("invalid-argument", "institutionId is required.");
  if (status !== STATUS.active && status !== STATUS.pending && status !== STATUS.suspended) {
    throw new HttpsError(
      "invalid-argument",
      `status must be one of ${STATUS.pending}, ${STATUS.active}, ${STATUS.suspended}.`,
    );
  }

  const db = getFirestore();
  const ref = db.doc(`${ROOT.registry}/${institutionId}`);
  if (!(await ref.get()).exists) {
    throw new HttpsError("not-found", `No institution registered as "${institutionId}".`);
  }

  const now = FieldValue.serverTimestamp();
  const batch = db.batch();
  batch.set(
    ref,
    {
      status,
      approvedAt: status === STATUS.active ? now : null,
      statusChangedAt: now,
      statusChangedBy: request.auth?.uid ?? "",
    },
    { merge: true },
  );
  // Mirrored onto the institution document so clients can see their own status
  // without being able to read the registry's approval fields.
  batch.set(db.doc(`${ROOT.institutions}/${institutionId}`), { status }, { merge: true });
  await batch.commit();

  logger.info("Institution status changed", { institutionId, status });
  return { institutionId, status };
});
