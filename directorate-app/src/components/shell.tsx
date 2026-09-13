"use client";

// components/shell.tsx
//
// The portal chrome. Renders nothing but the page on /login (which has its own
// full-screen layout), and gates everything else behind a directorate session.

import React from "react";
import { usePathname } from "next/navigation";
import { useAuth } from "@/lib/auth-context";

export default function Shell({ children }: { children: React.ReactNode }) {
  const { profile, loading, logout } = useAuth();
  const pathname = usePathname();

  if (pathname === "/login") return <>{children}</>;

  if (loading || !profile) {
    return (
      <div className="flex h-screen items-center justify-center">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-[#C8A84B] border-t-transparent" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#070D18] text-slate-100">
      <header className="border-b border-blue-950/70 bg-[#050B14]">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-4 px-6 py-4">
          <div className="flex items-center gap-3">
            <span className="rounded-xl bg-[#C8A84B]/10 p-2 text-2xl">🏛️</span>
            <div>
              <h1 className="text-sm font-black uppercase tracking-widest text-[#E6C96E]">
                NEXLIB Directorate
              </h1>
              <p className="text-[11px] text-slate-500">
                Higher Education Department · Khyber Pakhtunkhwa
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <div className="text-right">
              <p className="text-xs font-bold text-white">{profile.email.split("@")[0]}</p>
              <p className="text-[10px] uppercase tracking-wider text-[#C8A84B]">
                {profile.role}
              </p>
            </div>
            <button
              onClick={logout}
              className="rounded-lg px-2 py-1.5 text-xs font-bold text-red-400 hover:bg-red-500/10"
            >
              Sign out
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-6 py-8">{children}</main>

      <footer className="mx-auto max-w-7xl px-6 pb-10 pt-4 text-[11px] text-slate-600">
        Read-only aggregated view. Figures are published by each college&apos;s own
        app and reflect that college&apos;s most recent sync.
      </footer>
    </div>
  );
}
