// lib/firebase.ts
// Firebase Web SDK initialization for NEXLIB web client.
// Connects to the same nexlib-e7970 project as the Android and Desktop apps.

import { initializeApp, getApps, getApp } from "firebase/app";
import { getAuth, connectAuthEmulator } from "firebase/auth";
import { getFirestore, connectFirestoreEmulator } from "firebase/firestore";

const firebaseConfig = {
  apiKey: "AIzaSyCDhboGleaMssC2Sl97uKcRcw1o9t1EkXg",
  authDomain: "nexlib-e7970.firebaseapp.com",
  projectId: "nexlib-e7970",
  storageBucket: "nexlib-e7970.firebasestorage.app",
  messagingSenderId: "277394507679",
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID || "1:277394507679:web:06e6943835015de1879faa",
};

// Prevent duplicate initialization in Next.js dev mode (hot reload)
const app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApp();

export const auth = getAuth(app);
export const db = getFirestore(app);

// Local scale/load testing only — connects to `firebase emulators:start`
// instead of the live nexlib-e7970 project, so a 300-college test run never
// touches production data or bills a real read. OFF unless explicitly set;
// see tests/firestore/README.md for how to run a load test against this.
// The `as { _connected?: boolean }` guard is because Next.js's dev-mode hot
// reload re-executes this module without re-running getAuth/getFirestore
// (they return the cached instance), and connectXEmulator() throws the
// second time it is called on the same instance.
if (process.env.NEXT_PUBLIC_USE_EMULATOR === "true") {
  const g = globalThis as typeof globalThis & { __nexlibEmulatorConnected?: boolean };
  if (!g.__nexlibEmulatorConnected) {
    connectFirestoreEmulator(db, "127.0.0.1", 8080);
    connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
    g.__nexlibEmulatorConnected = true;
  }
}

export default app;
