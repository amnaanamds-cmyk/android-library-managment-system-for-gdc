"use client";

import { useState } from "react";
import { signInWithEmailAndPassword } from "firebase/auth";
import { auth } from "@/lib/firebase";
import { useAuth } from "@/lib/auth-context";

/** Firebase returns invalid-credential for a wrong password as well as an
 *  unknown email, so this must never claim the account does not exist. */
function mapFirebaseError(code: string): string {
  switch (code) {
    case "auth/invalid-credential":
    case "auth/wrong-password":
      return "Incorrect email or password.";
    case "auth/user-not-found":
      return "No account found with that email address.";
    case "auth/invalid-email":
      return "That email address is not valid.";
    case "auth/user-disabled":
      return "This account has been disabled.";
    case "auth/too-many-requests":
      return "Too many failed attempts. Wait a few minutes and try again.";
    case "auth/network-request-failed":
      return "Network error. Check your internet connection.";
    default:
      return `Sign-in failed. (${code})`;
  }
}

export default function DirectorateLogin() {
  const { accessDenied } = useAuth();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) {
      setError("Enter your email and password.");
      return;
    }
    setLoading(true);
    setError(null);
    try {
      await signInWithEmailAndPassword(auth, email.trim(), password);
      // AuthProvider decides where to go — and signs out a non-directorate
      // account, surfacing accessDenied here.
    } catch (err) {
      const code = (err as { code?: string }).code || "unknown";
      setError(mapFirebaseError(code));
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="w-full max-w-md">
        <div className="mb-8 text-center">
          <span className="inline-block rounded-2xl bg-[#C8A84B]/10 p-4 text-4xl">🏛️</span>
          <h1 className="mt-4 text-2xl font-black uppercase tracking-widest text-[#E6C96E]">
            NEXLIB Directorate
          </h1>
          <p className="mt-1 text-xs text-slate-500">
            Higher Education Department · Khyber Pakhtunkhwa
          </p>
        </div>

        <form
          onSubmit={submit}
          className="rounded-2xl border border-blue-950 bg-[#070F1E] p-8 shadow-xl"
        >
          <h2 className="mb-6 text-lg font-bold text-white">Directorate sign in</h2>

          {accessDenied && (
            <div className="mb-4 rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-xs text-amber-200">
              {accessDenied}
            </div>
          )}

          {error && (
            <div className="mb-4 rounded-lg border border-red-500/30 bg-red-500/10 p-3 text-xs text-red-300">
              {error}
            </div>
          )}

          <label className="mb-1 block text-[10px] font-black uppercase tracking-widest text-slate-500">
            Email address
          </label>
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="username"
            className="mb-4 w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-3 py-2.5 text-sm text-slate-200 outline-none focus:border-[#C8A84B]"
          />

          <label className="mb-1 block text-[10px] font-black uppercase tracking-widest text-slate-500">
            Password
          </label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            className="mb-6 w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-3 py-2.5 text-sm text-slate-200 outline-none focus:border-[#C8A84B]"
          />

          <button
            type="submit"
            disabled={loading}
            className="w-full rounded-lg bg-[#C8A84B] px-4 py-2.5 text-sm font-bold text-[#1a1400] transition-colors hover:bg-[#E6C96E] disabled:opacity-40"
          >
            {loading ? "Signing in…" : "Sign in"}
          </button>

          <p className="mt-6 text-center text-[11px] leading-relaxed text-slate-600">
            This portal is for directorate accounts only. A college&apos;s own
            library login belongs in the college web portal, not here.
          </p>
        </form>
      </div>
    </div>
  );
}
