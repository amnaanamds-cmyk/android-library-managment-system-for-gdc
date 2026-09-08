"use client";

import React, { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { User, onAuthStateChanged, signOut } from "firebase/auth";
import { doc, getDoc } from "firebase/firestore";
import { httpsCallable } from "firebase/functions";
import { auth, db, functions } from "./firebase";
import { useRouter, usePathname } from "next/navigation";

/**
 * Auto-logout after 30 minutes of inactivity (spec section 4).
 *
 * Library terminals are shared and frequently left signed in at a public desk.
 * The same timeout is configured on the desktop client (INACTIVITY_TIMEOUT in
 * gdc_desktop/config.py) so the behaviour is consistent across platforms.
 */
export const IDLE_TIMEOUT_MS = 30 * 60 * 1000;

const ACTIVITY_EVENTS = ["mousedown", "keydown", "scroll", "touchstart", "visibilitychange"];

export type Role = "directorate" | "admin" | "librarian" | "staff" | "student";

export interface UserProfile {
  uid: string;
  email: string;
  role: Role | string;
  institutionId: string;
  displayName?: string;
  /** True when role/institutionId came from the ID token rather than Firestore. */
  fromClaims: boolean;
}

interface AuthContextType {
  user: User | null;
  profile: UserProfile | null;
  loading: boolean;
  logout: () => Promise<void>;
  refreshProfile: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  profile: null,
  loading: true,
  logout: async () => {},
  refreshProfile: async () => {},
});

/**
 * Resolve the signed-in user's role and institution.
 *
 * Custom claims are authoritative and free — they travel inside the ID token,
 * and firestore.rules reads the same values, so trusting anything else here
 * would let the UI and the rules disagree about who someone is.
 *
 * The Firestore fallback exists for accounts that predate claims. When it fires
 * we ask the backend to mint claims and refresh the token, so each account
 * takes this slow path at most once.
 */
async function resolveProfile(user: User): Promise<UserProfile | null> {
  const email = user.email || "";

  try {
    const token = await user.getIdTokenResult();
    const role = token.claims.role as string | undefined;
    const institutionId = (token.claims.institutionId as string | undefined) || "";

    // Directorate accounts legitimately carry no institutionId.
    if (role && (institutionId || role === "directorate")) {
      return { uid: user.uid, email, role, institutionId, fromClaims: true };
    }
  } catch (e) {
    console.warn("Could not read ID token claims:", e);
  }

  // ── Fallback: read the profile document, then migrate this account. ────────
  let profile: UserProfile | null = null;
  try {
    const snap = await getDoc(doc(db, "users", user.uid));
    if (snap.exists()) {
      const data = snap.data();
      profile = {
        uid: user.uid,
        email,
        role: data.role || "student",
        institutionId: data.institutionId || data.collegeId || "",
        displayName: data.displayName,
        fromClaims: false,
      };
    }
  } catch (e) {
    console.error("Error fetching user profile:", e);
  }

  if (!profile) return null;

  // Mint claims for this account so the next sign-in takes the fast path and
  // the security rules stop paying for a document read on every evaluation.
  try {
    await httpsCallable(functions, "refreshMyClaims")({});
    await user.getIdToken(true);
  } catch (e) {
    // Non-fatal: the profile document still authorises this session, and the
    // rules keep their own fallback for exactly this case.
    console.warn("Could not mint custom claims for this account:", e);
  }

  return profile;
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const router = useRouter();
  const pathname = usePathname();

  // Held in a ref so the idle timer can sign out without this callback being a
  // dependency of the effect that installs the listeners.
  const logoutRef = useRef<() => Promise<void>>(async () => {});

  const logout = useCallback(async () => {
    setLoading(true);
    await signOut(auth);
    setUser(null);
    setProfile(null);
    router.push("/login");
    setLoading(false);
  }, [router]);

  logoutRef.current = logout;

  const refreshProfile = useCallback(async () => {
    if (auth.currentUser) setProfile(await resolveProfile(auth.currentUser));
  }, []);

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, async (firebaseUser) => {
      setUser(firebaseUser);

      if (!firebaseUser) {
        setProfile(null);
        if (pathname !== "/login") router.push("/login");
        setLoading(false);
        return;
      }

      const prof = await resolveProfile(firebaseUser);
      setProfile(prof);

      // A directorate account has no institution and must not be sent to
      // onboarding — that would trap it in a loop it can never complete.
      const isDirectorate = prof?.role === "directorate";

      if (!prof || (!prof.institutionId && !isDirectorate)) {
        if (pathname !== "/onboard") router.push("/onboard");
      } else if (pathname === "/login" || pathname === "/onboard" || pathname === "/") {
        router.push(isDirectorate ? "/director" : "/dashboard");
      }

      setLoading(false);
    });

    return () => unsubscribe();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pathname]);

  // ── Idle timeout ──────────────────────────────────────────────────────────
  useEffect(() => {
    if (!user) return;

    let timer: ReturnType<typeof setTimeout>;
    const reset = () => {
      clearTimeout(timer);
      timer = setTimeout(() => {
        void logoutRef.current();
      }, IDLE_TIMEOUT_MS);
    };

    reset();
    ACTIVITY_EVENTS.forEach((e) => window.addEventListener(e, reset, { passive: true }));
    return () => {
      clearTimeout(timer);
      ACTIVITY_EVENTS.forEach((e) => window.removeEventListener(e, reset));
    };
  }, [user]);

  return (
    <AuthContext.Provider value={{ user, profile, loading, logout, refreshProfile }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
