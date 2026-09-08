/**
 * Integration tests for the directorate rollup, against the Firestore emulator.
 *
 * These exercise the real query paths — COUNT aggregations, the members/students
 * and issued_books/issues aliases, soft-delete filtering, and the paginated
 * registry walk — because every one of those fails silently. A wrong count does
 * not throw; it just puts a smaller number on the director's dashboard, and
 * nobody can tell by looking whether 1,204 books is right.
 *
 * Run:  npm --prefix functions test
 */
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";

process.env.GCLOUD_PROJECT ??= "demo-nexlib";
process.env.FIRESTORE_EMULATOR_HOST ??= "127.0.0.1:8080";

initializeApp({ projectId: process.env.GCLOUD_PROJECT });
const db = getFirestore();

const { summariseInstitution, rebuildAllSummaries } = await import("../lib/summary.js");

let pass = 0;
let fail = 0;

function eq(actual, expected, label) {
  if (actual === expected) {
    console.log(`  ok   ${label} = ${actual}`);
    pass++;
  } else {
    console.log(`  FAIL ${label}: expected ${expected}, got ${actual}`);
    fail++;
  }
}

const today = new Date().toISOString().slice(0, 10);
const past = "2000-01-01";
const future = "2999-12-31";

async function wipe() {
  for (const root of ["institutions", "institution_registry", "directorate_summary"]) {
    const docs = await db.collection(root).listDocuments();
    await Promise.all(docs.map((d) => db.recursiveDelete(d)));
  }
}

/** Seed a college using the collection names this deployment actually writes. */
async function seedZiam() {
  const id = "GDC-ZIAM";
  const base = `institutions/${id}`;
  const batch = db.batch();

  batch.set(db.doc(`institution_registry/${id}`), {
    institutionId: id, name: "GDC Ziam Sherpao", district: "Charsadda",
    region: "Peshawar", status: "active", lastSyncAt: 1_700_000_000_000,
  });

  // 4 live printed, 2 live digital, 1 soft-deleted, 1 with no envelope at all.
  for (let i = 0; i < 4; i++) {
    batch.set(db.doc(`${base}/books/p${i}`), { title: `P${i}`, deleted: false, isDigital: false });
  }
  for (let i = 0; i < 2; i++) {
    batch.set(db.doc(`${base}/books/d${i}`), { title: `D${i}`, deleted: false, isDigital: true });
  }
  batch.set(db.doc(`${base}/books/gone`), { title: "Gone", deleted: true, isDigital: false });
  batch.set(db.doc(`${base}/books/legacy`), { title: "Pre-envelope" }); // no `deleted`

  // 3 live members, 1 deleted.
  for (let i = 0; i < 3; i++) {
    batch.set(db.doc(`${base}/members/m${i}`), { name: `M${i}`, deleted: false });
  }
  batch.set(db.doc(`${base}/members/mdel`), { name: "Removed", deleted: true });

  // 2 open loans (one overdue), 1 returned, 1 deleted-but-open.
  batch.set(db.doc(`${base}/issued_books/i1`), { status: "Issued", deleted: false, dueDate: future });
  batch.set(db.doc(`${base}/issued_books/i2`), { status: "Issued", deleted: false, dueDate: past });
  batch.set(db.doc(`${base}/issued_books/i3`), { status: "Returned", deleted: false, dueDate: past });
  batch.set(db.doc(`${base}/issued_books/i4`), { status: "Issued", deleted: true, dueDate: past });

  batch.set(db.doc(`${base}/reservations/r1`), { status: "Pending", deleted: false });
  batch.set(db.doc(`${base}/reservations/r2`), { status: "Fulfilled", deleted: false });

  await batch.commit();
  return id;
}

/** Seed a college using the KPK spec's collection names instead. */
async function seedSpecNamed() {
  const id = "GDC-SPEC";
  const base = `institutions/${id}`;
  const batch = db.batch();
  batch.set(db.doc(`institution_registry/${id}`), {
    institutionId: id, name: "GDC Spec Names", district: "Swat",
    region: "Malakand", status: "active",
  });
  batch.set(db.doc(`${base}/books/b1`), { title: "B1", deleted: false, isDigital: false });
  for (let i = 0; i < 5; i++) {
    batch.set(db.doc(`${base}/students/s${i}`), { name: `S${i}`, deleted: false });
  }
  batch.set(db.doc(`${base}/issues/x1`), { status: "Issued", deleted: false, dueDate: past });
  batch.set(db.doc(`${base}/issues/x2`), { status: "Issued", deleted: false, dueDate: future });
  await batch.commit();
  return id;
}

console.log("\n── Rollup of a college using the deployed collection names ──");
await wipe();
const ziam = await seedZiam();
const reg = (await db.doc(`institution_registry/${ziam}`).get()).data();
const s = await summariseInstitution(db, ziam, reg);

eq(s.totalBooks, 4, "printed books excludes digital and deleted");
eq(s.totalEbooks, 2, "digital titles counted separately");
eq(s.members, 3, "members excludes soft-deleted");
eq(s.issued, 2, "open loans excludes returned and deleted");
eq(s.overdue, 1, "overdue is open loans past their due date");
eq(s.reservations, 1, "reservations counts only pending");
eq(s.district, "Charsadda", "district carried from the registry");
eq(s.recordsMissingSyncEnvelope, 1, "pre-envelope record is reported, not hidden");

console.log("\n── The same rollup with the KPK spec's collection names ──");
const spec = await seedSpecNamed();
const specReg = (await db.doc(`institution_registry/${spec}`).get()).data();
const s2 = await summariseInstitution(db, spec, specReg);
eq(s2.members, 5, "students/ counted as patron records");
eq(s2.issued, 2, "issues/ counted as loans");
eq(s2.overdue, 1, "overdue works across the alias");

console.log("\n── Paginated walk writes one summary per active institution ──");
await db.doc("institution_registry/GDC-PENDING").set({
  institutionId: "GDC-PENDING", name: "Not yet approved", district: "Dir", status: "pending",
});
const result = await rebuildAllSummaries();
eq(result.institutions, 2, "pending institutions are skipped");
eq(result.written, 2, "one summary written per active institution");

const written = await db.collection("directorate_summary").get();
eq(written.size, 2, "summary collection size");
eq(
  (await db.doc(`directorate_summary/${ziam}`).get()).data().totalBooks,
  4,
  "persisted summary matches computed",
);
eq(
  (await db.doc("directorate_summary/GDC-PENDING").get()).exists,
  false,
  "unapproved college has no summary row",
);

console.log("\n── A suspended college stops reporting ──");
await db.doc(`institution_registry/${spec}`).set({ status: "suspended" }, { merge: true });
const after = await rebuildAllSummaries();
eq(after.institutions, 1, "suspended college drops out of the walk");

await wipe();
console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail === 0 ? 0 : 1);
