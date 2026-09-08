/**
 * End-to-end test for the KPK spec, section 9.7:
 *
 *   create 2 test institutions
 *     -> issue a book (as the desktop client would)
 *     -> confirm another client sees it
 *     -> confirm the director dashboard reflects it
 *
 * Runs against the Auth and Firestore emulators, exercising the real
 * registration, claims and rollup code. What it is really guarding is the
 * boundary between the two colleges: every count the directorate sees must come
 * from exactly one institution's data, and nothing must leak across.
 *
 * Run:  npm --prefix functions run test:e2e
 */
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getAuth } from "firebase-admin/auth";

process.env.GCLOUD_PROJECT ??= "demo-nexlib";
process.env.FIRESTORE_EMULATOR_HOST ??= "127.0.0.1:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST ??= "127.0.0.1:9099";

initializeApp({ projectId: process.env.GCLOUD_PROJECT });
const db = getFirestore();
const auth = getAuth();

const { createInstitution } = await import("../lib/registration.js");
const { rebuildAllSummaries } = await import("../lib/summary.js");
const { claimsForProfile } = await import("../lib/claims.js");

let pass = 0;
let fail = 0;

function check(label, condition, detail = "") {
  if (condition) {
    console.log(`  ok   ${label}`);
    pass++;
  } else {
    console.log(`  FAIL ${label}${detail ? ` — ${detail}` : ""}`);
    fail++;
  }
}

async function wipe() {
  for (const root of ["institutions", "institution_registry", "directorate_summary", "users"]) {
    const docs = await db.collection(root).listDocuments();
    await Promise.all(docs.map((d) => db.recursiveDelete(d)));
  }
  const users = await auth.listUsers(1000);
  if (users.users.length) {
    await auth.deleteUsers(users.users.map((u) => u.uid));
  }
}

await wipe();

// ── 1. Register two institutions ─────────────────────────────────────────────

console.log("\n── Registering two institutions ──");

const DIRECTORATE_UID = "directorate-officer";
await auth.createUser({ uid: DIRECTORATE_UID, email: "dte@hed.gkp.pk", password: "test1234" });

const a = await createInstitution(
  {
    collegeName: "Government Degree College Ziam Sherpao",
    district: "Charsadda",
    region: "Peshawar",
    adminName: "Librarian A",
    adminEmail: "admin.a@gdc.edu.pk",
    autoApprove: true,
  },
  DIRECTORATE_UID,
  true,
);

const b = await createInstitution(
  {
    collegeName: "Government Degree College Mingora",
    district: "Swat",
    region: "Malakand",
    adminName: "Librarian B",
    adminEmail: "admin.b@gdc.edu.pk",
  },
  DIRECTORATE_UID,
  true, // directorate caller, but no autoApprove -> must still land pending
);

check("institution A gets a readable id", a.institutionId.startsWith("GDC-"), a.institutionId);
check("the id carries the district", a.institutionId.includes("CHARSADDA"), a.institutionId);
check("two colleges get distinct ids", a.institutionId !== b.institutionId);
check("A was auto-approved by a directorate caller", a.status === "active", a.status);
check("B landed pending without autoApprove", b.status === "pending", b.status);
check("a temporary password is returned once", typeof a.temporaryPassword === "string");

// Registration must create all four things, or the college half-exists.
const instDoc = await db.doc(`institutions/${a.institutionId}`).get();
const metaDoc = await db.doc(`institutions/${a.institutionId}/meta/profile`).get();
const regDoc = await db.doc(`institution_registry/${a.institutionId}`).get();
const userDoc = await db.doc(`users/${a.adminUid}`).get();
check("the institution document exists", instDoc.exists);
check("the meta profile exists", metaDoc.exists);
check("the registry entry exists", regDoc.exists);
check("the admin profile exists", userDoc.exists);

const adminUser = await auth.getUser(a.adminUid);
check("the admin auth account was created", !!adminUser);
check("its claims scope it to its own institution",
  adminUser.customClaims?.institutionId === a.institutionId,
  JSON.stringify(adminUser.customClaims));
check("its claims give it the admin role", adminUser.customClaims?.role === "admin");

// A directorate account must NOT carry an institutionId, or its reads would be
// scoped to one college and the dashboard would silently show nothing.
check("a directorate profile mints no institutionId",
  claimsForProfile({ role: "directorate", institutionId: "GDC-SOMETHING" }).institutionId === undefined);

// ── 2. Issue a book, the way the desktop client writes it ────────────────────

console.log("\n── A loan recorded at college A ──");

