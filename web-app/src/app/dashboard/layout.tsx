"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { useSyncHealth } from "@/lib/firestore-hooks";
import { canViewDirectorate } from "@/lib/directorate";

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { profile, logout, loading } = useAuth();
  const sync = useSyncHealth();

  // The toggle writes the `dark` class onto <html>, so every page picks the
  // theme up through the tokens in globals.css. It used to live in this
  // component's state, which meant only the shell changed colour and every page
  // inside it stayed dark on a light frame.
  const [isDarkMode, setIsDarkMode] = useState(true);

  useEffect(() => {
    setIsDarkMode(document.documentElement.classList.contains("dark"));
  }, []);

  const toggleTheme = () => {
    const next = !isDarkMode;
    setIsDarkMode(next);
    document.documentElement.classList.toggle("dark", next);
    localStorage.setItem("web-theme", next ? "dark" : "light");
  };

  if (loading) {
    return (
      <div className="flex h-screen w-full items-center justify-center bg-app">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-accent border-t-transparent" />
      </div>
    );
  }

  // Windows Desktop App matching groups — all features active
  const navSections = [
    {
      title: "Core Services",
      items: [
        { name: "Dashboard", path: "/dashboard", icon: "🏠" },
        { name: "Gate Log", path: "/dashboard/gate-log", icon: "🛂" },
        { name: "Issue / Return", path: "/dashboard/transactions", icon: "📋" },
      ]
    },
    {
      title: "Inventory & Catalog",
      items: [
        { name: "Books", path: "/dashboard/books", icon: "📚" },
        { name: "Digital Library", path: "/dashboard/digital-library", icon: "🌐" },
        { name: "MARC Catalog", path: "/dashboard/marc-catalog", icon: "📑" },
        { name: "Classification", path: "/dashboard/classification", icon: "🗂️" },
        { name: "Spine Labels", path: "/dashboard/spine-labels", icon: "🏷️" },
        { name: "Inventory", path: "/dashboard/inventory", icon: "📦" },
        { name: "Acquisitions", path: "/dashboard/acquisitions", icon: "💰" },
        { name: "Serials", path: "/dashboard/serials", icon: "📰" },
        { name: "Wishlist", path: "/dashboard/wishlist", icon: "⭐" },
      ]
    },
    {
      title: "Patron Management",
      items: [
        { name: "Members", path: "/dashboard/members", icon: "👥" },
        { name: "Biometric", path: "/dashboard/biometric", icon: "🔐" },
        { name: "Union Catalogue", path: "/dashboard/union-catalog", icon: "🌐" },
        { name: "ILL Network", path: "/dashboard/sync", icon: "🌍" },
        { name: "Book Transfers", path: "/dashboard/book-transfers", icon: "🔄" },
        { name: "Digital ID Cards", path: "/dashboard/digital-id-cards", icon: "🪪" },
      ]
    },
    {
      title: "Analytics & Intelligence",
      items: [
        { name: "OPAC Monitor", path: "/dashboard/opac", icon: "🔍" },
        { name: "Reports", path: "/dashboard/reports", icon: "📊" },
        { name: "AI Recommender", path: "/dashboard/ai-recommender", icon: "🤖" },
        { name: "Usage Heatmap", path: "/dashboard/usage-heatmap", icon: "🔥" },
        { name: "Reading Goals", path: "/dashboard/reading-goals", icon: "🎯" },
      ]
    },
    {
      title: "Administration",
      items: [
        { name: "College Profile", path: "/dashboard/college-profile", icon: "🏛️" },
        { name: "Enterprise Feat.", path: "/dashboard/enterprise", icon: "🚀" },
        { name: "Fine Waiver AI", path: "/dashboard/fine-waiver-ai", icon: "⚖️" },
        { name: "Settings", path: "/dashboard/settings", icon: "⚙️" },
        // Shown only to director-level accounts; the portal itself re-checks.
        ...(canViewDirectorate(profile?.role)
          ? [{ name: "Directorate Portal", path: "/director", icon: "🏛️" }]
          : []),
      ]
    }
  ];

  // Honest sync indicator. This used to be a hardcoded green dot, so a session
  // whose writes were all being rejected still looked healthy.
  const syncIndicator = {
    live: { dot: "bg-positive", label: "Real-time sync active", tone: "text-muted" },
    connecting: { dot: "bg-warning animate-pulse", label: sync.online ? "Reconnecting…" : "Offline — changes queued", tone: "text-warning" },
    error: { dot: "bg-danger", label: "Sync error — check access", tone: "text-danger" },
    idle: { dot: "bg-line-strong", label: "No institution selected", tone: "text-muted" },
  }[sync.state];

  const currentTitle =
    navSections.flatMap((s) => s.items).find((i) => i.path === pathname)?.name ?? "Dashboard";

  return (
    <div className="flex h-screen overflow-hidden bg-app text-body">
      {/* Sidebar */}
      <aside className="flex w-64 flex-col justify-between overflow-y-auto overflow-x-hidden border-r border-line bg-surface">
        <div>
          <div className="flex items-center gap-3 border-b border-line p-6">
            <span className="rounded-xl bg-accent-soft p-2 text-2xl">📚</span>
            <div>
              <h1 className="font-bold leading-none tracking-tight text-ink">NEXLIB</h1>
              <span className="text-[10px] font-bold uppercase tracking-widest text-muted">
                Library Portal
              </span>
            </div>
          </div>
          <nav className="space-y-6 p-4">
            {navSections.map((section, idx) => (
              <div key={idx}>
                <h3 className="mb-2 px-4 text-[11px] font-bold uppercase tracking-wider text-muted">
                  {section.title}
                </h3>
                <div className="space-y-0.5">
                  {section.items.map((item) => {
                    const active = pathname === item.path;
                    return (
                      <Link
                        key={item.name}
                        href={item.path}
                        className={`flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                          active
                            ? "bg-accent-bg text-on-accent"
                            : "text-body hover:bg-surface-2 hover:text-ink"
                        }`}
                      >
                        <span className="text-base opacity-80">{item.icon}</span>
                        <span>{item.name}</span>
                      </Link>
                    );
                  })}
                </div>
              </div>
            ))}
          </nav>
        </div>

        {/* User card */}
        <div className="sticky bottom-0 border-t border-line bg-surface p-4">
          <div className="mb-3 flex items-center gap-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-accent-bg text-sm font-bold text-on-accent">
              {profile?.email?.[0]?.toUpperCase() || "U"}
            </div>
            <div className="overflow-hidden">
              <p className="truncate text-xs font-bold text-ink">{profile?.email?.split("@")[0]}</p>
              <p className="text-[10px] font-bold uppercase tracking-wider text-muted">
                {profile?.role}
              </p>
            </div>
          </div>
          <button
            onClick={logout}
            className="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-xs font-bold text-danger transition-colors hover:bg-danger-soft"
          >
            <span>🚪</span>
            <span>Sign Out</span>
          </button>
        </div>
      </aside>

      {/* Main content */}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-16 items-center justify-between border-b border-line bg-surface px-8">
          <div className="flex items-center gap-3">
            <h2 className="text-lg font-bold text-ink">{currentTitle}</h2>
            <div className="h-4 w-px bg-line" />
            {/* The institution id is on screen deliberately: when two clients
                disagree about the data, the first thing to check is whether
                they are pointed at the same tenant. */}
            <span className="font-mono text-xs text-muted">
              {profile?.institutionId || "no institution"}
            </span>
          </div>

          <div className="flex items-center gap-6">
            <div
              className="flex items-center gap-2"
              title={
                sync.error ||
                (sync.lastSyncAt
                  ? `Last server update ${new Date(sync.lastSyncAt).toLocaleTimeString()}`
                  : undefined)
              }
            >
              <span className={`h-2 w-2 rounded-full ${syncIndicator.dot}`} />
              <span className={`text-[11px] font-semibold ${syncIndicator.tone}`}>
                {syncIndicator.label}
              </span>
            </div>

            <button
              onClick={toggleTheme}
              aria-label={isDarkMode ? "Switch to light theme" : "Switch to dark theme"}
              className="rounded-lg border border-line px-2.5 py-1.5 text-sm transition-colors hover:border-accent"
            >
              {isDarkMode ? "☀️" : "🌙"}
            </button>
          </div>
        </header>

        <main className="flex-1 overflow-y-auto bg-app p-8">
          <div className="mx-auto max-w-7xl">{children}</div>
        </main>
      </div>
    </div>
  );
}
