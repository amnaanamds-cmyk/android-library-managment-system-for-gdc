"use client";

import React, { useState, useRef } from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";

export default function GateLogPage() {
  const { data: logs, loading, addRecord, updateRecord } = useTenantCollection("visitor_log");
  const [entryInput, setEntryInput] = useState("");
  const [visitorType, setVisitorType] = useState("Member");
  const inputRef = useRef<HTMLInputElement>(null);

  const today = new Date().toISOString().split("T")[0];
  const todayLogs = logs?.filter((l) => l.dateStr === today) ?? [];
  const occupancy = todayLogs.filter((l) => !l.exitTime).length;

  const handleSignIn = async () => {
    const val = entryInput.trim();
    if (!val) return;
    await addRecord({
      name: val,
      visitorType,
      dateStr: today,
      entryTime: Date.now(),
      exitTime: null,
    });
    setEntryInput("");
    inputRef.current?.focus();
  };

  const handleSignOut = async (id: string) => {
    await updateRecord(id, { exitTime: Date.now() });
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-500 max-w-5xl mx-auto">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold text-[#E8EEF8]">🛂 Gate Entry Monitor</h1>
          <p className="text-sm text-slate-400">Log daily visitors and track real-time library occupancy</p>
        </div>
        <div className="bg-emerald-900/40 border border-emerald-700/50 rounded-xl px-6 py-3 text-center">
          <p className="text-xs font-bold uppercase tracking-wider text-emerald-400">Current Occupancy</p>
          <p className="text-4xl font-black text-emerald-400">{occupancy}</p>
        </div>
      </div>

      {/* Quick Entry Bar */}
      <div className="rounded-xl border border-blue-950 bg-[#070F1E] p-5 shadow-xl">
        <div className="flex gap-3">
          <input
            ref={inputRef}
            type="text"
            value={entryInput}
            onChange={(e) => setEntryInput(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && handleSignIn()}
            placeholder="Scan Member ID or Type Guest Name..."
            className="flex-1 rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-3 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]"
            autoFocus
          />
          <select
            value={visitorType}
            onChange={(e) => setVisitorType(e.target.value)}
            className="rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-3 text-sm text-[#E8EEF8] outline-none"
          >
            <option>Member</option>
            <option>Guest</option>
            <option>Staff</option>
          </select>
          <button
            onClick={handleSignIn}
            className="px-6 py-3 rounded-lg bg-emerald-600 text-white font-bold text-sm hover:bg-emerald-500 transition-colors shadow"
          >
            ✅ Entry Sign-In
          </button>
        </div>
      </div>

      {/* Log Table */}
      <div className="rounded-xl border border-blue-950 bg-[#070F1E] p-6 shadow-xl overflow-hidden">
        <h3 className="text-lg font-bold text-white mb-4">Today's Visitor Log — {today}</h3>
        {loading ? (
          <div className="py-12 flex justify-center">
            <div className="h-8 w-8 animate-spin rounded-full border-4 border-[#C8A84B] border-t-transparent" />
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-slate-300">
              <thead className="text-xs uppercase bg-[#0D1F38]/40 text-slate-400">
                <tr>
                  <th className="px-4 py-3">Name / ID</th>
                  <th className="px-4 py-3">Type</th>
                  <th className="px-4 py-3">Entry Time</th>
                  <th className="px-4 py-3">Exit Time</th>
                  <th className="px-4 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-blue-950/40">
                {todayLogs.map((log) => {
                  const entryStr = new Date(log.entryTime).toLocaleTimeString();
                  const exitStr = log.exitTime ? new Date(log.exitTime).toLocaleTimeString() : null;
                  return (
                    <tr key={log.id} className="hover:bg-blue-950/10">
                      <td className="px-4 py-4 font-semibold text-white">{log.name}</td>
                      <td className="px-4 py-4">
                        <span className="px-2 py-0.5 rounded text-xs font-bold bg-blue-900/40 text-blue-300">{log.visitorType}</span>
                      </td>
                      <td className="px-4 py-4">{entryStr}</td>
                      <td className="px-4 py-4">
                        {exitStr ? (
                          <span className="text-slate-400">{exitStr}</span>
                        ) : (
                          <span className="text-emerald-400 font-bold animate-pulse">● IN LIBRARY</span>
                        )}
                      </td>
                      <td className="px-4 py-4 text-right">
                        {!log.exitTime && (
                          <button
                            onClick={() => handleSignOut(log.id)}
                            className="px-3 py-1 rounded bg-red-500/10 border border-red-500/30 text-xs font-bold text-red-400 hover:bg-red-500/20 transition-colors"
                          >
                            Sign Out
                          </button>
                        )}
                      </td>
                    </tr>
                  );
                })}
                {todayLogs.length === 0 && (
                  <tr>
                    <td colSpan={5} className="text-center py-8 text-slate-500">No visitors logged today.</td>
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
