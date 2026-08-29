"use client";

import React, { useState } from "react";

export default function ReadingGoalsPage() {
  const [goalName, setGoalName] = useState("");
  const [targetBooks, setTargetBooks] = useState(10);
  
  const activeGoals = [
    { id: 1, name: "Summer Reading Challenge", current: 450, target: 1000, color: "bg-blue-500" },
    { id: 2, name: "Science Faculty Prep", current: 25, target: 50, color: "bg-emerald-500" },
    { id: 3, name: "Freshmen Orientation", current: 120, target: 300, color: "bg-purple-500" }
  ];

  return (
    <div className="space-y-6 animate-in fade-in duration-500 max-w-5xl mx-auto">
      <div>
        <h1 className="text-3xl font-extrabold text-[#E8EEF8]">🎯 Institutional Reading Goals</h1>
        <p className="text-sm text-slate-400">Set campus-wide or departmental reading targets</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="md:col-span-1 rounded-xl border border-blue-950 bg-[#070F1E] p-6 shadow-xl h-fit">
          <h3 className="text-lg font-bold text-white mb-4">Create New Goal</h3>
          <div className="space-y-4">
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-slate-400">Campaign Name</label>
              <input
                type="text"
                value={goalName}
                onChange={(e) => setGoalName(e.target.value)}
                className="mt-2 w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2.5 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]"
                placeholder="e.g. Winter Reading"
              />
            </div>
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-slate-400">Target (Books Read)</label>
              <input
                type="number"
                value={targetBooks}
                onChange={(e) => setTargetBooks(parseInt(e.target.value))}
                className="mt-2 w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2.5 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]"
              />
            </div>
            <button className="w-full rounded-lg bg-gradient-to-r from-[#1E5FD4] to-[#2872F0] py-3 text-sm font-bold text-white transition-all hover:from-[#2872F0] hover:to-[#3D8EFF] shadow-lg">
              🚀 Launch Goal
            </button>
          </div>
        </div>

        <div className="md:col-span-2 space-y-4">
          <h3 className="text-lg font-bold text-white mb-4">Active Campaigns</h3>
          
          {activeGoals.map(goal => {
            const percentage = Math.round((goal.current / goal.target) * 100);
            return (
              <div key={goal.id} className="rounded-xl border border-blue-950 bg-[#070F1E] p-6 shadow-xl">
                <div className="flex justify-between mb-2">
                  <h4 className="font-bold text-white">{goal.name}</h4>
                  <span className="text-sm font-bold text-slate-400">{goal.current} / {goal.target} Books</span>
                </div>
                <div className="h-4 w-full bg-[#0D1F38] rounded-full overflow-hidden border border-blue-900/30">
                  <div 
                    className={`h-full ${goal.color} transition-all duration-1000`} 
                    style={{ width: `${percentage}%` }}
                  />
                </div>
                <p className="text-right text-xs mt-2 text-slate-500 font-bold">{percentage}% Completed</p>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
