"use client";

import React from "react";
import { useAuth } from "@/lib/auth-context";

export default function CollegeProfilePage() {
  const { profile } = useAuth();

  return (
    <div className="space-y-6 animate-in fade-in duration-500 max-w-4xl mx-auto">
      <div>
        <h1 className="text-3xl font-extrabold text-[#E8EEF8]">🏛️ College Profile</h1>
        <p className="text-sm text-slate-400">Manage institutional details and system branding</p>
      </div>

      <div className="rounded-xl border border-blue-950 bg-[#070F1E] p-8 shadow-xl">
        <div className="flex items-start gap-8">
          <div className="w-32 h-32 rounded-xl bg-[#0D1F38] border-2 border-dashed border-[#1E3050] flex flex-col items-center justify-center text-slate-500 cursor-pointer hover:border-[#C8A84B] transition-colors">
            <span className="text-3xl mb-2">📸</span>
            <span className="text-xs font-bold">Upload Logo</span>
          </div>
          
          <div className="flex-1 space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-slate-400">Institution Name</label>
                <input
                  type="text"
                  defaultValue="Govt. Degree College (GDC11)"
                  className="mt-2 w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2.5 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]"
                />
              </div>
              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-slate-400">Library Name</label>
                <input
                  type="text"
                  defaultValue="Central Digital Library"
                  className="mt-2 w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2.5 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]"
                />
              </div>
            </div>
            
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-slate-400">Contact Email</label>
              <input
                type="email"
                defaultValue={profile?.email || "admin@gdc11.edu"}
                className="mt-2 w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2.5 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]"
              />
            </div>

            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-slate-400">Address / Location</label>
              <textarea
                rows={3}
                defaultValue="Main Campus, Library Block, City Center."
                className="mt-2 w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2.5 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B] resize-none"
              />
            </div>

            <div className="pt-4">
              <button className="rounded-lg bg-emerald-600 px-6 py-2.5 text-sm font-bold text-white transition-all hover:bg-emerald-500 shadow-lg">
                Save Profile Configuration
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
