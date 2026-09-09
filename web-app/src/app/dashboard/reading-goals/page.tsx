"use client";

import React, { useState } from "react";

export default function ReadingGoalsPage() {
  const [goalName, setGoalName] = useState("");
  const [targetBooks, setTargetBooks] = useState(10);
  
  const activeGoals = [
    { id: 1, name: "Summer Reading Challenge", current: 450, target: 1000, color: "bg-accent-bg" },
    { id: 2, name: "Science Faculty Prep", current: 25, target: 50, color: "bg-positive" },
    { id: 3, name: "Freshmen Orientation", current: 120, target: 300, color: "bg-accent-bg" }
  ];

  return (
    <div className="space-y-6 animate-in fade-in duration-500 max-w-5xl mx-auto">
      <div>
        <h1 className="text-3xl font-extrabold text-ink">🎯 Institutional Reading Goals</h1>
        <p className="text-sm text-muted">Set campus-wide or departmental reading targets</p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        <div className="md:col-span-1 rounded-xl border border-line bg-surface-2 p-6 shadow-xl h-fit">
          <h3 className="text-lg font-bold text-ink mb-4">Create New Goal</h3>
          <div className="space-y-4">
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-muted">Campaign Name</label>
              <input
                type="text"
                value={goalName}
                onChange={(e) => setGoalName(e.target.value)}
                className="mt-2 w-full rounded-lg border border-line bg-surface px-4 py-2.5 text-sm text-ink outline-none focus:border-accent"
                placeholder="e.g. Winter Reading"
              />
            </div>
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-muted">Target (Books Read)</label>
              <input
                type="number"
                value={targetBooks}
                onChange={(e) => setTargetBooks(parseInt(e.target.value))}
                className="mt-2 w-full rounded-lg border border-line bg-surface px-4 py-2.5 text-sm text-ink outline-none focus:border-accent"
              />
            </div>
            <button className="bg-accent-bg w-full rounded-lg py-3 text-sm font-bold text-on-accent transition-all shadow-lg">
              🚀 Launch Goal
            </button>
          </div>
        </div>

        <div className="md:col-span-2 space-y-4">
          <h3 className="text-lg font-bold text-ink mb-4">Active Campaigns</h3>
          
          {activeGoals.map(goal => {
            const percentage = Math.round((goal.current / goal.target) * 100);
            return (
              <div key={goal.id} className="rounded-xl border border-line bg-surface-2 p-6 shadow-xl">
                <div className="flex justify-between mb-2">
                  <h4 className="font-bold text-ink">{goal.name}</h4>
                  <span className="text-sm font-bold text-muted">{goal.current} / {goal.target} Books</span>
                </div>
                <div className="h-4 w-full bg-surface rounded-full overflow-hidden border border-line/30">
                  <div 
                    className={`h-full ${goal.color} transition-all duration-1000`} 
                    style={{ width: `${percentage}%` }}
                  />
                </div>
                <p className="text-right text-xs mt-2 text-muted font-bold">{percentage}% Completed</p>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
