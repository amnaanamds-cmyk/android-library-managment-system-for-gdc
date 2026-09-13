"use client";

// lib/auth-context.tsx
//
// Auth for the directorate portal. Deliberately NOT the web portal's version:
// this app has no concept of a college, no onboarding, and refuses any account
// that is not directorate-level. A college's own owner/admin signing in here
// is signed straight back out rather than being shown a shell they cannot use.

import React, { createContext, useContext, useEffect, useState } from "react";
import { User, onAuthStateChanged, signOut } from "firebase/auth";
import { doc, getDoc } from "firebase/firestore";
import { auth, db } from "./firebase";
import { canViewDirectorate } from "./roles";
import { useRouter, usePathname } from "next/navigation";

interface DirectorateProfile {
  uid: string;
  email: string;
  role: string;
}

interface AuthContextValue {
  user: User | null;
  profile: DirectorateProfile | null;
  loading: boolean;
  /** Set when a real account signed in but is not directorate-level. */
  accessDenied: string | null;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue>({
  user: null,
  profile: null,
  loading: true,
  accessDenied: null,
  logout: async () => {},
});

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [profile, setProfile] = useState<DirectorateProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const [accessDenied, setAccessDenied] = useState<string | null>(null);
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, async (firebaseUser) => {
      setUser(firebaseUser);

      if (!firebaseUser) {
        setProfile(null);
        setAccessDenied(null);
        setLoading(false);
        if (pathname !== "/login") router.push("/login");
        return;
      }

      let role = "";
      try {
        const snap = await getDoc(doc(db, "users", firebaseUser.uid));
        if (snap.exists()) role = snap.data().role || "";
      } catch {
        // A denied read leaves role empty, which fails the check below —
        // the safe direction.
      }

      if (!canViewDirectorate(role)) {
        // Not a directorate account. Sign out rather than leaving a session
        // open against a portal it has no business in.
        setProfile(null);
        setAccessDenied(
          role
            ? `This account is signed in as "${role}". The directorate portal is limited to Higher Education Department directorate accounts.`
            : "This account has no directorate role assigned.",
        );
        await signOut(auth);
        setLoading(false);
        if (pathname !== "/login") router.push("/login");
        return;
      }

      setAccessDenied(null);
      setProfile({ uid: firebaseUser.uid, email: firebaseUser.email || "", role });
      setLoading(false);
      if (pathname === "/login") router.push("/");
    });

    return () => unsubscribe();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pathname]);

  const logout = async () => {
    await signOut(auth);
    router.push("/login");
  };

  return (
    <AuthContext.Provider value={{ user, profile, loading, accessDenied, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
