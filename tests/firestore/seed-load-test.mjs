/**
 * Seeds the LOCAL Firestore + Auth emulators with a synthetic 300-college
 * directorate network, so the MIS portal can be scale-tested end to end
 * without touching the real nexlib-e7970 project or writing a single real
 * document to it.
 *
 * Requires an already-running emulator pair (see README.md in this folder):
 *   firebase emulators:start --only firestore,auth --project nexlib-e7970
 *
 * Run:
 *   node seed-load-test.mjs
 *
 * The colleges, accounts and edge cases this writes are deliberately not
 * uniform — a flat "300 identical rows" dataset would prove the UI can
 * render a table, not that the portal's actual features (alerts, benchmarking,
 * the district rollup, the union search cap) behave correctly against the
 * irregular data a real network produces.
 */
import { initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { doc, setDoc, collection } from "firebase/firestore";
import { initializeApp } from "firebase/app";
import { getAuth, connectAuthEmulator, createUserWithEmailAndPassword } from "firebase/auth";
import { readFileSync } from "node:fs";

const PROJECT_ID = "nexlib-e7970";
const COLLEGE_COUNT = 300;
const PASSWORD = "LoadTest123!";
const RULES = readFileSync(new URL("../../firestore.rules", import.meta.url), "utf8");

const DISTRICTS = [
  "Peshawar", "Mardan", "Swat", "Abbottabad", "Bannu", "Kohat", "Dera Ismail Khan",
  "Mansehra", "Charsadda", "Nowshera", "Swabi", "Haripur", "Malakand", "Karak", "Chitral",
];

const testEnv = await initializeTestEnvironment({
  projectId: PROJECT_ID,
  firestore: { rules: RULES, host: "127.0.0.1", port: 8080 },
});

// Auth emulator: real, sign-in-able accounts — a separate client app because
// rules-unit-testing's contexts are Firestore-only fakes, not real Auth users.
const authApp = initializeApp({ apiKey: "fake-api-key", projectId: PROJECT_ID }, "seed-auth");
const authClient = getAuth(authApp);
connectAuthEmulator(authClient, "http://127.0.0.1:9099", { disableWarnings: true });

async function makeUser(email) {
  const cred = await createUserWithEmailAndPassword(authClient, email, PASSWORD);
  return cred.user.uid;
}

console.log("Creating directorate accounts…");
const directorUid = await makeUser("director@nexlib.com");
const regionalUid = await makeUser("regional@nexlib.com");
const analystUid = await makeUser("analyst@nexlib.com");
const ownerUid = await makeUser("owner@loadtest.edu");

await testEnv.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();

  // All three pass the role gate in auth-context.tsx. director is left with
  // NO /directorate_staff record on purpose: it must default to super_admin
  // exactly the way the real director@nexlib.com does in production, which
  // is the bootstrap-safety guarantee staff.ts and firestore.rules both rest on.
  await setDoc(doc(db, "users", directorUid), { email: "director@nexlib.com", role: "directorate_admin", institutionId: "" });
  await setDoc(doc(db, "users", regionalUid), { email: "regional@nexlib.com", role: "directorate_admin", institutionId: "" });
  await setDoc(doc(db, "users", analystUid), { email: "analyst@nexlib.com", role: "directorate_admin", institutionId: "" });
  await setDoc(doc(db, "directorate_staff", regionalUid), { email: "regional@nexlib.com", tier: "regional", districts: [], addedAt: Date.now(), addedByEmail: "seed" });
  await setDoc(doc(db, "directorate_staff", analystUid), { email: "analyst@nexlib.com", tier: "analyst", districts: [], addedAt: Date.now(), addedByEmail: "seed" });

  // One ordinary college account, so a live check can confirm this account
  // is bounced straight back out of the portal against 300 real-shaped docs,
  // not just the handful the rules unit tests use.
  await setDoc(doc(db, "users", ownerUid), { email: "owner@loadtest.edu", role: "owner", institutionId: "LOADTEST-001" });

  console.log(`Seeding ${COLLEGE_COUNT} colleges…`);
  const now = Date.now();
  let written = 0;
  for (let i = 1; i <= COLLEGE_COUNT; i++) {
    const cid = `LOADTEST-${String(i).padStart(3, "0")}`;
    const district = DISTRICTS[i % DISTRICTS.length];

    // Edge cases at realistic frequencies, not evenly spaced — each one
    // exercises a specific alert or compliance check in lib/analytics.ts.
    const neverSynced = i % 47 === 0;
    const stale = !neverSynced && i % 23 === 0;
    const zeroBooks = !neverSynced && !stale && i % 31 === 0;
    const noContact = i % 17 === 0;
    const highOverdue = i % 29 === 0;
    const unassignedDistrict = i % 53 === 0;

    const books = zeroBooks ? 0 : 400 + ((i * 37) % 4000);
    const members = 50 + ((i * 13) % 800);
    const activeLoans = Math.max(1, Math.round(books * 0.15));
    const overdueCount = highOverdue ? Math.round(activeLoans * 0.65) : Math.round(activeLoans * 0.08);

    await setDoc(doc(db, "institutions", cid), {
      name: `Government Degree College ${district} ${i}`,
      ownerUid: `loadtest-owner-${i}`,
      createdAt: now - i * 3_600_000,
    });

    await setDoc(doc(db, "directorate_index", cid), {
      institutionId: cid,
      name: `Government Degree College ${district} ${i}`,
      location: `${district}, Khyber Pakhtunkhwa`,
      district: unassignedDistrict ? "" : district,
      contactEmail: noContact ? "" : `library@gdc${i}.edu.pk`,
      phone: noContact ? "" : "0300-0000000",
      booksCount: books,
      ebooksCount: Math.round(books * 0.1),
      membersCount: members,
      activeLoans,
      overdueCount,
      reservationsCount: Math.round(members * 0.05),
      finesOutstanding: overdueCount * 50,
      lastSyncAt: neverSynced ? 0 : stale ? now - 5 * 24 * 3_600_000 : now - ((i * 91) % 24) * 3_600_000,
      lastSyncPlatform: i % 3 === 0 ? "android" : i % 3 === 1 ? "web" : "desktop",
      schemaVersion: 2,
    });

    // Most approved; a slice pending (no approval doc at all); a slice hidden.
    if (i % 10 !== 0) {
      await setDoc(doc(db, "directorate_approvals", cid), { collegeId: cid, status: "approved", updatedAt: now, updatedBy: "seed" });
    } else if (i % 20 === 0) {
      await setDoc(doc(db, "directorate_approvals", cid), { collegeId: cid, status: "hidden", updatedAt: now, updatedBy: "seed" });
    }

    written++;
    if (written % 50 === 0) console.log(`  ${written}/${COLLEGE_COUNT}`);
  }

  // A duplicate-institution-ID collision: two different Firestore documents
  // both publishing institutionId "LOADTEST-001" — the stale-republish
  // scenario lib/directorate.ts's own comments describe, and exactly what
  // the "duplicate ID" alert in lib/analytics.ts exists to catch.
  await setDoc(doc(db, "directorate_index", "LOADTEST-001-STALE-DUP"), {
    institutionId: "LOADTEST-001",
    name: "Government Degree College Peshawar 1 (old republish)",
    district: "Peshawar",
    booksCount: 12, ebooksCount: 0, membersCount: 5, activeLoans: 0, overdueCount: 0,
    reservationsCount: 0, finesOutstanding: 0,
    lastSyncAt: now - 90 * 24 * 3_600_000, lastSyncPlatform: "desktop", schemaVersion: 1,
  });
  await setDoc(doc(db, "directorate_approvals", "LOADTEST-001-STALE-DUP"), { collegeId: "LOADTEST-001-STALE-DUP", status: "approved", updatedAt: now, updatedBy: "seed" });

  // Public catalogues for 60 colleges — enough to exceed search.ts's
  // MAX_COLLEGES_PER_SEARCH (40) cap, so a real search run proves the
  // fan-out actually stops there instead of reading all 300 catalogues.
  console.log("Seeding book catalogues for the union-search cap test…");
  for (let i = 1; i <= 60; i++) {
    const cid = `LOADTEST-${String(i).padStart(3, "0")}`;
    for (let b = 1; b <= 5; b++) {
      await setDoc(doc(db, "institutions", cid, "books", `seed-${b}`), {
        syncId: `seed-${b}`,
        title: b === 1 ? "Introduction to Physics" : `Load Test Title ${cid}-${b}`,
        author: "Test Author",
        isbn: b === 2 ? "9780000000001" : `978${String(i).padStart(9, "0")}${b}`,
        category: "Science",
        status: "Available",
        deleted: false,
        collegeId: cid,
        lastUpdated: now,
      });
    }
  }

  // A few pre-existing MIS records so list pages aren't empty on first load;
  // every other write path is exercised live, by hand, through the UI.
  await setDoc(doc(collection(db, "directorate_followups")), {
    collegeId: "LOADTEST-002", collegeName: "Government Degree College Mardan 2",
    title: "Confirm principal contact", detail: "Seed record.", status: "open",
    dueDate: "2026-10-15", createdAt: now, createdByEmail: "seed", resolvedAt: null,
  });
  await setDoc(doc(collection(db, "directorate_announcements")), {
    title: "Welcome to NEXLIB MIS", body: "Seed announcement.",
    publishedAt: now, publishedByEmail: "seed", retracted: false,
  });
});

console.log(`\nSeed complete: ${COLLEGE_COUNT} colleges, 3 directorate accounts, 1 tenant owner.`);
console.log("Start directorate-app with NEXT_PUBLIC_USE_EMULATOR=true and sign in at");
console.log("http://localhost:3001/login with:");
console.log(`  director@nexlib.com / ${PASSWORD}   (super_admin — no staff record)`);
console.log(`  regional@nexlib.com / ${PASSWORD}   (regional tier)`);
console.log(`  analyst@nexlib.com  / ${PASSWORD}   (analyst tier, read-only)`);
console.log(`  owner@loadtest.edu  / ${PASSWORD}   (ordinary college account — must be bounced out)`);

await testEnv.cleanup();
process.exit(0);
