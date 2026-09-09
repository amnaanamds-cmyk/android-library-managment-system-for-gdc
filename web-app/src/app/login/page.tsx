"use client";

import { useState } from "react";
import {
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
} from "firebase/auth";
import { auth, db } from "@/lib/firebase";
import { doc, setDoc } from "firebase/firestore";
import { useRouter } from "next/navigation";

/** Maps Firebase auth error codes to user-friendly messages. */
function mapFirebaseError(code: string): string {
  switch (code) {
    case "auth/invalid-credential":
    case "auth/wrong-password":
    case "auth/user-not-found":
      return "No account found with these credentials. If you are signing in for the first time on the web, please create a new account below.";
    case "auth/invalid-email":
      return "The email address format is invalid. Please enter a valid email.";
    case "auth/user-disabled":
      return "This account has been disabled. Contact your administrator.";
    case "auth/too-many-requests":
      return "Too many failed attempts. Please wait a few minutes and try again.";
    case "auth/network-request-failed":
      return "Network error. Please check your internet connection and try again.";
    case "auth/email-already-in-use":
      return "An account with this email already exists. Try signing in instead.";
    case "auth/weak-password":
      return "Password is too weak. Please use at least 6 characters.";
    case "auth/operation-not-allowed":
      return "Email/password sign-in is not enabled in Firebase Console. Go to Authentication → Sign-in method → Enable Email/Password.";
    default:
      return `Authentication failed. (${code})`;
  }
}

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [isRegister, setIsRegister] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const router = useRouter();

  const handleAction = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) {
      setErrorMsg("Please fill in all fields.");
      return;
    }
    if (isRegister && password.length < 6) {
      setErrorMsg("Password must be at least 6 characters.");
      return;
    }
    setLoading(true);
    setErrorMsg(null);
    setSuccessMsg(null);

    try {
      if (isRegister) {
        const result = await createUserWithEmailAndPassword(
          auth,
          email.trim(),
          password
        );
        const uid = result.user.uid;
        // Create initial user document — institutionId empty means onboard next
        await setDoc(doc(db, "users", uid), {
          email: email.trim(),
          institutionId: "",
          role: "owner",
          createdAt: Date.now(),
        });
        setSuccessMsg("✅ Account created! Redirecting to setup…");
      } else {
        await signInWithEmailAndPassword(auth, email.trim(), password);
        setSuccessMsg("✅ Signed in! Redirecting…");
      }
      setTimeout(() => router.push("/"), 500);
    } catch (err: any) {
      console.error("Auth error:", err.code, err.message);
      setErrorMsg(mapFirebaseError(err.code || err.message));
    } finally {
      setLoading(false);
    }
  };

  const switchToRegister = () => {
    setIsRegister(true);
    setErrorMsg(null);
    setSuccessMsg(null);
  };

  const switchToLogin = () => {
    setIsRegister(false);
    setErrorMsg(null);
    setSuccessMsg(null);
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-app via-app to-app px-4 py-12 sm:px-6 lg:px-8">
      <div className="w-full max-w-md space-y-6 rounded-2xl border border-line/50 bg-surface-2/90 p-8 shadow-2xl backdrop-blur">

        {/* Logo */}
        <div className="text-center">
          <div className="text-5xl mb-3">📚</div>
          <h2 className="text-3xl font-extrabold tracking-tight text-accent-strong">
            NEXLIB Web Portal
          </h2>
          <p className="mt-1 text-xs tracking-wider text-muted uppercase">
            GDC Library Management System
          </p>
        </div>

        {/* Tab switcher */}
        <div className="flex rounded-xl border border-line/40 overflow-hidden">
          <button
            type="button"
            onClick={switchToLogin}
            className={`flex-1 py-2.5 text-sm font-bold transition-colors ${
              !isRegister
                ? "bg-accent-bg text-on-accent"
                : "text-muted hover:text-ink hover:bg-surface-2"
            }`}
          >
            Sign In
          </button>
          <button
            type="button"
            onClick={switchToRegister}
            className={`flex-1 py-2.5 text-sm font-bold transition-colors ${
              isRegister
                ? "bg-accent-bg text-on-accent"
                : "text-muted hover:text-ink hover:bg-surface-2"
            }`}
          >
            Create Account
          </button>
        </div>

        {/* Context hint */}
        {isRegister && (
          <div className="rounded-lg bg-accent-bg/10 border border-accent/30 px-4 py-3 text-xs text-accent-strong">
            <strong>First time on the web portal?</strong> Create a new account
            using your college email. After registration you&apos;ll set up your
            institution.
          </div>
        )}

        <form className="space-y-5" onSubmit={handleAction}>
          <div className="space-y-4">
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-muted mb-1">
                Email Address
              </label>
              <input
                id="email-input"
                type="email"
                required
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full rounded-lg border border-line bg-surface px-4 py-3 text-sm text-ink placeholder-muted outline-none transition-colors focus:border-accent focus:bg-surface"
                placeholder="admin@college.edu"
              />
            </div>
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-muted mb-1">
                Password {isRegister && <span className="text-muted normal-case font-normal">(min. 6 characters)</span>}
              </label>
              <input
                id="password-input"
                type="password"
                required
                autoComplete={isRegister ? "new-password" : "current-password"}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full rounded-lg border border-line bg-surface px-4 py-3 text-sm text-ink placeholder-muted outline-none transition-colors focus:border-accent focus:bg-surface"
                placeholder="••••••••"
              />
            </div>
          </div>

          {/* Error */}
          {errorMsg && (
            <div className="rounded-lg border border-danger/30 bg-danger/10 p-4 space-y-2">
              <div className="flex items-start gap-2 text-xs text-danger">
                <span className="text-base leading-none mt-0.5 shrink-0">⚠️</span>
                <span className="font-semibold leading-relaxed">{errorMsg}</span>
              </div>
              {/* If wrong credentials, suggest registering */}
              {(errorMsg.includes("No account") || errorMsg.includes("credentials")) && !isRegister && (
                <div className="pl-6 text-xs text-muted">
                  👉{" "}
                  <button
                    type="button"
                    onClick={switchToRegister}
                    className="text-accent underline hover:text-warning font-semibold"
                  >
                    Create a new account instead
                  </button>
                </div>
              )}
            </div>
          )}

          {/* Success */}
          {successMsg && (
            <div className="rounded-lg border border-positive/30 bg-positive/10 px-4 py-3 text-xs text-positive font-semibold">
              {successMsg}
            </div>
          )}

          <button
            id="auth-submit-btn"
            type="submit"
            disabled={loading}
            className="flex w-full justify-center rounded-lg bg-accent-bg py-3.5 text-sm font-bold text-on-accent transition-colors hover:bg-accent-strong focus:outline-none disabled:opacity-50"
          >
            {loading ? (
              <div className="h-5 w-5 animate-spin rounded-full border-2 border-white border-t-transparent" />
            ) : isRegister ? (
              "🚀 Create Account & Enter"
            ) : (
              "🔐 Sign In"
            )}
          </button>
        </form>

        {/* Firebase project info — diagnostic */}
        <div className="pt-2 border-t border-line/30 text-center">
          <p className="text-[10px] text-muted">
            Firebase: nexlib-e7970.firebaseapp.com
          </p>
        </div>
      </div>
    </div>
  );
}
