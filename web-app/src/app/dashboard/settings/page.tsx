"use client";

import React, { useState } from "react";

export default function SettingsPage() {
  const [activeTab, setActiveTab] = useState("general");
  
  return (
    <div className="space-y-8 animate-in fade-in duration-500 max-w-5xl mx-auto">
      <div>
        <h1 className="text-3xl font-extrabold text-[#E8EEF8] tracking-tight">⚙️ System Settings</h1>
        <p className="text-sm text-slate-400 mt-1">Configure global parameters and view audit logs</p>
      </div>

      <div className="flex space-x-2 border-b border-blue-900/50 pb-px">
        {["General", "Circulation Matrix", "Audit Logs", "Developer Tools"].map((tab) => {
          const id = tab.toLowerCase().replace(" ", "");
          return (
            <button
              key={id}
              onClick={() => setActiveTab(id)}
              className={`px-4 py-2 text-sm font-bold rounded-t-lg transition-colors ${
                activeTab === id
                  ? "bg-[#1E3050] text-[#E6C96E] border-b-2 border-[#C8A84B]"
                  : "text-slate-400 hover:text-white hover:bg-white/5"
              }`}
            >
              {tab}
            </button>
          )
        })}
      </div>

      <div className="bg-[#071428] rounded-b-xl rounded-tr-xl border border-blue-950 p-6 shadow-xl min-h-[400px]">
        {activeTab === "general" && <GeneralSettings />}
        {activeTab === "circulationmatrix" && <CirculationMatrix />}
        {activeTab === "auditlogs" && <AuditLogs />}
        {activeTab === "developertools" && <DeveloperTools />}
      </div>
    </div>
  );
}

function GeneralSettings() {
  const [fine, setFine] = useState(5.0);
  
  return (
    <div className="space-y-8 max-w-2xl">
      <div className="space-y-4">
        <label className="block text-sm font-bold text-slate-300">Daily Overdue Fine Rate (Rs.)</label>
        <input 
          type="number" 
          value={fine} 
          onChange={(e) => setFine(parseFloat(e.target.value))}
          className="w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-3 text-[#E8EEF8] outline-none focus:border-[#C8A84B]"
        />
      </div>

      <div className="space-y-4 pt-6 border-t border-blue-900/40">
        <h3 className="text-lg font-bold text-blue-400">Directorate Central Network</h3>
        <div>
          <label className="block text-xs font-bold text-slate-400 uppercase tracking-wider mb-2">Central API URL</label>
          <input 
            type="text" 
            defaultValue="http://localhost:8000"
            className="w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-3 text-[#E8EEF8] outline-none"
          />
        </div>
        <div>
          <label className="block text-xs font-bold text-slate-400 uppercase tracking-wider mb-2">API Key</label>
          <input 
            type="password" 
            defaultValue="gdc_demo_key_2024"
            className="w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-3 text-[#E8EEF8] outline-none"
          />
        </div>
      </div>

      <button className="px-6 py-3 bg-blue-600 hover:bg-blue-500 text-white font-bold rounded-lg shadow-lg">
        💾 Save Settings
      </button>
    </div>
  );
}

function CirculationMatrix() {
  return (
    <div className="space-y-6">
      <p className="text-slate-400">Define loan periods and max limits per Member Type. This implements a standard Koha Circulation Matrix.</p>
      <div className="overflow-x-auto rounded-lg border border-blue-950">
        <table className="w-full text-left text-sm text-slate-300">
          <thead className="text-xs uppercase bg-[#0D1F38]/40 text-slate-400">
            <tr>
              <th className="px-4 py-3">Member Type</th>
              <th className="px-4 py-3">Max Loans</th>
              <th className="px-4 py-3">Loan Days</th>
              <th className="px-4 py-3">Fine/Day (Rs.)</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-blue-950/40">
            <tr className="hover:bg-blue-950/10">
              <td className="px-4 py-4 font-bold text-white">Student</td>
              <td className="px-4 py-4"><input type="number" defaultValue={2} className="w-16 bg-[#0D1F38] border border-blue-900 rounded px-2 py-1 text-center" /></td>
              <td className="px-4 py-4"><input type="number" defaultValue={14} className="w-16 bg-[#0D1F38] border border-blue-900 rounded px-2 py-1 text-center" /></td>
              <td className="px-4 py-4"><input type="number" defaultValue={5} className="w-16 bg-[#0D1F38] border border-blue-900 rounded px-2 py-1 text-center" /></td>
            </tr>
            <tr className="hover:bg-blue-950/10">
              <td className="px-4 py-4 font-bold text-white">Staff/Faculty</td>
              <td className="px-4 py-4"><input type="number" defaultValue={5} className="w-16 bg-[#0D1F38] border border-blue-900 rounded px-2 py-1 text-center" /></td>
              <td className="px-4 py-4"><input type="number" defaultValue={30} className="w-16 bg-[#0D1F38] border border-blue-900 rounded px-2 py-1 text-center" /></td>
              <td className="px-4 py-4"><input type="number" defaultValue={0} className="w-16 bg-[#0D1F38] border border-blue-900 rounded px-2 py-1 text-center" /></td>
            </tr>
          </tbody>
        </table>
      </div>
      <button className="px-6 py-2 bg-emerald-600 hover:bg-emerald-500 text-white font-bold rounded-lg">Save Matrix</button>
    </div>
  );
}

function AuditLogs() {
  return (
    <div className="space-y-4">
      <div className="flex justify-between items-center mb-4">
        <h3 className="text-lg font-bold text-white">System Audit Trail</h3>
        <button className="text-xs bg-blue-900 hover:bg-blue-800 text-blue-200 px-3 py-1 rounded">Refresh</button>
      </div>
      <div className="overflow-x-auto rounded-lg border border-blue-950 h-64 flex items-center justify-center bg-[#0D1F38]/30">
        <p className="text-slate-500 font-bold uppercase tracking-widest text-xs">No Recent Audit Logs Found</p>
      </div>
    </div>
  );
}

function DeveloperTools() {
  return (
    <div className="space-y-8 max-w-2xl">
      <div className="p-6 rounded-xl border border-red-900/50 bg-red-950/20 space-y-4">
        <div>
          <h3 className="font-bold text-red-400 text-lg">⚠️ Danger Zone</h3>
          <p className="text-sm text-slate-400 mt-1">These actions can lead to permanent data loss.</p>
        </div>
        <button 
          onClick={() => confirm("This will permanently delete all cloud data. Continue?")}
          className="px-6 py-3 bg-red-600 hover:bg-red-500 text-white font-bold rounded-lg shadow-lg"
        >
          Reset Database
        </button>
      </div>

      <div className="p-6 rounded-xl border border-blue-900/50 bg-[#0D1F38]/30 space-y-4">
        <div>
          <h3 className="font-bold text-blue-400 text-lg">System Health</h3>
          <p className="text-sm text-slate-400 mt-1">Run diagnostics and maintain data integrity.</p>
        </div>
        <div className="flex gap-4">
          <button className="px-6 py-3 bg-amber-600 hover:bg-amber-500 text-amber-950 font-bold rounded-lg shadow-lg">
            🩺 Run Data Integrity Check
          </button>
          <button className="px-6 py-3 bg-purple-600 hover:bg-purple-500 text-white font-bold rounded-lg shadow-lg">
            🗄️ Request Cloud Backup
          </button>
        </div>
      </div>
    </div>
  );
}
