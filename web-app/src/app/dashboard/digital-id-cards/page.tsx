"use client";
import React, { useState } from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";
import { useInstitutionProfile } from "@/lib/settings";

export default function DigitalIdCardsPage() {
  // The card must carry the signed-in college's own name, not a fixed one.
  const { institution } = useInstitutionProfile();
  const { data: members, loading } = useTenantCollection("members");
  const [search, setSearch] = useState("");
  const [selected, setSelected] = useState<any>(null);

  const filtered = (members ?? []).filter(m => {
    const q = search.toLowerCase();
    return !q || (m.name || "").toLowerCase().includes(q) || (m.email || "").toLowerCase().includes(q);
  });

  const handlePrint = () => {
    window.print();
  };

  const handleDownload = () => {
    if (!selected) return;
    // Create a simple SVG ID card and trigger download
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="420" height="270">
      <defs><linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop offset="0%" stop-color="#0F1E3D"/><stop offset="50%" stop-color="#1A3A6E"/><stop offset="100%" stop-color="#0F1E3D"/></linearGradient></defs>
      <rect width="420" height="270" rx="18" fill="url(#bg)" stroke="#C8A84B" stroke-width="2"/>
      <text x="24" y="44" fill="#C8A84B" font-size="22" font-family="Arial" font-weight="bold">📚 ${institution.libraryName.toUpperCase()}</text>
      <text x="24" y="64" fill="#94A3B8" font-size="11" font-family="Arial">${institution.name}</text>
      <line x1="20" y1="78" x2="400" y2="78" stroke="#C8A84B" stroke-opacity="0.4" stroke-width="1"/>
      <text x="24" y="115" fill="white" font-size="18" font-family="Arial" font-weight="bold">${selected.name}</text>
      <text x="24" y="138" fill="#94A3B8" font-size="12" font-family="Arial">MEMBER ID: ${selected.id?.slice(0, 8).toUpperCase()}</text>
      <text x="24" y="158" fill="#94A3B8" font-size="12" font-family="Arial">ROLE: ${selected.role || "Student"}</text>
      <text x="24" y="178" fill="#94A3B8" font-size="12" font-family="Arial">EMAIL: ${selected.email || "—"}</text>
      <rect x="300" y="90" width="100" height="100" rx="8" fill="white"/>
      <text x="350" y="150" fill="#666" font-size="10" font-family="Arial" text-anchor="middle">QR CODE</text>
      <rect x="20" y="240" width="380" height="4" rx="2" fill="#C8A84B" fill-opacity="0.4"/>
      <rect x="370" y="90" width="30" height="8" rx="4" fill="#059669"/>
      <text x="385" y="97" fill="white" font-size="7" font-family="Arial" text-anchor="middle">ACTIVE</text>
    </svg>`;
    const blob = new Blob([svg], { type: "image/svg+xml" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a"); a.href = url; a.download = `ID_Card_${selected.name?.replace(/ /g, "_")}.svg`; a.click();
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-500 max-w-6xl mx-auto">
      <div>
        <h1 className="text-3xl font-extrabold text-[#E8EEF8]">🪪 Digital ID Cards</h1>
        <p className="text-sm text-slate-400">Generate and print institutional library ID cards for all members</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Member List */}
        <div className="lg:col-span-1 rounded-xl border border-blue-950 bg-[#070F1E] p-6 shadow-xl">
          <h3 className="text-sm font-bold uppercase tracking-wider text-slate-400 mb-3">👥 Select Member</h3>
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Search by name or email..."
            className="w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2.5 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B] mb-3"
          />
          {loading ? <div className="flex justify-center py-8"><div className="h-6 w-6 animate-spin rounded-full border-4 border-[#C8A84B] border-t-transparent" /></div> : (
            <div className="space-y-1 max-h-[60vh] overflow-y-auto">
              {filtered.map(m => (
                <button
                  key={m.id}
                  onClick={() => setSelected(m)}
                  className={`w-full text-left px-4 py-3 rounded-xl text-sm transition-all ${selected?.id === m.id ? "bg-blue-600 text-white font-bold" : "text-slate-300 hover:bg-[#0D1F38]"}`}
                >
                  <p className="font-bold">{m.name}</p>
                  <p className="text-xs opacity-70">{m.role || "Student"}</p>
                </button>
              ))}
              {filtered.length === 0 && <p className="text-center text-slate-500 py-8 text-sm">No members found.</p>}
            </div>
          )}
        </div>

        {/* ID Card Preview */}
        <div className="lg:col-span-2 space-y-6">
          <div className="text-center">
            <h2 className="text-lg font-bold text-[#C8A84B] uppercase tracking-widest mb-4">🪪 Digital ID Card Preview</h2>
          </div>

          {/* The Card */}
          <div id="id-card-printable" className="mx-auto w-[420px] h-[270px] rounded-[18px] border-2 border-[#C8A84B] shadow-2xl shadow-blue-900/30 relative overflow-hidden"
            style={{ background: "linear-gradient(135deg, #0F1E3D 0%, #1A3A6E 50%, #0F1E3D 100%)" }}>
            {/* Header */}
            <div className="flex items-center gap-3 px-5 pt-4 pb-3 border-b border-[#C8A84B]/30">
              <span className="text-2xl">📚</span>
              <div>
                <p className="font-extrabold text-[#C8A84B] text-sm tracking-wide">{institution.libraryName.toUpperCase()}</p>
                <p className="text-[10px] text-slate-400">{institution.name}</p>
              </div>
              <div className="ml-auto">
                <span className="bg-emerald-600 text-white text-[9px] font-black px-2 py-0.5 rounded-full">ACTIVE</span>
              </div>
            </div>

            {/* Body */}
            <div className="flex px-5 pt-4 gap-4">
              <div className="flex-1 space-y-1.5">
                <p className="font-black text-white text-base">{selected?.name || "Select a member..."}</p>
                <p className="text-[11px] text-slate-400">MEMBER ID: {selected?.id?.slice(0, 8).toUpperCase() || "—"}</p>
                <p className="text-[11px] text-slate-400">ROLE: {selected?.role || "—"}</p>
                <p className="text-[11px] text-slate-400">EMAIL: {selected?.email || "—"}</p>
                <p className="text-[11px] text-slate-400">PHONE: {selected?.phone || "—"}</p>
              </div>
              {/* QR Placeholder */}
              <div className="w-24 h-24 bg-white rounded-lg flex items-center justify-center flex-shrink-0 border-2 border-[#C8A84B]">
                <div className="text-center">
                  <div className="grid grid-cols-5 gap-0.5 p-1">
                    {Array.from({length: 25}).map((_, i) => (
                      <div key={i} className={`w-2 h-2 ${Math.random() > 0.5 ? "bg-black" : "bg-white"}`} />
                    ))}
                  </div>
                </div>
              </div>
            </div>

            {/* Footer bar */}
            <div className="absolute bottom-0 left-0 right-0 h-1.5 bg-gradient-to-r from-[#C8A84B]/30 via-[#C8A84B] to-[#C8A84B]/30" />
          </div>

          {/* Action Buttons */}
          <div className="flex gap-3 justify-center">
            <button onClick={handleDownload} disabled={!selected} className="px-6 py-2.5 rounded-lg bg-[#1E3050] text-slate-200 font-bold text-sm hover:bg-[#2A4166] disabled:opacity-50 transition-colors">
              💾 Save Card (SVG)
            </button>
            <button onClick={handlePrint} disabled={!selected} className="px-6 py-2.5 rounded-lg bg-blue-600 text-white font-bold text-sm hover:bg-blue-500 disabled:opacity-50 transition-colors shadow">
              🖨️ Print ID Card
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
