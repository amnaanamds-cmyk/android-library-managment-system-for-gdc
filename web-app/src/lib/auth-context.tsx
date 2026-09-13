"use client";

import React, { createContext, useContext, useEffect, useState } from "react";
import { User, onAuthStateChanged, signOut } from "firebase/auth";
import { doc, getDoc } from "firebase/firestore";
import { auth, db } from "./firebase";
import { canViewDirectorate } from "./roles";
import { useRouter, usePathname } from "next/navigation";

interface UserProfile {
  uid: string;
  email: string;
  role: string;
  institutionId: string;
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

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [loading, setLoading] = useState(true);
  const router = useRouter();
  const pathname = usePathname();

  const fetchProfile = async (uid: string, email: string) => {
    try {
      const userRef = doc(db, "users", uid);
      const userSnap = await getDoc(userRef);
      if (userSnap.exists()) {
        const data = userSnap.data();
        return {
          uid,
          email,
          role: data.role || "staff",
          institutionId: data.institutionId || data.collegeId || "",
        };
      }
    } catch (e) {
      console.error("Error fetching user profile:", e);
    }
    return null;
  };

  const refreshProfile = async () => {
    if (user) {
      const prof = await fetchProfile(user.uid, user.email || "");
      setProfile(prof);
    }
  };

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, async (firebaseUser) => {
      setUser(firebaseUser);
      if (firebaseUser) {
        const prof = await fetchProfile(firebaseUser.uid, firebaseUser.email || "");
        setProfile(prof);

        if (canViewDirectorate(prof?.role)) {
          // A directorate account belongs to no college, so the
          // "no institutionId -> onboard" rule below must not apply to it:
          // that rule used to march it into college onboarding, whose join
          // path overwrites role with "staff" and silently demotes it.
          if (pathname === "/login" || pathname === "/onboard" || pathname === "/") {
            router.push("/director");
          }
        } else if (!prof || !prof.institutionId) {
          if (pathname !== "/onboard") {
            router.push("/onboard");
          }
        } else if (pathname === "/login" || pathname === "/onboard" || pathname === "/") {
          router.push("/dashboard");
        }
      } else {
        setProfile(null);
        if (pathname !== "/login") {
          router.push("/login");
        }
      }
      setLoading(false);
    });

    return () => unsubscribe();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pathname]); // ✅ Only re-run when route changes, not on user state updates (avoids infinite loop)

  const logout = async () => {
    setLoading(true);
    await signOut(auth);
    setUser(null);
    setProfile(null);
    router.push("/login");
    setLoading(false);
  };

  return (
    <AuthContext.Provider value={{ user, profile, loading, logout, refreshProfile }}>
      {children}
    </AuthContext.Provider>
  );
}

export const useAuth = () => useContext(AuthContext);
