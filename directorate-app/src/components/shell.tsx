"use client";

// components/shell.tsx
//
// The portal chrome: a sidebar + top bar MIS layout, replacing the original
// single always-on-page-one dashboard. Renders nothing but the page on
// /login (which has its own full-screen layout), and gates everything else
// behind a directorate session.

import React, { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { useDirectorateNetwork } from "@/lib/directorate";
import { computeAlerts } from "@/lib/analytics";
import { useFollowups } from "@/lib/registry-admin";
import { useMyTier, TIER_LABEL } from "@/lib/staff";
import Sidebar, { useNavGroups } from "./sidebar";
import { IconBuilding, IconLogout, IconChevronDown } from "./icons";
import { Spinner } from "./ui";

export default function Shell({ children }: { children: React.ReactNode }) {
  const { profile, loading, logout } = useAuth();
  const pathname = usePathname();

  if (pathname === "/login") return <>{children}</>;

  if (loading || !profile) {
    return (
      <div className="flex h-screen items-center justify-center bg-[#050B14]">
        <Spinner label="Authenticating…" />
      </div>
    );
  }

  return <AuthedShell profile={profile} logout={logout}>{children}</AuthedShell>;
}

function AuthedShell({
  profile,
  logout,
  children,
}: {
  profile: { email: string; role: string };
  logout: () => Promise<void>;
  children: React.ReactNode;
}) {
  const { pending, totals, colleges } = useDirectorateNetwork();
  const alerts = computeAlerts(colleges);
  const { items: followups } = useFollowups();
  const tier = useMyTier();
  const [mobileOpen, setMobileOpen] = useState(false);

  const counts = {
    pending: pending.length,
    alerts: alerts.length,
    openFollowups: followups.filter((f) => f.status === "open").length,
  };
  const groups = useNavGroups(counts);
  const pathname = usePathname();
  const currentLabel = groups.flatMap((g) => g.items).find((i) => i.href === pathname)?.label || "Overview";

  return (
    <div className="flex min-h-screen bg-[#050B14] text-slate-100">
      <Sidebar counts={counts} />

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="border-b border-slate-800 bg-[#050B14]/95 backdrop-blur">
          <div className="flex items-center justify-between gap-4 px-5 py-3.5">
            <div className="flex items-center gap-3">
              <button
                onClick={() => setMobileOpen((v) => !v)}
                className="rounded-md border border-slate-800 p-1.5 text-slate-400 lg:hidden"
                aria-label="Toggle navigation"
              >
                <IconBuilding className="h-4 w-4" />
              </button>
              <div>
                <p className="text-[10px] font-bold uppercase tracking-widest text-slate-600">
                  NEXLIB MIS · Higher Education Department
                </p>
                <h2 className="text-sm font-bold text-white lg:hidden">{currentLabel}</h2>
                <p className="hidden text-xs text-slate-500 lg:block">
                  {totals.colleges} approved {totals.colleges === 1 ? "institution" : "institutions"} in network
                </p>
              </div>
            </div>

            <div className="flex items-center gap-3">
              <div className="hidden text-right sm:block">
                <p className="text-xs font-semibold text-white">{profile.email}</p>
                <p className="text-[10px] uppercase tracking-wide text-amber-500">{TIER_LABEL[tier]}</p>
              </div>
              <button
                onClick={logout}
                className="flex items-center gap-1.5 rounded-md border border-slate-800 px-2.5 py-1.5 text-xs font-semibold text-slate-400 transition-colors hover:border-red-500/40 hover:text-red-400"
              >
                <IconLogout className="h-3.5 w-3.5" />
                <span className="hidden sm:inline">Sign out</span>
              </button>
            </div>
          </div>

          {/* Mobile nav: a horizontal scroll strip, since the sidebar is
              hidden below the lg breakpoint. */}
          {mobileOpen && (
            <div className="flex gap-1 overflow-x-auto border-t border-slate-800 px-3 py-2 lg:hidden">
              {groups.flatMap((g) => g.items).map((item) => (
                <Link
                  key={item.href}
                  href={item.href}
                  onClick={() => setMobileOpen(false)}
                  className={`whitespace-nowrap rounded-md px-3 py-1.5 text-xs font-semibold ${
                    pathname === item.href ? "bg-amber-500/10 text-amber-400" : "text-slate-400"
                  }`}
                >
                  {item.label}
                </Link>
              ))}
            </div>
          )}
        </header>

        <main className="mx-auto w-full max-w-7xl flex-1 px-5 py-7">{children}</main>

        <footer className="border-t border-slate-800 px-5 py-4 text-[11px] text-slate-600">
          Read-only aggregated view. Figures are published by each college&apos;s own app and reflect
          that college&apos;s most recent sync.
        </footer>
      </div>
    </div>
  );
}

// Re-exported so pages needing the chevron in a locally-built dropdown do
// not need to import components/icons directly for this one glyph.
export { IconChevronDown };
