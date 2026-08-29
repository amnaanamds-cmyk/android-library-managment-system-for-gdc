"use client";

import React from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";

export default function DashboardOverview() {
  const { data: books, loading: loadingBooks } = useTenantCollection("books");
  const { data: members, loading: loadingMembers } = useTenantCollection("members");
  const { data: issues, loading: loadingIssues } = useTenantCollection("issued_books");

  const totalBooks = books?.length || 0;
  const totalMembers = members?.length || 0;
  const activeLoans = issues?.filter(i => !i.returned).length || 0;

  const stats = [
    { name: "Total Books Cataloged", value: totalBooks, icon: "📚", color: "from-blue-600 to-indigo-600" },
    { name: "Registered Members", value: totalMembers, icon: "👥", color: "from-emerald-600 to-teal-600" },
    { name: "Active Book Issues", value: activeLoans, icon: "🔄", color: "from-[#C8A84B] to-amber-600" },
  ];

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-extrabold text-[#E8EEF8]">Dashboard Overview</h1>
        <p className="text-sm text-slate-400">Real-time statistics for your active institution</p>
      </div>

      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {stats.map((stat, i) => (
          <div key={i} className="rounded-xl border border-blue-950 bg-[#070F1E] p-6 flex items-center justify-between shadow-xl">
            <div>
              <p className="text-xs font-bold uppercase tracking-wider text-slate-400">{stat.name}</p>
              <h2 className="text-4xl font-extrabold text-white mt-2">
                {loadingBooks || loadingMembers || loadingIssues ? (
                  <span className="inline-block h-6 w-12 animate-pulse bg-slate-800 rounded" />
                ) : (
                  stat.value
                )}
              </h2>
            </div>
            <span className={`text-4xl p-3 rounded-lg bg-gradient-to-br ${stat.color} text-white`}>
              {stat.icon}
            </span>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        {/* Recent Books */}
        <div className="rounded-xl border border-blue-950 bg-[#070F1E] p-6 shadow-xl">
          <h3 className="text-lg font-bold text-white mb-4">Newly Added Books</h3>
          <div className="space-y-4">
            {books?.slice(0, 5).map((book, idx) => (
              <div key={idx} className="flex justify-between items-center border-b border-blue-950/40 pb-2">
                <div>
                  <p className="text-sm font-semibold text-slate-200">{book.title}</p>
                  <p className="text-xs text-slate-400">{book.author || "Unknown Author"}</p>
                </div>
                <span className="text-xs bg-blue-950 text-blue-400 border border-blue-800 px-2 py-0.5 rounded">
                  {book.subject || "General"}
                </span>
              </div>
            ))}
            {books?.length === 0 && <p className="text-xs text-slate-500">No books cataloged yet.</p>}
          </div>
        </div>

        {/* Active Issues */}
        <div className="rounded-xl border border-blue-950 bg-[#070F1E] p-6 shadow-xl">
          <h3 className="text-lg font-bold text-white mb-4">Active Issues</h3>
          <div className="space-y-4">
            {issues?.filter(i => !i.returned).slice(0, 5).map((issue, idx) => (
              <div key={idx} className="flex justify-between items-center border-b border-blue-950/40 pb-2">
                <div>
                  <p className="text-sm font-semibold text-slate-200">{issue.bookTitle || `Book ID: ${issue.bookId}`}</p>
                  <p className="text-xs text-slate-400">Issued to: {issue.memberName || `Member ID: ${issue.memberId}`}</p>
                </div>
                <span className="text-xs text-[#E6C96E]">
                  Due: {issue.dueDate || "N/A"}
                </span>
              </div>
            ))}
            {issues?.filter(i => !i.returned).length === 0 && (
              <p className="text-xs text-slate-500">No active book loans at the moment.</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
