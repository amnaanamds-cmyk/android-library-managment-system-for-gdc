"use client";

import React, { createContext, useContext, useEffect, useState } from "react";
import { auth, db } from "@/lib/firebase";
import { onAuthStateChanged, User as FirebaseUser } from "firebase/auth";
import { doc, getDoc } from "firebase/firestore";

interface AppUser {
  uid: string;
  email: string;
  role: string;
  institutionId: string;
  name: string;
}

interface AuthContextType {
  user: AppUser | null;
  loading: boolean;
}

const AuthContext = createContext<AuthContextType>({ user: null, loading: true });

export const useAuth = () => useContext(AuthContext);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<AppUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Check if auth is mocked
    if (auth.app.options.apiKey === "dummy") {
      console.warn("Using Firebase Mock Mode due to missing config.");
      // We'll simulate a logged-in user if there's no config, so UI works for testing
      setUser({
        uid: "dummy_uid",
        email: "admin@gdc.edu",
        role: "admin",
        institutionId: "gdc-ziam-sherpao",
        name: "Admin User",
      });
      setLoading(false);
      return;
    }

    const unsubscribe = onAuthStateChanged(auth, async (firebaseUser: FirebaseUser | null) => {
      if (firebaseUser) {
        try {
          const userDoc = await getDoc(doc(db, "users", firebaseUser.uid));
          if (userDoc.exists()) {
            const data = userDoc.data();
            setUser({
              uid: firebaseUser.uid,
              email: firebaseUser.email || "",
              role: data.role || "staff",
              institutionId: data.institutionId || "",
              name: data.name || "",
            });
          } else {
            // New user without role
            setUser({
              uid: firebaseUser.uid,
              email: firebaseUser.email || "",
              role: "staff",
              institutionId: "",
              name: "",
            });
          }
        } catch (error) {
          console.error("Error fetching user role:", error);
          setUser(null);
        }
      } else {
        setUser(null);
      }
      setLoading(false);
    });

    return () => unsubscribe();
  }, []);

  return (
    <AuthContext.Provider value={{ user, loading }}>
      {children}
    </AuthContext.Provider>
  );
}
