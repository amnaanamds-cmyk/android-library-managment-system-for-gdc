"use client";

import React, { useState } from "react";

export default function UsageHeatmapPage() {
  const [timeframe, setTimeframe] = useState("Weekly");

  // Simulated heatmap data for UI parity
  const days = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
  const hours = ["8 AM", "10 AM", "12 PM", "2 PM", "4 PM", "6 PM"];
  
  const intensityMap = (val: number) => {
    if (val > 80) return "bg-red-500/80";
    if (val > 60) return "bg-orange-500/80";
    if (val > 40) return "bg-yellow-500/80";
    if (val > 20) return "bg-emerald-500/80";
    return "bg-slate-700/50";
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-500 max-w-7xl mx-auto">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold text-[#E8EEF8]">🔥 Usage Heatmap</h1>
          <p className="text-sm text-slate-400">Analyze library footfall and system engagement times</p>
        </div>
        <div className="flex gap-2">
          <select 
            value={timeframe} 
            onChange={(e) => setTimeframe(e.target.value)}
            className="rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2 text-sm text-[#E8EEF8] outline-none"
          >
            <option>Daily</option>
            <option>Weekly</option>
            <option>Monthly</option>
          </select>
          <button className="bg-[#1E3050] text-[#A0B4CC] border border-[#1E3050] rounded-md px-3 py-1.5 text-xs font-semibold hover:bg-[#2A4166] transition-colors shadow">
            📤 Export Data
          </button>
        </div>
      </div>

      <div className="rounded-xl border border-blue-950 bg-[#070F1E] p-8 shadow-xl">
        <h3 className="text-lg font-bold text-white mb-6">Library Traffic Analysis ({timeframe})</h3>
        
        <div className="overflow-x-auto">
          <div className="min-w-[600px]">
            <div className="flex mb-2">
              <div className="w-16"></div>
              {hours.map(h => (
                <div key={h} className="flex-1 text-center text-xs font-bold text-slate-400">{h}</div>
              ))}
            </div>
            
            {days.map((day, i) => (
              <div key={day} className="flex items-center gap-2 mb-2">
                <div className="w-16 text-right text-sm font-bold text-slate-300 pr-4">{day}</div>
                {hours.map((_, j) => {
                  const val = Math.random() * 100;
                  return (
                    <div 
                      key={j} 
                      className={`flex-1 h-12 rounded-md ${intensityMap(val)} transition-all hover:scale-[1.02] cursor-pointer hover:shadow-lg hover:shadow-white/10`}
                      title={`Traffic Intensity: ${Math.round(val)}%`}
                    />
                  );
                })}
              </div>
            ))}
          </div>
        </div>

        <div className="mt-8 flex justify-center gap-6 text-xs font-bold text-slate-400">
          <div className="flex items-center gap-2"><div className="w-4 h-4 rounded bg-slate-700/50" /> Low</div>
          <div className="flex items-center gap-2"><div className="w-4 h-4 rounded bg-emerald-500/80" /> Moderate</div>
          <div className="flex items-center gap-2"><div className="w-4 h-4 rounded bg-yellow-500/80" /> Busy</div>
          <div className="flex items-center gap-2"><div className="w-4 h-4 rounded bg-red-500/80" /> Peak</div>
        </div>
      </div>
    </div>
  );
}
