"use client";

import React from "react";
import { useAuth } from "@/lib/auth-context";

export default function CollegeProfilePage() {
  const { profile } = useAuth();

  return (
    <div className="space-y-6 animate-in fade-in duration-500 max-w-4xl mx-auto">
      <div>
        <h1 className="text-3xl font-extrabold text-ink">🏛️ College Profile</h1>
        <p className="text-sm text-muted">Manage institutional details and system branding</p>
      </div>

      <div className="rounded-xl border border-line bg-surface-2 p-8 shadow-xl">
        <div className="flex items-start gap-8">
          <div className="w-32 h-32 rounded-xl bg-surface border-2 border-dashed border-line flex flex-col items-center justify-center text-muted cursor-pointer hover:border-accent transition-colors">
            <span className="text-3xl mb-2">📸</span>
            <span className="text-xs font-bold">Upload Logo</span>
          </div>
          
          <div className="flex-1 space-y-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-muted">Institution Name</label>
                <input
                  type="text"
                  defaultValue="Govt. Degree College (GDC11)"
                  className="mt-2 w-full rounded-lg border border-line bg-surface px-4 py-2.5 text-sm text-ink outline-none focus:border-accent"
                />
              </div>
              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-muted">Library Name</label>
                <input
                  type="text"
                  defaultValue="Central Digital Library"
                  className="mt-2 w-full rounded-lg border border-line bg-surface px-4 py-2.5 text-sm text-ink outline-none focus:border-accent"
                />
              </div>
            </div>
            
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-muted">Contact Email</label>
              <input
                type="email"
                defaultValue={profile?.email || "admin@gdc11.edu"}
                className="mt-2 w-full rounded-lg border border-line bg-surface px-4 py-2.5 text-sm text-ink outline-none focus:border-accent"
              />
            </div>

            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-muted">Address / Location</label>
              <textarea
                rows={3}
                defaultValue="Main Campus, Library Block, City Center."
                className="mt-2 w-full rounded-lg border border-line bg-surface px-4 py-2.5 text-sm text-ink outline-none focus:border-accent resize-none"
              />
            </div>

            <div className="pt-4">
              <button className="rounded-lg bg-positive px-6 py-2.5 text-sm font-bold text-on-accent transition-all hover:bg-positive shadow-lg">
                Save Profile Configuration
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