const past = "2000-01-01";
const future = "2999-12-31";

async function seedLoan(institutionId, { books, members, issued, overdue }) {
  const base = `institutions/${institutionId}`;
  const batch = db.batch();
  for (let i = 0; i < books; i++) {
    batch.set(db.doc(`${base}/books/bk${i}`), {
      syncId: `bk${i}`, title: `Book ${i}`, deleted: false, isDigital: false,
      status: "Available", lastUpdated: Date.now(),
    });
  }
  for (let i = 0; i < members; i++) {
    batch.set(db.doc(`${base}/members/mb${i}`), {
      syncId: `mb${i}`, name: `Member ${i}`, deleted: false, lastUpdated: Date.now(),
    });
  }
  for (let i = 0; i < issued; i++) {
    batch.set(db.doc(`${base}/issued_books/is${i}`), {
      syncId: `is${i}`, status: "Issued", deleted: false,
      dueDate: i < overdue ? past : future, lastUpdated: Date.now(),
    });
  }
  await batch.commit();
}

await seedLoan(a.institutionId, { books: 12, members: 5, issued: 3, overdue: 1 });
await seedLoan(b.institutionId, { books: 40, members: 9, issued: 7, overdue: 4 });

// Any other client reads the same tenant path — this is the "Mobile reflects
// it" step. There is one copy of the data, not a per-platform copy.
const asSeenByAnotherClient = await db
  .collection(`institutions/${a.institutionId}/issued_books`)
  .where("deleted", "==", false)
  .where("status", "==", "Issued")
  .get();
check("another client sees the loan on the same tenant path",
  asSeenByAnotherClient.size === 3, `${asSeenByAnotherClient.size} loans`);

// ── 3. The director dashboard ────────────────────────────────────────────────

console.log("\n── The directorate rollup ──");

let result = await rebuildAllSummaries();
check("only the approved college is summarised", result.institutions === 1, JSON.stringify(result));

let summaryA = (await db.doc(`directorate_summary/${a.institutionId}`).get()).data();
check("A's books reached the dashboard", summaryA.totalBooks === 12, `${summaryA?.totalBooks}`);
check("A's members reached the dashboard", summaryA.members === 5, `${summaryA?.members}`);
check("A's active loans reached the dashboard", summaryA.issued === 3, `${summaryA?.issued}`);
check("A's overdue count is right", summaryA.overdue === 1, `${summaryA?.overdue}`);
check("A's district came through for rollups", summaryA.district === "Charsadda");
check("B has no summary while pending",
  !(await db.doc(`directorate_summary/${b.institutionId}`).get()).exists);

// ── 4. Approve B and confirm both report, without cross-contamination ────────

console.log("\n── Approving the second college ──");

await db.doc(`institution_registry/${b.institutionId}`).set({ status: "active" }, { merge: true });
result = await rebuildAllSummaries();
check("both colleges now report", result.institutions === 2, JSON.stringify(result));

const summaryB = (await db.doc(`directorate_summary/${b.institutionId}`).get()).data();
summaryA = (await db.doc(`directorate_summary/${a.institutionId}`).get()).data();

check("B's figures are B's own", summaryB.totalBooks === 40 && summaryB.issued === 7,
  `${summaryB?.totalBooks} books, ${summaryB?.issued} loans`);
check("A's figures did not absorb B's", summaryA.totalBooks === 12 && summaryA.issued === 3,
  `${summaryA?.totalBooks} books, ${summaryA?.issued} loans`);
check("the two colleges are in different districts",
  summaryA.district === "Charsadda" && summaryB.district === "Swat");

// Province-wide totals are what the dashboard renders at the top.
const all = await db.collection("directorate_summary").get();
const totals = all.docs.reduce(
  (acc, d) => {
    const s = d.data();
    return {
      books: acc.books + s.totalBooks,
      members: acc.members + s.members,
      issued: acc.issued + s.issued,
      overdue: acc.overdue + s.overdue,
    };
  },
  { books: 0, members: 0, issued: 0, overdue: 0 },
);
check("network totals sum both colleges",
  totals.books === 52 && totals.members === 14 && totals.issued === 10 && totals.overdue === 5,
  JSON.stringify(totals));

// ── 5. Suspension stops reporting ────────────────────────────────────────────

console.log("\n── Suspending a college ──");
await db.doc(`institution_registry/${a.institutionId}`).set({ status: "suspended" }, { merge: true });
result = await rebuildAllSummaries();
check("a suspended college drops out of the walk", result.institutions === 1);

await wipe();
console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail === 0 ? 0 : 1);
