/**
 * Behavioural tests for firestore.rules.
 *
 * The central claim of the fix is that the OLD rules denied every Android/Web
 * write for an institution with no /colleges registry document (the state the
 * desktop onboarding flow leaves behind), and that the NEW rules allow that
 * college's own staff to write while still isolating tenants. These tests
 * exercise exactly that, plus the collections that previously had no rule.
 */
import {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} from "@firebase/rules-unit-testing";
import { doc, setDoc, getDoc, updateDoc, collection, getDocs } from "firebase/firestore";
import { readFileSync } from "node:fs";

const RULES = readFileSync(process.argv[2], "utf8");

const testEnv = await initializeTestEnvironment({
  projectId: "demo-nexlib",
  firestore: { rules: RULES, host: "127.0.0.1", port: 8080 },
});

// ── Seed: an institution created the way the DESKTOP app creates one, i.e.
//    with NO /colleges registry document. This is the exact state that made the
//    old isCollegeDirector() raise and deny everything.
await testEnv.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  await setDoc(doc(db, "institutions/GDC-ZIAM"), { name: "GDC Ziam", ownerUid: "owner1" });
  await setDoc(doc(db, "institutions/GDC-OTHER"), { name: "Other College" });
  // Staff profile with NO collegeIds field — the shape that made
  // `x in profile.collegeIds` raise.
  await setDoc(doc(db, "users/staff1"), { email: "s@x.edu", role: "staff", institutionId: "GDC-ZIAM" });
  await setDoc(doc(db, "users/owner1"), { email: "o@x.edu", role: "owner", institutionId: "GDC-ZIAM" });
  await setDoc(doc(db, "users/dir1"), { email: "d@x.edu", role: "director", institutionId: "" });
  await setDoc(doc(db, "users/outsider"), { email: "z@x.edu", role: "staff", institutionId: "GDC-OTHER" });
  await setDoc(doc(db, "institutions/GDC-ZIAM/books/b1"), { title: "Seed", deleted: false });
  await setDoc(doc(db, "directorate_index/GDC-ZIAM"), { institutionId: "GDC-ZIAM", booksCount: 1 });
});

const staff = testEnv.authenticatedContext("staff1").firestore();
const owner = testEnv.authenticatedContext("owner1").firestore();
const director = testEnv.authenticatedContext("dir1").firestore();
const outsider = testEnv.authenticatedContext("outsider").firestore();
const anon = testEnv.unauthenticatedContext().firestore();

let pass = 0, fail = 0;
async function check(name, fn) {
  try { await fn(); console.log(`  ok   ${name}`); pass++; }
  catch (e) { console.log(`  FAIL ${name}\n       ${String(e).split("\n")[0]}`); fail++; }
}

console.log("\n── The regression: staff writes to a desktop-created institution ──");
await check("staff can create a book (was denied: null.data on /colleges)", () =>
  assertSucceeds(setDoc(doc(staff, "institutions/GDC-ZIAM/books/b2"), { title: "New", deleted: false })));
await check("staff can update a book", () =>
  assertSucceeds(updateDoc(doc(staff, "institutions/GDC-ZIAM/books/b1"), { title: "Edited" })));
await check("staff can soft-delete a book", () =>
  assertSucceeds(updateDoc(doc(staff, "institutions/GDC-ZIAM/books/b1"), { deleted: true })));
await check("staff can write members", () =>
  assertSucceeds(setDoc(doc(staff, "institutions/GDC-ZIAM/members/m1"), { name: "A", deleted: false })));
await check("staff can write issued_books", () =>
  assertSucceeds(setDoc(doc(staff, "institutions/GDC-ZIAM/issued_books/i1"), { status: "Issued", deleted: false })));
await check("staff can write reservations", () =>
  assertSucceeds(setDoc(doc(staff, "institutions/GDC-ZIAM/reservations/r1"), { status: "Pending", deleted: false })));

console.log("\n── Collections that previously had NO rule (default deny) ──");
for (const c of ["audit_log", "visitor_log", "purchase_orders", "book_transfers", "ill_requests", "ebooks", "_sync"]) {
  await check(`staff can write ${c}`, () =>
    assertSucceeds(setDoc(doc(staff, `institutions/GDC-ZIAM/${c}/x1`), { v: 1 })));
}

console.log("\n── Tenant isolation still holds ──");
await check("outsider CANNOT write another college's books", () =>
  assertFails(setDoc(doc(outsider, "institutions/GDC-ZIAM/books/evil"), { title: "hack" })));
await check("outsider CANNOT read another college's members", () =>
  assertFails(getDoc(doc(outsider, "institutions/GDC-ZIAM/members/m1"))));
await check("outsider CANNOT read another college's loans", () =>
  assertFails(getDoc(doc(outsider, "institutions/GDC-ZIAM/issued_books/i1"))));
await check("outsider CANNOT write another college's audit log", () =>
  assertFails(setDoc(doc(outsider, "institutions/GDC-ZIAM/audit_log/evil"), { v: 1 })));
await check("anonymous CANNOT write books", () =>
  assertFails(setDoc(doc(anon, "institutions/GDC-ZIAM/books/evil"), { title: "hack" })));

console.log("\n── Public OPAC catalogue stays readable ──");
await check("anonymous CAN read the catalogue", () =>
  assertSucceeds(getDoc(doc(anon, "institutions/GDC-ZIAM/books/b1"))));

console.log("\n── Settings and audit stay admin-only (catch-all must not re-grant) ──");
await check("staff CANNOT write settings", () =>
  assertFails(setDoc(doc(staff, "institutions/GDC-ZIAM/settings/library_settings"), { fineRatePerDay: 0 })));
await check("owner CAN write settings", () =>
  assertSucceeds(setDoc(doc(owner, "institutions/GDC-ZIAM/settings/library_settings"), { fineRatePerDay: 5 })));
await check("staff CAN append to audit_log", () =>
  assertSucceeds(setDoc(doc(staff, "institutions/GDC-ZIAM/audit_log/a2"), { action: "x" })));
await check("staff CANNOT amend an audit entry", () =>
  assertFails(updateDoc(doc(staff, "institutions/GDC-ZIAM/audit_log/a2"), { action: "tampered" })));

console.log("\n── Directorate registry ──");
await check("director CAN read the whole registry", () =>
  assertSucceeds(getDocs(collection(director, "directorate_index"))));
await check("staff CAN publish their own college's snapshot", () =>
  assertSucceeds(setDoc(doc(staff, "directorate_index/GDC-ZIAM"), { booksCount: 42 }, { merge: true })));
await check("staff CANNOT publish another college's snapshot", () =>
  assertFails(setDoc(doc(staff, "directorate_index/GDC-OTHER"), { booksCount: 999 }, { merge: true })));
await check("anonymous CANNOT read the registry", () =>
  assertFails(getDocs(collection(anon, "directorate_index"))));

console.log("\n── Onboarding ──");
await check("signed-in user CAN create a new institution", () =>
  assertSucceeds(setDoc(doc(staff, "institutions/BRAND-NEW"), { name: "New", ownerUid: "staff1" })));
await check("user CANNOT overwrite an existing institution they don't own", () =>
  assertFails(setDoc(doc(outsider, "institutions/GDC-ZIAM"), { name: "Hijacked" })));
await check("any signed-in user CAN read institution metadata (invite codes)", () =>
  assertSucceeds(getDoc(doc(outsider, "institutions/GDC-ZIAM"))));

await testEnv.cleanup();
console.log(`\n${pass} passed, ${fail} failed`);
process.exit(fail === 0 ? 0 : 1);
