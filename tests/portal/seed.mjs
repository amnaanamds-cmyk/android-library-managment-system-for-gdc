// tests/portal/seed.mjs
//
// Seed the Firebase emulators with a directorate officer and a plausible slice
// of the KPK network, so the director portal can be exercised for real rather
// than against mocks. See tests/portal/README.md.
//
// Emulators only. It refuses to touch a real project: firebase-admin talks to
// whatever FIRESTORE_EMULATOR_HOST points at, and these are set here rather
// than inherited so a stray environment cannot redirect it at production.
const HOST = process.env.EMULATOR_HOST || "127.0.0.1";
process.env.FIRESTORE_EMULATOR_HOST = `${HOST}:8080`;
process.env.FIREBASE_AUTH_EMULATOR_HOST = `${HOST}:9099`;
import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";

initializeApp({ projectId: "nexlib-e7970" });
const auth = getAuth();
const db = getFirestore();

const EMAIL = "directorate.officer@hed.gkp.pk";
let user;
try { user = await auth.getUserByEmail(EMAIL); }
catch { user = await auth.createUser({ email: EMAIL, password: "Passw0rd!" }); }
await auth.setCustomUserClaims(user.uid, { role: "directorate", institutionId: null });
await db.doc(`users/${user.uid}`).set(
  { uid: user.uid, email: EMAIL, role: "directorate", institutionId: "" }, { merge: true });

const HOUR = 3600_000;
const colleges = [
  ["GDC-ZIAM-SHERPAO", "GDC Zia-ud-Din Sherpao, Charsadda", "Charsadda", "active",    5581, 240, 1180, 96, 12, 0.5],
  ["GDC-MARDAN",       "GDC Mardan",                        "Mardan",    "active",   12470, 610, 2430, 214, 41, 2],
  ["GDC-SWAT",         "GDC Saidu Sharif, Swat",            "Swat",      "active",    8120, 310, 1640, 130,  0, 6],
  ["GDC-PESHAWAR-1",   "GDC No.1 Peshawar",                 "Peshawar",  "active",   19340, 880, 3910, 402, 77, 1],
  ["GDC-KOHAT",        "GDC Kohat",                         "Kohat",     "active",    4410, 120,  980,  61,  3, 96],
  ["GDC-DIKHAN",       "GDC Dera Ismail Khan",              "D.I. Khan", "suspended", 3020,  80,  640,  38,  0, 400],
  ["GDC-BANNU",        "GDC Bannu",                         "Bannu",     "active",       0,   0,    0,   0,  0, null],
  ["GDC-CHITRAL",      "GDC Chitral",                       "Chitral",   "pending",      0,   0,    0,   0,  0, null],
];

const batch = db.batch();
for (const [id, name, district, status, books, ebooks, members, issued, overdue, agoHours] of colleges) {
  batch.set(db.doc(`institution_registry/${id}`), {
    institutionId: id, name, district, region: "Khyber Pakhtunkhwa", status,
    contactEmail: `principal@${id.toLowerCase()}.edu.pk`,
    adminName: "Principal", createdAt: Date.now() - 90 * 24 * HOUR,
  });
  if (status === "pending") continue;
  batch.set(db.doc(`directorate_summary/${id}`), {
    institutionId: id, name, district, region: "Khyber Pakhtunkhwa", status,
    contactEmail: `principal@${id.toLowerCase()}.edu.pk`, phone: "",
    totalBooks: books, totalEbooks: ebooks, members, issued, overdue,
    reservations: Math.round(members / 40),
    recordsMissingSyncEnvelope: id === "GDC-KOHAT" ? 214 : 0,
    lastSynced: agoHours === null ? 0 : Date.now() - agoHours * HOUR,
    computedAt: Date.now() - HOUR,
  });
}
await batch.commit();
console.log("seeded", colleges.length, "colleges; directorate uid", user.uid);
