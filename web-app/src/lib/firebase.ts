// lib/firebase.ts
// Firebase Web SDK initialization for NEXLIB web client.
// Connects to the same nexlib-e7970 project as the Android and Desktop apps.

import { initializeApp, getApps, getApp } from "firebase/app";
import { getAuth } from "firebase/auth";
import { getFirestore } from "firebase/firestore";

const firebaseConfig = {
  apiKey: "AIzaSyBnDlkWcFqCWaXq9YSl2CFMzBb3yh3pIlE",
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
export default app;
