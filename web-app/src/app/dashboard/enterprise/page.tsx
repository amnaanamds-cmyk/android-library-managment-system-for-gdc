"use client";

import React, { useState } from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";

export default function EnterprisePage() {
  const [activeTab, setActiveTab] = useState("readingRoom");

  const tabs = [
    { id: "readingRoom", label: "🪑 Reading Room" },
    { id: "lostFound", label: "🔍 Lost & Found" },
    { id: "events", label: "📅 Event Scheduler" },
    { id: "gamification", label: "🏆 Gamification" },
    { id: "ai", label: "🤖 AI Procurement" },
  ];

  return (
    <div className="space-y-8 animate-in fade-in duration-500">
      <div>
        <h1 className="text-3xl font-extrabold text-[#E8EEF8] tracking-tight">🚀 Enterprise & Unique Features</h1>
        <p className="text-sm text-slate-400 mt-1">Advanced management, automation, and community engagement</p>
      </div>

      <div className="flex space-x-2 border-b border-blue-900/50 pb-px">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`px-4 py-2 text-sm font-bold rounded-t-lg transition-colors ${
              activeTab === tab.id
                ? "bg-[#1E3050] text-[#E6C96E] border-b-2 border-[#C8A84B]"
                : "text-slate-400 hover:text-white hover:bg-white/5"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="bg-[#071428] rounded-b-xl rounded-tr-xl border border-blue-950 p-6 shadow-xl min-h-[500px]">
        {activeTab === "readingRoom" && <ReadingRoomTab />}
        {activeTab === "lostFound" && <LostFoundTab />}
        {activeTab === "events" && <EventsTab />}
        {activeTab === "gamification" && <GamificationTab />}
        {activeTab === "ai" && <AiProcurementTab />}
      </div>
    </div>
  );
}

function ReadingRoomTab() {
  const [seats, setSeats] = useState(
    Array.from({ length: 20 }, (_, i) => ({ id: i + 1, occupant: null }))
  );

  const toggleSeat = (id: number) => {
    const seat = seats.find((s) => s.id === id);
    if (seat?.occupant) {
      if (confirm(`Make Seat ${id} empty?`)) {
        setSeats(seats.map(s => s.id === id ? { ...s, occupant: null } : s));
      }
    } else {
      const occupant = prompt(`Enter Member ID for Seat ${id}:`);
      if (occupant) {
        setSeats(seats.map(s => s.id === id ? { ...s, occupant } : s));
      }
    }
  };

  return (
    <div className="space-y-6">
      <p className="text-sm text-slate-400">Manage seat assignments for the physical reading room.</p>
      <div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-5 gap-4">
        {seats.map((seat) => (
          <button
            key={seat.id}
            onClick={() => toggleSeat(seat.id)}
            className={`h-24 rounded-xl flex flex-col items-center justify-center font-bold transition-all ${
              seat.occupant
                ? "bg-red-500 text-white shadow-lg shadow-red-500/20 hover:bg-red-400"
                : "bg-emerald-500 text-white shadow-lg shadow-emerald-500/20 hover:bg-emerald-400"
            }`}
          >
            <span className="text-sm opacity-80">Seat {seat.id}</span>
            <span className="text-lg mt-1">{seat.occupant || "(Empty)"}</span>
          </button>
        ))}
      </div>
    </div>
  );
}

function LostFoundTab() {
  const [items, setItems] = useState([
    { id: 1, date: "2024-05-12", desc: "Blue Water Bottle", loc: "Reading Room", status: "Found" },
    { id: 2, date: "2024-05-10", desc: "HP Laptop Charger", loc: "Section B", status: "Lost" },
  ]);

  const reportItem = () => {
    const desc = prompt("Item Description:");
    if (!desc) return;
    const loc = prompt("Location:");
    if (!loc) return;
    const status = confirm("Is it Found? (Cancel for Lost)") ? "Found" : "Lost";
    setItems([{ id: Date.now(), date: new Date().toISOString().split('T')[0], desc, loc, status }, ...items]);
  };

  const resolveItem = (id: number) => {
    setItems(items.map(i => i.id === id ? { ...i, status: "Claimed/Resolved" } : i));
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <p className="text-sm text-slate-400">Track items lost or found within the library premises.</p>
        <button onClick={reportItem} className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white font-bold rounded-lg shadow-lg">
          ➕ Report Item
        </button>
      </div>
      <div className="overflow-x-auto rounded-lg border border-blue-950">
        <table className="w-full text-left text-sm text-slate-300">
          <thead className="text-xs uppercase bg-[#0D1F38]/40 text-slate-400">
            <tr>
              <th className="px-4 py-3">Date</th>
              <th className="px-4 py-3">Item Description</th>
              <th className="px-4 py-3">Location</th>
              <th className="px-4 py-3">Status</th>
              <th className="px-4 py-3 text-right">Action</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-blue-950/40">
            {items.map(item => (
              <tr key={item.id} className="hover:bg-blue-950/10">
                <td className="px-4 py-4">{item.date}</td>
                <td className="px-4 py-4 font-semibold text-white">{item.desc}</td>
                <td className="px-4 py-4">{item.loc}</td>
                <td className="px-4 py-4">
                  <span className={`font-bold ${
                    item.status === 'Lost' ? 'text-red-400' : 
                    item.status === 'Found' ? 'text-emerald-400' : 'text-[#C8A84B]'
                  }`}>
                    {item.status}
                  </span>
                </td>
                <td className="px-4 py-4 text-right">
                  {item.status !== "Claimed/Resolved" && (
                    <button onClick={() => resolveItem(item.id)} className="text-emerald-400 hover:text-emerald-300 text-xs font-bold bg-emerald-950 px-2 py-1 rounded">
                      Resolve
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function EventsTab() {
  const [events, setEvents] = useState([
    { id: 1, dt: "2024-06-15 14:00", title: "Introduction to Python", host: "Dr. Ahmed", att: "45/50" },
    { id: 2, dt: "2024-06-20 10:00", title: "Literature & Modern World", host: "Guest Author", att: "120/150" },
  ]);

  const addEvent = () => {
    const title = prompt("Event Title:");
    if (!title) return;
    const host = prompt("Speaker/Host:");
    if (!host) return;
    
    const d = new Date();
    d.setDate(d.getDate() + 7);
    const dt = d.toISOString().replace('T', ' ').substring(0, 16);
    
    setEvents([{ id: Date.now(), dt, title, host, att: "0/50" }, ...events]);
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <p className="text-sm text-slate-400">Schedule library workshops, author talks, and community events.</p>
        <button onClick={addEvent} className="px-4 py-2 bg-purple-600 hover:bg-purple-500 text-white font-bold rounded-lg shadow-lg">
          📅 Schedule New Event
        </button>
      </div>
      <div className="overflow-x-auto rounded-lg border border-blue-950">
        <table className="w-full text-left text-sm text-slate-300">
          <thead className="text-xs uppercase bg-[#0D1F38]/40 text-slate-400">
            <tr>
              <th className="px-4 py-3">Date & Time</th>
              <th className="px-4 py-3">Event Title</th>
              <th className="px-4 py-3">Speaker/Host</th>
              <th className="px-4 py-3">Attendees</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-blue-950/40">
            {events.map(ev => (
              <tr key={ev.id} className="hover:bg-blue-950/10">
                <td className="px-4 py-4">{ev.dt}</td>
                <td className="px-4 py-4 font-semibold text-white">{ev.title}</td>
                <td className="px-4 py-4">{ev.host}</td>
                <td className="px-4 py-4 text-purple-300 font-bold">{ev.att}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function GamificationTab() {
  const { data: members } = useTenantCollection("members");
  const [search, setSearch] = useState("");
  const [result, setResult] = useState<any>(null);

  const analyze = () => {
    const m = members.find(mem => mem.memberId === search);
    if (!m) {
      setResult("Not Found");
      return;
    }
    const read = m.booksIssued || 0;
    let rank, badge, nextTgt;
    if (read < 5) { rank = "Novice Reader"; badge = "🌱"; nextTgt = 5; }
    else if (read < 20) { rank = "Avid Bookworm"; badge = "🐛"; nextTgt = 20; }
    else if (read < 50) { rank = "Library Scholar"; badge = "🎓"; nextTgt = 50; }
    else { rank = "Grandmaster of Pages"; badge = "👑"; nextTgt = "MAX"; }

    setResult({ name: m.name, read, rank, badge, nextTgt });
  };

  return (
    <div className="space-y-6 max-w-2xl mx-auto py-8">
      <div className="text-center">
        <h3 className="text-xl font-bold text-white mb-2">Boost Reading Engagement!</h3>
        <p className="text-sm text-slate-400">Ranks update automatically based on reading history.</p>
      </div>
      <div className="flex gap-2">
        <input 
          type="text" 
          placeholder="Member ID..." 
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="flex-1 rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-3 text-[#E8EEF8] outline-none focus:border-[#C8A84B]"
        />
        <button onClick={analyze} className="px-6 py-3 bg-[#C8A84B] hover:bg-amber-400 text-amber-950 font-black rounded-lg transition-colors">
          Analyze Profile
        </button>
      </div>
      
      <div className="mt-8 border border-dashed border-[#1E3050] bg-[#0D1F38]/50 rounded-xl p-8 flex flex-col items-center justify-center min-h-[200px]">
        {!result ? (
          <p className="text-slate-500 font-bold uppercase tracking-widest text-sm">Profile Data Will Appear Here</p>
        ) : result === "Not Found" ? (
          <p className="text-red-400 font-bold text-lg">Member Not Found.</p>
        ) : (
          <div className="text-center space-y-3">
            <h2 className="text-4xl text-[#E6C96E] font-black">{result.badge} {result.name}</h2>
            <p className="text-lg text-white">Current Rank: <span className="font-bold text-[#E6C96E]">{result.rank}</span></p>
            <p className="text-slate-300">Books Read: <span className="font-bold text-white text-xl">{result.read}</span></p>
            {result.nextTgt !== "MAX" && (
              <p className="text-xs text-blue-400 italic font-medium mt-4">
                Read {result.nextTgt - result.read} more books to rank up!
              </p>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function AiProcurementTab() {
  const { data: issues } = useTenantCollection("issued_books");
  const [analysis, setAnalysis] = useState<string[] | null>(null);
  const [loading, setLoading] = useState(false);

  const runAi = () => {
    setLoading(true);
    setTimeout(() => {
      if (!issues || issues.length === 0) {
        setAnalysis([]);
      } else {
        const counts: Record<string, number> = {};
        issues.forEach((i: any) => {
          const t = i.bookTitle || "Unknown Title";
          counts[t] = (counts[t] || 0) + 1;
        });
        const sorted = Object.entries(counts).sort((a, b) => b[1] - a[1]).slice(0, 5);
        setAnalysis(sorted.map(([t, c]) => `🔹 High Demand: '${t}' (${c} times)`));
      }
      setLoading(false);
    }, 1500);
  };

  return (
    <div className="space-y-6 max-w-3xl mx-auto py-8">
      <div className="text-center space-y-4">
        <p className="text-slate-400 text-lg">Analyzes circulation history to automatically suggest books to purchase based on high demand.</p>
        <button 
          onClick={runAi}
          disabled={loading}
          className="px-8 py-4 bg-gradient-to-r from-red-500 to-amber-500 hover:from-red-400 hover:to-amber-400 text-white font-black rounded-xl shadow-xl transition-all disabled:opacity-50 text-lg"
        >
          {loading ? "Analyzing patterns..." : "🤖 Run AI Demand Analysis"}
        </button>
      </div>
      
      {analysis !== null && (
        <div className="mt-8 bg-[#0D1F38] border border-blue-900 rounded-xl p-6 text-slate-200">
          <h3 className="font-bold text-emerald-400 mb-4 text-xl">✅ Analysis Complete:</h3>
          {analysis.length === 0 ? (
            <p className="text-slate-500 italic">Not enough data points yet.</p>
          ) : (
            <ul className="space-y-3">
              {analysis.map((line, i) => (
                <li key={i} className="font-medium text-lg">{line}</li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}
