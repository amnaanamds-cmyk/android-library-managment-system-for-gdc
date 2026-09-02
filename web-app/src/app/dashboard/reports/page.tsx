"use client";

import React, { useState, useEffect } from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";
import { isOverdue } from "@/lib/schema";

export default function ReportsPage() {
  const { data: books } = useTenantCollection("books");
  const { data: issues } = useTenantCollection("issued_books");
  
  const [val, setVal] = useState<any>(null);

  useEffect(() => {
    if (books && issues) {
      let totalValue = 0;
      let pricedCount = 0;
      let available = 0;
      let issued = 0;
      let lost = 0;

      books.forEach((b: any) => {
        if (b.price) {
          totalValue += Number(b.price);
          pricedCount++;
        }
        if (b.status === "Available") available++;
        else if (b.status === "Issued") issued++;
        else if (b.status === "Lost") lost++;
      });

      // Use the shared predicate: an open loan is status === "Issued".
      // The old check tested a `returned` boolean that no platform writes,
      // so returned loans still counted as overdue.
      const overdueCount = issues.filter((i: any) => isOverdue(i)).length;

      setVal({
        totalValue,
        pricedCount,
        totalBooks: books.length,
        available,
        issued,
        lost,
        overdueCount
      });
    }
  }, [books, issues]);

  return (
    <div className="space-y-8 animate-in fade-in duration-500">
      <div>
        <h1 className="text-3xl font-extrabold text-[#E8EEF8] tracking-tight">📊 Library Reports & Analytics</h1>
        <p className="text-sm text-slate-400 mt-1">Generate professional reports, interactive charts, and insights.</p>
      </div>

      {/* Financial Valuation */}
      <div className="bg-[#0D1F38] border-2 border-[#C8A84B] rounded-xl p-6 flex items-center justify-between shadow-xl shadow-amber-900/10">
        <div className="flex items-center gap-6">
          <span className="text-5xl">🏦</span>
          <div>
            <h3 className="text-[#C8A84B] font-black text-2xl">
              Total Collection Value: Rs. {val?.totalValue?.toLocaleString() || "..."}
            </h3>
            <p className="text-slate-400 font-bold text-sm">
              {val?.pricedCount || 0} / {val?.totalBooks || 0} books priced | Avg: Rs. {val?.pricedCount ? Math.round(val.totalValue / val.pricedCount) : 0}
            </p>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        {/* Availability Ratio */}
        <div className="bg-[#071428] border border-blue-950 rounded-xl p-6 shadow-xl border-t-4 border-t-emerald-500">
          <h3 className="font-bold text-white text-lg mb-4 flex items-center gap-2">
            <span>🥑</span> Availability Ratio
          </h3>
          <div className="space-y-4">
            <ProgressBar label={`Available (${val?.available || 0})`} value={val?.available || 0} total={val?.totalBooks || 1} color="bg-emerald-500" />
            <ProgressBar label={`Issued (${val?.issued || 0})`} value={val?.issued || 0} total={val?.totalBooks || 1} color="bg-amber-500" />
            <ProgressBar label={`Lost/Other (${val?.lost || 0})`} value={val?.lost || 0} total={val?.totalBooks || 1} color="bg-red-500" />
          </div>
        </div>

        {/* WhatsApp Overdue Reminders */}
        <div className="bg-[#0D1F38] border border-[#25D366]/30 rounded-xl p-6 shadow-xl">
          <h3 className="font-bold text-[#25D366] text-lg mb-2 flex items-center gap-2">
            <span>📱</span> WhatsApp Reminders
          </h3>
          <p className="text-sm text-slate-400 mb-6">
            Send polite, auto-filled WhatsApp messages to patrons with overdue books.
            Currently tracking <span className="font-black text-white">{val?.overdueCount || 0} overdue</span> issues.
          </p>
          <button 
            disabled={!val?.overdueCount}
            className="w-full py-3 bg-[#25D366] text-white font-bold rounded-lg shadow-lg shadow-[#25D366]/20 transition-all hover:bg-[#20bd5a] disabled:opacity-50 disabled:cursor-not-allowed"
          >
            📱 Send Overdue Reminders
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <ReportCard 
          icon="📈" title="General Statistics" 
          desc="Internal report detailing current inventory, issued books, and active members." 
          btnText="Generate PDF" color="blue" 
        />
        <ReportCard 
          icon="🎯" title="Director Pitch Summary" 
          desc="Polished letterhead report specifically formatted for HEC / Directorate of Archives." 
          btnText="Generate PDF" color="amber" 
        />
        <ReportCard 
          icon="💾" title="Excel Master Export" 
          desc="Export the entire database (Books, Members, Issues) to a formatted workbook." 
          btnText="Export to Excel" color="emerald" 
        />
      </div>
    </div>
  );
}

function ProgressBar({ label, value, total, color }: { label: string, value: number, total: number, color: string }) {
  const pct = total > 0 ? Math.round((value / total) * 100) : 0;
  return (
    <div>
      <div className="flex justify-between text-xs font-bold text-slate-300 mb-1">
        <span>{label}</span>
        <span>{pct}%</span>
      </div>
      <div className="w-full bg-[#0D1F38] rounded-full h-3">
        <div className={`${color} h-3 rounded-full`} style={{ width: `${pct}%` }}></div>
      </div>
    </div>
  );
}

function ReportCard({ icon, title, desc, btnText, color }: { icon: string, title: string, desc: string, btnText: string, color: string }) {
  const colorMap: Record<string, string> = {
    blue: "border-t-blue-500 bg-blue-600 hover:bg-blue-500 text-white",
    amber: "border-t-[#C8A84B] bg-[#C8A84B] hover:bg-amber-400 text-amber-950",
    emerald: "border-t-emerald-500 bg-emerald-600 hover:bg-emerald-500 text-white"
  };
  const btnClass = colorMap[color];

  return (
    <div className={`bg-[#071428] border border-blue-950 rounded-xl p-6 flex flex-col h-full border-t-4 ${btnClass.split(' ')[0]}`}>
      <div className="text-4xl mb-4">{icon}</div>
      <h3 className="text-lg font-bold text-white mb-2">{title}</h3>
      <p className="text-sm text-slate-400 mb-6 flex-1">{desc}</p>
      <button className={`w-full py-2.5 rounded-lg font-bold shadow-lg transition-colors ${btnClass.split(' ').slice(1).join(' ')}`}>
        {btnText}
      </button>
    </div>
  );
}
