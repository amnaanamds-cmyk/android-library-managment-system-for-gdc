"use client";

import React from "react";
import { useAuth } from "@/lib/auth-context";
import { useDirectorColleges } from "@/lib/firestore-hooks";

export default function DirectoratePage() {
  const { profile } = useAuth();
  const { colleges, loading } = useDirectorColleges();

  // Only authorized profiles (directorate_admin, director, owner) can read
  const isAuthorized =
    profile?.role === "directorate_admin" ||
    profile?.role === "director" ||
    profile?.role === "owner";

  if (!isAuthorized) {
    return (
      <div className="rounded-xl border border-red-500/30 bg-red-500/10 p-6 text-[#F08080]">
        <h3 className="font-bold text-lg">Access Denied</h3>
        <p className="text-sm mt-1">Unauthorized access. You do not have Directorate access permissions.</p>
      </div>
    );
  }

  const grandTotalBooks = colleges.reduce((acc, m) => acc + (m.booksCount || 0), 0);
  const grandTotalMembers = colleges.reduce((acc, m) => acc + (m.membersCount || 0), 0);
  const grandActiveLoans = colleges.reduce((acc, m) => acc + (m.circulationCount || 0), 0);

  return (
    <div className="space-y-8 animate-in fade-in duration-500">
      <div className="flex justify-between items-end">
        <div>
          <h1 className="text-3xl font-extrabold text-white tracking-tight">Directorate Network</h1>
          <p className="text-sm text-slate-400 mt-1">Live aggregate oversight across all managed library colleges</p>
        </div>
        <div className="flex gap-2">
           <button className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white rounded-lg text-xs font-bold transition-all shadow-lg shadow-blue-600/20">
             + Create New College
           </button>
        </div>
      </div>

      {/* Aggregate Overview Cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
        <StatSummary title="Active Colleges" value={colleges.length} color="text-yellow-400" icon="🏢" />
        <StatSummary title="Grand Total Books" value={grandTotalBooks} color="text-white" icon="📚" />
        <StatSummary title="Grand Total Members" value={grandTotalMembers} color="text-white" icon="👥" />
        <StatSummary title="System Active Loans" value={grandActiveLoans} color="text-blue-400" icon="🔄" />
      </div>

      {/* Colleges Detail Table */}
      <div className="rounded-2xl border border-blue-950 bg-[#070F1E] p-6 shadow-xl overflow-hidden">
        <div className="flex items-center justify-between mb-6">
          <h3 className="text-lg font-bold text-white">College Registry Status</h3>
          <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest bg-slate-900 px-3 py-1 rounded-full">
            Real-time Monitoring
          </span>
        </div>

        {loading ? (
          <div className="py-12 flex flex-col items-center gap-4">
            <div className="h-10 w-10 animate-spin rounded-full border-4 border-blue-600 border-t-transparent" />
            <p className="text-xs text-slate-500 font-bold uppercase tracking-wider">Loading Network Data...</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-slate-300">
              <thead className="text-[10px] uppercase bg-blue-950/30 text-blue-400 font-black tracking-widest">
                <tr>
                  <th className="px-6 py-4">Institution</th>
                  <th className="px-6 py-4">ID & Code</th>
                  <th className="px-6 py-4 text-center">Books</th>
                  <th className="px-6 py-4 text-center">Members</th>
                  <th className="px-6 py-4 text-center">Live Circulation</th>
                  <th className="px-6 py-4 text-right">Last Sync</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-blue-950/30">
                {colleges.map((m) => (
                  <tr key={m.id} className="hover:bg-blue-950/10 transition-colors group">
                    <td className="px-6 py-5">
                      <div className="flex items-center gap-3">
                        <div className="w-8 h-8 rounded-lg bg-blue-600/10 flex items-center justify-center text-blue-500 font-black text-xs">
                          {m.collegeName?.[0] || 'C'}
                        </div>
                        <span className="font-bold text-white group-hover:text-blue-400 transition-colors">{m.collegeName}</span>
                      </div>
                    </td>
                    <td className="px-6 py-5">
                      <div className="flex flex-col">
                        <span className="font-mono text-[10px] text-slate-500 uppercase">ID: {m.collegeId?.substring(0, 8)}...</span>
                        <span className="font-bold text-[#E6C96E] text-xs">Invite: {m.inviteCode}</span>
                      </div>
                    </td>
                    <td className="px-6 py-5 text-center font-bold text-white">{m.booksCount || 0}</td>
                    <td className="px-6 py-5 text-center">{m.membersCount || 0}</td>
                    <td className="px-6 py-5 text-center">
                      <span className="px-3 py-1 rounded-full text-[10px] bg-blue-950 text-blue-400 border border-blue-800 font-black uppercase tracking-tighter">
                        {m.circulationCount || 0} active
                      </span>
                    </td>
                    <td className="px-6 py-5 text-right">
                       <span className="text-[10px] text-slate-500 font-bold uppercase">
                         {m.lastSyncAt ? new Date(m.lastSyncAt).toLocaleTimeString() : 'Never'}
                       </span>
                    </td>
                  </tr>
                ))}
                {colleges.length === 0 && (
                  <tr>
                    <td colSpan={6} className="text-center py-16">
                      <div className="flex flex-col items-center gap-2 opacity-30">
                        <span className="text-4xl">🏢</span>
                        <p className="text-sm font-bold uppercase tracking-wider text-slate-400">No Colleges Linked to this account</p>
                      </div>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

function StatSummary({ title, value, color, icon }: { title: string, value: number, color: string, icon: string }) {
  return (
    <div className="rounded-2xl border border-blue-950 bg-[#070F1E] p-6 shadow-xl relative overflow-hidden group hover:border-blue-800 transition-all">
      <div className="absolute -right-4 -bottom-4 text-6xl opacity-5 group-hover:scale-110 transition-transform duration-500">{icon}</div>
      <p className="text-[10px] font-black uppercase tracking-widest text-slate-500 mb-2">{title}</p>
      <h2 className={`text-4xl font-black ${color} tracking-tighter`}>{value.toLocaleString()}</h2>
    </div>
  );
}
