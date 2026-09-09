// lib/firebase.ts
//
// Firebase Web SDK initialization for the NEXLIB web client and the directorate
// portal. Both surfaces, and the Android and Python desktop apps, must point at
// the SAME project — a mix of dev and prod configs across platforms is what
// breaks the "one linked system" behaviour, and it fails silently: each app
// works perfectly on its own and simply never sees the others' data.

import { initializeApp, getApps, getApp, FirebaseOptions } from "firebase/app";
import { getAuth, connectAuthEmulator } from "firebase/auth";
import {
  initializeFirestore,
  getFirestore,
  connectFirestoreEmulator,
  persistentLocalCache,
  persistentMultipleTabManager,
} from "firebase/firestore";
import { getFunctions, connectFunctionsEmulator } from "firebase/functions";

/**
 * Point the whole web app at the local Firebase emulators.
 *
 * Set NEXT_PUBLIC_FIREBASE_EMULATOR=1 and run `firebase emulators:start`. The
 * ports match firebase.json. Without this there is no way to open the
 * dashboards or the director portal without a real project and real
 * credentials, which makes the app impossible to try, demo or test offline.
 */
const USE_EMULATOR = process.env.NEXT_PUBLIC_FIREBASE_EMULATOR === "1";
const EMULATOR_HOST = process.env.NEXT_PUBLIC_FIREBASE_EMULATOR_HOST || "127.0.0.1";

/**
 * Region for callable functions. Must match REGION in functions/src/config.ts —
 * a callable invoked against the wrong region fails with an opaque
 * "internal" error rather than anything that names the mismatch.
 */
export const FUNCTIONS_REGION = "asia-south1";

// Web config values are not secrets (they ship in every client bundle), but
// they are environment-specific, so they are overridable for staging.
const firebaseConfig: FirebaseOptions = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY || "AIzaSyBnDlkWcFqCWaXq9YSl2CFMzBb3yh3pIlE",
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN || "nexlib-e7970.firebaseapp.com",
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID || "nexlib-e7970",
  storageBucket:
    process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET || "nexlib-e7970.firebasestorage.app",
  messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID || "277394507679",
  appId:
    process.env.NEXT_PUBLIC_FIREBASE_APP_ID || "1:277394507679:web:06e6943835015de1879faa",
};

// Prevent duplicate initialization in Next.js dev mode (hot reload).
const app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApp();

/**
 * Firestore with offline persistence (spec section 5).
 *
 * Without this the web app blocks on the network for every read, so a librarian
 * on a dropped connection sees empty tables rather than the last known
 * catalogue — the opposite of the offline-first guarantee the desktop and
 * mobile apps provide. `persistentMultipleTabManager` is required because staff
 * routinely keep the catalogue and the circulation desk open in two tabs; the
 * single-tab manager throws in the second one.
 *
 * initializeFirestore must run before any getFirestore() call, and it throws if
 * called twice, hence the getApps() guard above and the try/catch here:
 * persistence is unavailable in private browsing and on some locked-down
 * browsers, and losing the cache must degrade to a network-only client rather
 * than break the whole app.
 */
function createDb() {
  if (typeof window === "undefined") {
    // Server-side rendering has no IndexedDB and needs no cache.
    return getFirestore(app);
  }
  try {
    return initializeFirestore(app, {
      localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() }),
    });
  } catch (err) {
    console.warn("Firestore offline persistence unavailable, falling back to network-only:", err);
    return getFirestore(app);
  }
}

export const auth = getAuth(app);
export const db = createDb();
export const functions = getFunctions(app, FUNCTIONS_REGION);

if (USE_EMULATOR) {
  // connect*Emulator throws if called twice on the same instance, which Next's
  // hot reload will happily do, so each is guarded independently.
  const connect = (label: string, fn: () => void) => {
    try {
      fn();
    } catch (err) {
      if (process.env.NODE_ENV !== "production") {
        console.warn(`${label} emulator already connected:`, (err as Error).message);
      }
    }
  };
  connect("Auth", () =>
    connectAuthEmulator(auth, `http://${EMULATOR_HOST}:9099`, { disableWarnings: true }),
  );
  connect("Firestore", () => connectFirestoreEmulator(db, EMULATOR_HOST, 8080));
  connect("Functions", () => connectFunctionsEmulator(functions, EMULATOR_HOST, 5001));
  console.info(`NEXLIB: using Firebase emulators at ${EMULATOR_HOST}`);
}

export default app;
