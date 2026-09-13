"use client";

import { useState } from "react";
import {
  signInWithEmailAndPassword,
  createUserWithEmailAndPassword,
} from "firebase/auth";
import { auth, db } from "@/lib/firebase";
import { doc, setDoc } from "firebase/firestore";
import { useRouter } from "next/navigation";
import { useAuth } from "@/lib/auth-context";

/** Maps Firebase auth error codes to user-friendly messages. */
function mapFirebaseError(code: string): string {
  switch (code) {
    // Firebase returns invalid-credential for a WRONG PASSWORD as well as an
    // unknown email (email-enumeration protection deliberately makes the two
    // indistinguishable), so this must not claim the account doesn't exist —
    // that sends people off creating a duplicate account that then fails as
    // already-in-use, when all they had was a typo in the password.
    case "auth/invalid-credential":
    case "auth/wrong-password":
      return "Incorrect email or password. If you don't have an account yet, create one below.";
    case "auth/user-not-found":
      return "No account found with that email address. If you are signing in for the first time on the web, please create a new account below.";
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
  const { wrongPortal } = useAuth();

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
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-[#060C18] via-[#0D1B2A] to-[#060C18] px-4 py-12 sm:px-6 lg:px-8">
      <div className="w-full max-w-md space-y-6 rounded-2xl border border-blue-900/50 bg-[#0D1C37]/90 p-8 shadow-2xl backdrop-blur">

        {/* Logo */}
        <div className="text-center">
          <div className="text-5xl mb-3">📚</div>
          <h2 className="text-3xl font-extrabold tracking-tight text-[#E6C96E]">
            NEXLIB Web Portal
          </h2>
          <p className="mt-1 text-xs tracking-wider text-[#4D6A90] uppercase">
            GDC Library Management System
          </p>
        </div>

        {/* Tab switcher */}
        <div className="flex rounded-xl border border-blue-900/40 overflow-hidden">
          <button
            type="button"
            onClick={switchToLogin}
            className={`flex-1 py-2.5 text-sm font-bold transition-colors ${
              !isRegister
                ? "bg-blue-600 text-white"
                : "text-slate-400 hover:text-white hover:bg-white/5"
            }`}
          >
            Sign In
          </button>
          <button
            type="button"
            onClick={switchToRegister}
            className={`flex-1 py-2.5 text-sm font-bold transition-colors ${
              isRegister
                ? "bg-[#C8A84B] text-[#060C18]"
                : "text-slate-400 hover:text-white hover:bg-white/5"
            }`}
          >
            Create Account
          </button>
        </div>

        {/* Context hint */}
        {isRegister && (
          <div className="rounded-lg bg-[#C8A84B]/10 border border-[#C8A84B]/30 px-4 py-3 text-xs text-[#E6C96E]">
            <strong>First time on the web portal?</strong> Create a new account
            using your college email. After registration you&apos;ll set up your
            institution.
          </div>
        )}

        <form className="space-y-5" onSubmit={handleAction}>
          <div className="space-y-4">
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-[#A0B4CC] mb-1">
                Email Address
              </label>
              <input
                id="email-input"
                type="email"
                required
                autoComplete="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-3 text-sm text-[#E8EEF8] placeholder-slate-500 outline-none transition-colors focus:border-[#C8A84B] focus:bg-[#0F2444]"
                placeholder="admin@college.edu"
              />
            </div>
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-[#A0B4CC] mb-1">
                Password {isRegister && <span className="text-slate-500 normal-case font-normal">(min. 6 characters)</span>}
              </label>
              <input
                id="password-input"
                type="password"
                required
                autoComplete={isRegister ? "new-password" : "current-password"}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-3 text-sm text-[#E8EEF8] placeholder-slate-500 outline-none transition-colors focus:border-[#C8A84B] focus:bg-[#0F2444]"
                placeholder="••••••••"
              />
            </div>
          </div>

          {/* A directorate account signed in here — it belongs in the
              separate Directorate portal, so say so instead of failing. */}
          {wrongPortal && (
            <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 p-4 text-xs leading-relaxed text-amber-200">
              <span className="mr-1 text-base leading-none">🏛️</span>
              {wrongPortal}
            </div>
          )}

          {/* Error */}
          {errorMsg && (
            <div className="rounded-lg border border-red-500/30 bg-red-500/10 p-4 space-y-2">
              <div className="flex items-start gap-2 text-xs text-red-300">
                <span className="text-base leading-none mt-0.5 shrink-0">⚠️</span>
                <span className="font-semibold leading-relaxed">{errorMsg}</span>
              </div>
              {/* If wrong credentials, suggest registering */}
              {(errorMsg.includes("No account") || errorMsg.includes("credentials")) && !isRegister && (
                <div className="pl-6 text-xs text-slate-400">
                  👉{" "}
                  <button
                    type="button"
                    onClick={switchToRegister}
                    className="text-[#C8A84B] underline hover:text-yellow-300 font-semibold"
                  >
                    Create a new account instead
                  </button>
                </div>
              )}
            </div>
          )}

          {/* Success */}
          {successMsg && (
            <div className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-4 py-3 text-xs text-emerald-300 font-semibold">
              {successMsg}
            </div>
          )}

          <button
            id="auth-submit-btn"
            type="submit"
            disabled={loading}
            className={`flex w-full justify-center rounded-lg py-3.5 text-sm font-bold text-white transition-all focus:outline-none disabled:opacity-50 shadow-lg ${
              isRegister
                ? "bg-gradient-to-r from-[#B8941E] to-[#C8A84B] hover:from-[#C8A84B] hover:to-[#E0C060] text-[#0D1B2A]"
                : "bg-gradient-to-r from-[#1E5FD4] to-[#2872F0] hover:from-[#2872F0] hover:to-[#3D8EFF]"
            }`}
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
        <div className="pt-2 border-t border-blue-900/30 text-center">
          <p className="text-[10px] text-slate-600">
            Firebase: nexlib-e7970.firebaseapp.com
          </p>
        </div>
      </div>
    </div>
  );
}
