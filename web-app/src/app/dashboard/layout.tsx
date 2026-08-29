"use client";

import React, { useState, useEffect } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useAuth } from "@/lib/auth-context";
import { db } from "@/lib/firebase";
import { doc, updateDoc } from "firebase/firestore";

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { user, profile, logout, loading, refreshProfile } = useAuth();
  const [isDarkMode, setIsDarkMode] = useState(true);

  useEffect(() => {
    const savedTheme = localStorage.getItem("web-theme");
    if (savedTheme) {
      setIsDarkMode(savedTheme === "dark");
    }
  }, []);

  const toggleTheme = () => {
    const newMode = !isDarkMode;
    setIsDarkMode(newMode);
    localStorage.setItem("web-theme", newMode ? "dark" : "light");
  };

  const switchToDesktopData = async () => {
    if (!user) return;
    try {
      await updateDoc(doc(db, "users", user.uid), {
        institutionId: "gdc11"
      });
      await refreshProfile();
      alert("Switched to Desktop App Data (gdc11)!");
      window.location.reload();
    } catch (e) {
      console.error(e);
      alert("Failed to switch database.");
    }
  };

  if (loading) {
    return (
      <div className="flex h-screen w-full items-center justify-center bg-[#050B14]">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-[#C8A84B] border-t-transparent" />
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
        { name: "Serials", path: "#", icon: "📰" },
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
      ]
    }
  ];

  return (
    <div className={`flex h-screen transition-colors duration-300 ${isDarkMode ? "bg-[#111827] text-slate-100" : "bg-[#FAFAFA] text-slate-800"} overflow-hidden`}>
      {/* Sidebar */}
      <aside className={`w-64 border-r flex flex-col justify-between transition-colors duration-300 overflow-y-auto overflow-x-hidden ${
        isDarkMode ? "bg-[#0F1524] border-[#1E2638]" : "bg-white border-slate-200"
      }`}>
        <div>
          <div className="p-6 border-b border-white/10 flex items-center gap-3">
            <span className="text-3xl bg-blue-600/20 p-2 rounded-xl">📚</span>
            <div>
              <h1 className={`font-bold leading-none tracking-tight ${isDarkMode ? "text-white" : "text-blue-900"}`}>GDC Library</h1>
              <span className={`text-[10px] tracking-widest uppercase font-bold ${isDarkMode ? "text-blue-200/50" : "text-blue-600/70"}`}>Web Portal</span>
            </div>
          </div>
          <nav className="p-4 space-y-6">
            {navSections.map((section, idx) => (
              <div key={idx}>
                <h3 className={`text-[11px] font-bold uppercase tracking-wider mb-2 px-4 ${isDarkMode ? "text-slate-500" : "text-slate-400"}`}>
                  {section.title}
                </h3>
                <div className="space-y-1">
                  {section.items.map((item) => {
                    const active = pathname === item.path;
                    const isDisabled = item.path === "#";
                    return (
                      <Link
                        key={item.name}
                        href={item.path}
                        onClick={(e) => { if(isDisabled) e.preventDefault(); }}
                        className={`flex items-center gap-3 px-4 py-2.5 rounded-xl text-sm font-semibold transition-all ${
                          active 
                            ? "bg-blue-600 text-white shadow-lg shadow-blue-600/20"
                            : isDisabled
                              ? (isDarkMode ? "text-slate-600 cursor-not-allowed" : "text-slate-300 cursor-not-allowed")
                              : (isDarkMode ? "text-blue-100/60 hover:bg-slate-800 hover:text-white" : "text-slate-500 hover:bg-blue-50 hover:text-blue-700")
                        }`}
                      >
                        <span className="text-lg opacity-80">{item.icon}</span>
                        <span>{item.name}</span>
                        {isDisabled && (
                          <span className={`ml-auto text-[9px] px-1.5 py-0.5 rounded border ${isDarkMode ? "border-slate-700 text-slate-500" : "border-slate-200 text-slate-400"}`}>WIP</span>
                        )}
                      </Link>
                    );
                  })}
                </div>
              </div>
            ))}
          </nav>
        </div>

        {/* User Card */}
        <div className={`p-4 border-t sticky bottom-0 transition-colors duration-300 ${isDarkMode ? "border-[#1E2638] bg-[#0A1428]" : "border-slate-200 bg-slate-50"}`}>
          <div className="flex items-center gap-3 mb-4">
            <div className="h-9 w-9 rounded-full bg-gradient-to-tr from-blue-600 to-indigo-600 flex items-center justify-center font-bold text-white text-sm shadow-inner">
              {profile?.email?.[0]?.toUpperCase() || "U"}
            </div>
            <div className="overflow-hidden">
              <p className={`text-xs font-bold truncate ${isDarkMode ? "text-white" : "text-slate-800"}`}>{profile?.email?.split('@')[0]}</p>
              <p className={`text-[10px] font-bold uppercase tracking-wider opacity-70 ${isDarkMode ? "text-blue-300" : "text-blue-600"}`}>
                {profile?.role}
              </p>
            </div>
          </div>
          <button
            onClick={logout}
            className={`w-full text-left flex items-center gap-2 px-3 py-2 text-xs font-bold rounded-lg transition-colors ${
              isDarkMode ? "text-red-400 hover:bg-red-500/10" : "text-red-500 hover:bg-red-50"
            }`}
          >
            <span>🚪</span>
            <span>Sign Out</span>
          </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col min-w-0">
        <header className={`h-16 border-b flex items-center justify-between px-8 transition-colors duration-300 ${
          isDarkMode ? "bg-[#1F2937] border-[#1E2638]" : "bg-white border-slate-200 shadow-sm"
        }`}>
          <div className="flex items-center gap-3">
            <h2 className={`font-bold text-lg ${isDarkMode ? "text-white" : "text-slate-800"}`}>
              {navSections.flatMap(s => s.items).find(i => i.path === pathname)?.name || "Dashboard"}
            </h2>
            <div className={`h-4 w-[1px] ${isDarkMode ? "bg-slate-700" : "bg-slate-200"}`} />
            <span className="text-xs font-bold text-blue-500 uppercase tracking-widest">
              Inst-ID: {profile?.institutionId || "..."}
            </span>
            {profile?.institutionId !== "gdc11" && (
              <button 
                onClick={switchToDesktopData}
                className="ml-2 bg-emerald-600 text-white px-2 py-1 text-[10px] rounded font-bold uppercase hover:bg-emerald-500"
              >
                Sync with Desktop (gdc11)
              </button>
            )}
          </div>

          <div className="flex items-center gap-6">
            <div className="flex items-center gap-2">
              <span className={`h-2 w-2 rounded-full bg-green-500 animate-pulse`} />
              <span className={`text-[10px] font-bold uppercase tracking-tighter ${isDarkMode ? "text-slate-400" : "text-slate-500"}`}>
                Real-time Sync Active
              </span>
            </div>

            <button
              onClick={toggleTheme}
              className={`p-2 rounded-xl transition-all active:scale-95 ${
                isDarkMode ? "bg-slate-800 text-yellow-400 hover:bg-slate-700" : "bg-slate-100 text-slate-600 hover:bg-slate-200"
              }`}
            >
              {isDarkMode ? "☀️" : "🌙"}
            </button>
          </div>
        </header>
        <main className={`flex-1 overflow-y-auto p-8 transition-colors duration-300 ${
          isDarkMode ? "bg-[#111827]" : "bg-[#FAFAFA]"
        }`}>
          <div className="max-w-7xl mx-auto">
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
