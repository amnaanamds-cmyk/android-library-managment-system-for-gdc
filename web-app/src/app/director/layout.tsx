"use client";

import React from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { canViewDirectorate } from "@/lib/directorate";

/**
 * Directorate portal shell.
 *
 * Deliberately separate from /dashboard: that route is a college's own
 * workspace and every page there is scoped to `profile.institutionId`. The
 * directorate works across colleges and never writes tenant data, so it gets
 * its own read-only shell with its own access gate.
 */
export default function DirectorLayout({ children }: { children: React.ReactNode }) {
  const { profile, loading, logout } = useAuth();
  const pathname = usePathname();

  if (loading) {
    return (
      <div className="flex h-screen items-center justify-center bg-app">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-accent border-t-transparent" />
      </div>
    );
  }

  if (!canViewDirectorate(profile?.role)) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-app px-4">
        <div className="max-w-md rounded-2xl border border-danger/30 bg-danger/5 p-8 text-center">
          <div className="text-5xl">🔒</div>
          <h1 className="mt-4 text-xl font-bold text-ink">Directorate access required</h1>
          <p className="mt-2 text-sm text-muted">
            Your account is signed in as{" "}
            <span className="font-mono text-body">{profile?.role || "unknown"}</span>. The
            directorate portal is limited to director and directorate administrator accounts.
          </p>
          <p className="mt-4 text-xs text-muted">
            This portal is limited to the <span className="font-mono text-muted">directorate</span>{" "}
            role. A college&apos;s own <span className="font-mono">director</span> or{" "}
            <span className="font-mono">admin</span> administers that college only — it is not
            province-wide oversight, and the security rules draw the same line.
          </p>
          <p className="mt-2 text-xs text-muted">
            To grant access, an existing directorate account calls the{" "}
            <span className="font-mono">setUserRole</span> function with{" "}
            <span className="font-mono">role: &quot;directorate&quot;</span>. Bootstrapping the
            first one is described in DEPLOYMENT.md.
          </p>
          <div className="mt-6 flex justify-center gap-3">
            <Link
              href="/dashboard"
              className="rounded-lg bg-accent-bg px-4 py-2 text-xs font-bold text-on-accent hover:bg-accent-bg"
            >
              Back to my library
            </Link>
            <button
              onClick={logout}
              className="rounded-lg border border-line px-4 py-2 text-xs font-bold text-body hover:bg-surface-2"
            >
              Sign out
            </button>
          </div>
        </div>
      </div>
    );
  }

  const tabs = [
    { name: "Network Overview", href: "/director" },
    { name: "Onboard College", href: "/director/register" },
  ];

  return (
    <div className="min-h-screen bg-[#070D18] text-body">
      <header className="border-b border-line/70 bg-app">
        <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
          <div className="flex items-center gap-3">
            <span className="rounded-xl bg-accent-bg/10 p-2 text-2xl">🏛️</span>
            <div>
              <h1 className="text-sm font-black uppercase tracking-widest text-accent-strong">
                NEXLIB Directorate
              </h1>
              <p className="text-[11px] text-muted">
                Higher Education Department · Khyber Pakhtunkhwa
              </p>
            </div>
          </div>

          <div className="flex items-center gap-6">
            <nav className="flex gap-1">
              {tabs.map((tab) => {
                const active = pathname === tab.href;
                return (
                  <Link
                    key={tab.href}
                    href={tab.href}
                    className={`rounded-lg px-3 py-2 text-xs font-bold transition-colors ${
                      active
                        ? "bg-accent-bg text-on-accent"
                        : "text-muted hover:bg-surface-2 hover:text-on-accent"
                    }`}
                  >
                    {tab.name}
                  </Link>
                );
              })}
            </nav>
            <div className="flex items-center gap-3 border-l border-line pl-6">
              <div className="text-right">
                <p className="text-xs font-bold text-ink">{profile?.email?.split("@")[0]}</p>
                <p className="text-[10px] uppercase tracking-wider text-accent">
                  {profile?.role}
                </p>
              </div>
              <button
                onClick={logout}
                className="rounded-lg px-2 py-1.5 text-xs font-bold text-danger hover:bg-danger/10"
              >
                Sign out
              </button>
            </div>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-6 py-8">{children}</main>

      <footer className="mx-auto max-w-7xl px-6 pb-10 pt-4 text-[11px] text-muted">
        Read-only aggregated view. Figures are computed server-side by the scheduled rollup from
        each college&apos;s own data; no patron-level record leaves the college that owns it.
      </footer>
    </div>
  );
}
