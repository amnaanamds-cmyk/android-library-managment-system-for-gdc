// tests/firestore/spark.test.mjs
//
// Can the directorate portal be opened on the Firebase SPARK plan?
//
// Spark has no Cloud Functions, so nothing can mint a custom claim. The only
// place a role can live is the users/{uid} document, typed in by hand in the
// Firebase console. firestore.rules is written to fall back to that document
// when no claim is present — this checks that the fallback actually reaches
// every collection the portal reads, because if it does not, a directorate
// officer on Spark gets a portal whose every query fails.
//
//   firebase emulators:exec --only firestore --project spark-check \
//     "node tests/firestore/spark.test.mjs"
import { readFileSync } from "fs";
import { fileURLToPath } from "url";
import { dirname, join } from "path";
import { initializeTestEnvironment, assertSucceeds, assertFails }
  from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc, collection, getDocs } from "firebase/firestore";

const HERE = dirname(fileURLToPath(import.meta.url));
const RULES = join(HERE, "..", "..", "firestore.rules");

const env = await initializeTestEnvironment({
  projectId: "spark-check",
  firestore: {
    rules: readFileSync(RULES, "utf8"),
    host: "127.0.0.1",
    port: 8080,
  },
});

let pass = 0, fail = 0;
const check = async (label, p) => {
  try {
    await p;
    console.log("  ok  ", label);
    pass++;
  } catch (e) {
    console.log("  FAIL", label, "—", e.message.slice(0, 120));
    fail++;
  }
};

await env.withSecurityRulesDisabled(async (ctx) => {
  const db = ctx.firestore();
  // Exactly what an administrator types into the Firebase console on Spark.
  await setDoc(doc(db, "users/officer"),
    { uid: "officer", role: "directorate", institutionId: "" });
  await setDoc(doc(db, "institution_registry/GDCZIAM112233"),
    { institutionId: "GDCZIAM112233", name: "GDC Zia-ud-Din", status: "active" });
  await setDoc(doc(db, "directorate_summary/GDCZIAM112233"),
    { institutionId: "GDCZIAM112233", totalBooks: 558 });
  await setDoc(doc(db, "directorate_index/GDCZIAM112233"),
    { institutionId: "GDCZIAM112233", booksCount: 558 });
});

// No custom claims at all — the token carries nothing but the uid.
const officer = env.authenticatedContext("officer").firestore();

console.log("\n-- Spark plan: directorate role set only in users/{uid} --");
await check("reads the institution registry",
  assertSucceeds(getDocs(collection(officer, "institution_registry"))));
await check("reads the server-computed summary",
  assertSucceeds(getDocs(collection(officer, "directorate_summary"))));
await check("reads the self-reported rollup the portal falls back to on Spark",
  assertSucceeds(getDocs(collection(officer, "directorate_index"))));
await check("still cannot forge a summary row",
  assertFails(setDoc(doc(officer, "directorate_summary/GDCZIAM112233"),
    { totalBooks: 9999 })));

// The fallback must not be a way in for everyone else.
await env.withSecurityRulesDisabled(async (ctx) => {
  await setDoc(doc(ctx.firestore(), "users/collegeadmin"),
    { uid: "collegeadmin", role: "admin", institutionId: "GDC-OTHER" });
});
const admin = env.authenticatedContext("collegeadmin").firestore();
await check("a college admin cannot read another college's summary row",
  assertFails(getDoc(doc(admin, "directorate_summary/GDCZIAM112233"))));

console.log(`\n${pass} passed, ${fail} failed`);
await env.cleanup();
process.exit(fail === 0 ? 0 : 1);
