"use client";

import React, { useState, useEffect } from "react";

export default function Home() {
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isDarkMode, setIsDarkMode] = useState(false);
  const [currentScreen, setCurrentScreen] = useState("Dashboard");

  // Sync with system preference on first load if no preference is stored
  useEffect(() => {
    const savedTheme = localStorage.getItem("theme");
    if (savedTheme) {
      setIsDarkMode(savedTheme === "dark");
    } else if (window.matchMedia("(prefers-color-scheme: dark)").matches) {
      setIsDarkMode(true);
    }
  }, []);

  const toggleTheme = () => {
    const newMode = !isDarkMode;
    setIsDarkMode(newMode);
    localStorage.setItem("theme", newMode ? "dark" : "light");
  };

  if (!isAuthenticated) {
    return (
      <main className={`min-h-screen flex items-center justify-center p-4 transition-colors duration-300 ${isDarkMode ? 'bg-[#0A0F1C]' : 'bg-slate-50'}`}>
        <div className="absolute inset-0 bg-gradient-to-br from-indigo-900/10 via-transparent to-blue-900/10 z-0" />
        
        <div className="relative z-10 w-full max-w-md">
          <div className={`${isDarkMode ? 'bg-slate-900/80 border-slate-800' : 'bg-white border-slate-200'} backdrop-blur-xl p-8 rounded-2xl border shadow-2xl transition-colors duration-300`}>
            <div className="text-center mb-8">
              <div className="w-16 h-16 bg-[#1E3A8A] rounded-2xl mx-auto flex items-center justify-center mb-4 rotate-3 hover:rotate-6 transition-transform shadow-lg shadow-blue-500/20">
                <svg className="w-8 h-8 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6.253v13m0-13C10.832 5.477 9.246 5 7.5 5S4.168 5.477 3 6.253v13C4.168 18.477 5.754 18 7.5 18s3.332.477 4.5 1.253m0-13C13.168 5.477 14.754 5 16.5 5c1.747 0 3.332.477 4.5 1.253v13C19.832 18.477 18.247 18 16.5 18c-1.746 0-3.332.477-4.5 1.253" />
                </svg>
              </div>
              <h1 className={`text-3xl font-bold mb-2 tracking-tight ${isDarkMode ? 'text-white' : 'text-slate-900'}`}>GDC Library</h1>
              <p className={isDarkMode ? 'text-slate-400' : 'text-slate-600'}>Multi-Tenant Management System</p>
            </div>

            <form className="space-y-4" onSubmit={(e) => { e.preventDefault(); setIsAuthenticated(true); }}>
              <div>
                <label className={`block text-sm font-medium mb-1 ${isDarkMode ? 'text-slate-400' : 'text-slate-700'}`}>Email</label>
                <input 
                  type="email" 
                  defaultValue="admin@gdc.edu"
                  className={`w-full px-4 py-3 border rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all ${
                    isDarkMode ? 'bg-slate-950 border-slate-800 text-white' : 'bg-white border-slate-300 text-slate-900'
                  }`}
                  placeholder="name@college.edu"
                />
              </div>
              <div>
                <label className={`block text-sm font-medium mb-1 ${isDarkMode ? 'text-slate-400' : 'text-slate-700'}`}>Password</label>
                <input 
                  type="password"
                  defaultValue="password"
                  className={`w-full px-4 py-3 border rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all ${
                    isDarkMode ? 'bg-slate-950 border-slate-800 text-white' : 'bg-white border-slate-300 text-slate-900'
                  }`}
                  placeholder="••••••••"
                />
              </div>
              
              <button 
                type="submit"
                className="w-full py-3 px-4 bg-[#1E3A8A] hover:bg-[#172554] text-white rounded-xl font-medium shadow-lg transition-all active:scale-[0.98]"
              >
                Sign In Securely
              </button>
            </form>

            <button
              onClick={toggleTheme}
              className={`mt-6 w-full py-2 text-sm font-medium flex items-center justify-center gap-2 rounded-lg transition-colors ${
                isDarkMode ? 'text-slate-400 hover:text-white hover:bg-slate-800' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
              }`}
            >
              {isDarkMode ? '☀️ Switch to Light Mode' : '🌙 Switch to Dark Mode'}
            </button>
          </div>
        </div>
      </main>
    );
  }

  return (
    <div className={`min-h-screen transition-colors duration-300 ${isDarkMode ? 'bg-[#111827] text-slate-300' : 'bg-[#FAFAFA] text-slate-800'}`}>
      {/* Sidebar */}
      <aside className={`fixed left-0 top-0 h-screen w-64 border-r flex flex-col z-20 transition-colors duration-300 ${
        isDarkMode ? 'bg-[#0F1524] border-[#1E2638]' : 'bg-[#172554] border-blue-900'
      }`}>
        <div className="p-6">
          <h2 className="text-white text-xl font-bold flex items-center gap-2">
            <span className="w-8 h-8 bg-[#1E3A8A] rounded-lg flex items-center justify-center text-lg">📚</span>
            GDC Library
          </h2>
          <p className="text-xs text-blue-300/60 mt-1 uppercase tracking-wider font-semibold">Web Portal</p>
        </div>
        
        <nav className="flex-1 px-4 space-y-1 mt-4">
          <NavItem active={currentScreen === "Dashboard"} icon="🏠" text="Dashboard" onClick={() => setCurrentScreen("Dashboard")} />
          <NavItem active={currentScreen === "Catalog"} icon="📖" text="Book Catalog" onClick={() => setCurrentScreen("Catalog")} />
          <NavItem active={currentScreen === "Members"} icon="👥" text="Members" onClick={() => setCurrentScreen("Members")} />
          <NavItem active={currentScreen === "Circulation"} icon="🔄" text="Circulation" onClick={() => setCurrentScreen("Circulation")} />
          <NavItem active={currentScreen === "Reservations"} icon="🔔" text="Reservations" onClick={() => setCurrentScreen("Reservations")} />
          <NavItem active={currentScreen === "Reports"} icon="📊" text="Reports" onClick={() => setCurrentScreen("Reports")} />
          <NavItem active={currentScreen === "Settings"} icon="⚙️" text="Settings" onClick={() => setCurrentScreen("Settings")} />
        </nav>
        
        <div className={`p-4 border-t ${isDarkMode ? 'border-[#1E2638]' : 'border-blue-800'}`}>
          <div className={`flex items-center gap-3 p-2 rounded-xl cursor-pointer transition-colors ${
            isDarkMode ? 'hover:bg-[#1E2638]' : 'hover:bg-blue-800'
          }`}>
            <div className="w-10 h-10 rounded-full bg-gradient-to-tr from-blue-500 to-indigo-600 flex items-center justify-center text-white font-bold">
              AD
            </div>
            <div className="overflow-hidden">
              <p className="text-sm font-semibold text-white truncate">Admin Librarian</p>
              <p className="text-xs text-blue-300 truncate">GDC-11 Campus</p>
            </div>
          </div>
        </div>
      </aside>

      {/* Main Content */}
      <main className="ml-64 flex flex-col h-screen">
        {/* Header */}
        <header className={`h-16 flex justify-between items-center px-8 border-b transition-colors duration-300 ${
          isDarkMode ? 'bg-[#1F2937] border-[#1E2638]' : 'bg-white border-slate-200 shadow-sm'
        }`}>
          <div>
            <h1 className={`text-xl font-bold tracking-tight ${isDarkMode ? 'text-white' : 'text-slate-900'}`}>
              {currentScreen}
            </h1>
          </div>
          
          <div className="flex items-center gap-4">
            <button
              onClick={toggleTheme}
              className={`p-2 rounded-lg transition-colors ${
                isDarkMode ? 'hover:bg-slate-700 text-yellow-400' : 'hover:bg-slate-100 text-slate-600'
              }`}
            >
              {isDarkMode ? '☀️' : '🌙'}
            </button>
            <div className={`h-8 w-[1px] ${isDarkMode ? 'bg-slate-700' : 'bg-slate-200'}`} />
            <button
              onClick={() => setIsAuthenticated(false)}
              className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                isDarkMode ? 'bg-slate-700 hover:bg-slate-600 text-white' : 'bg-slate-100 hover:bg-slate-200 text-slate-900'
              }`}
            >
              Logout
            </button>
          </div>
        </header>

        {/* Content Area */}
        <div className="flex-1 overflow-auto p-8">
          {/* Stats Grid */}
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
            <StatCard isDarkMode={isDarkMode} title="Total Books" value="12,483" change="+24 this week" icon="📚" color="from-blue-500/20 to-blue-600/5" border={isDarkMode ? "border-blue-500/20" : "border-blue-200"} />
            <StatCard isDarkMode={isDarkMode} title="Active Members" value="3,214" change="+12 this week" icon="👥" color="from-purple-500/20 to-purple-600/5" border={isDarkMode ? "border-purple-500/20" : "border-purple-200"} />
            <StatCard isDarkMode={isDarkMode} title="Issued Books" value="842" change="14 overdue" icon="🔄" color="from-amber-500/20 to-amber-600/5" border={isDarkMode ? "border-amber-500/20" : "border-amber-200"} />
          </div>

          {/* Dashboard Content */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className={`col-span-2 rounded-2xl border p-6 shadow-xl transition-colors duration-300 ${
              isDarkMode ? 'bg-[#1F2937] border-[#1E2638]' : 'bg-white border-slate-200'
            }`}>
              <h3 className={`text-lg font-semibold mb-6 ${isDarkMode ? 'text-white' : 'text-slate-900'}`}>Recent Transactions</h3>
              <div className="space-y-4">
                {[1,2,3,4,5].map(i => (
                  <div key={i} className={`flex items-center justify-between p-4 rounded-xl border transition-colors ${
                    isDarkMode ? 'bg-[#131B2D] border-[#1E2638]/50 hover:border-[#1E2638]' : 'bg-slate-50 border-slate-100 hover:border-slate-200'
                  }`}>
                    <div className="flex items-center gap-4">
                      <div className={`w-10 h-10 rounded-full flex items-center justify-center ${isDarkMode ? 'bg-blue-500/10 text-blue-400' : 'bg-blue-100 text-blue-600'}`}>
                        {i % 2 === 0 ? '📥' : '📤'}
                      </div>
                      <div>
                        <p className={`font-medium ${isDarkMode ? 'text-slate-200' : 'text-slate-800'}`}>The Great Gatsby</p>
                        <p className={`text-sm ${isDarkMode ? 'text-slate-500' : 'text-slate-500'}`}>Member ID: {1000 + i}</p>
                      </div>
                    </div>
                    <div className="text-right">
                      <span className={`inline-block px-3 py-1 rounded-full text-xs font-medium ${
                        i % 2 === 0
                          ? (isDarkMode ? 'bg-emerald-500/10 text-emerald-400' : 'bg-emerald-100 text-emerald-700')
                          : (isDarkMode ? 'bg-amber-500/10 text-amber-400' : 'bg-amber-100 text-amber-700')
                      }`}>
                        {i % 2 === 0 ? 'Returned' : 'Issued'}
                      </span>
                      <p className="text-xs text-slate-500 mt-1">2 hours ago</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>

            <div className={`rounded-2xl border p-6 shadow-xl h-fit transition-colors duration-300 ${
              isDarkMode ? 'bg-[#1F2937] border-[#1E2638]' : 'bg-white border-slate-200'
            }`}>
              <h3 className={`text-lg font-semibold mb-6 ${isDarkMode ? 'text-white' : 'text-slate-900'}`}>Quick Actions</h3>
              <div className="grid grid-cols-2 gap-4">
                <QuickAction isDarkMode={isDarkMode} icon="➕" text="Add Book" />
                <QuickAction isDarkMode={isDarkMode} icon="👤" text="Add Member" />
                <QuickAction isDarkMode={isDarkMode} icon="📤" text="Issue Book" />
                <QuickAction isDarkMode={isDarkMode} icon="📥" text="Return Book" />
                <QuickAction isDarkMode={isDarkMode} icon="🔔" text="Reservations" />
                <QuickAction isDarkMode={isDarkMode} icon="📊" text="Reports" />
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}

function NavItem({ active, icon, text, onClick }: { active?: boolean, icon: string, text: string, onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className={`w-full flex items-center gap-3 px-4 py-3 rounded-xl font-medium transition-all ${
        active
          ? 'bg-[#1E3A8A] text-white shadow-lg'
          : 'text-blue-100/70 hover:text-white hover:bg-white/10'
      }`}
    >
      <span className="text-xl">{icon}</span>
      {text}
    </button>
  );
}

function StatCard({ title, value, change, icon, color, border, isDarkMode }: { title: string, value: string, change: string, icon: string, color: string, border: string, isDarkMode: boolean }) {
  return (
    <div className={`relative overflow-hidden rounded-2xl border p-6 shadow-xl transition-colors duration-300 ${
      isDarkMode ? 'bg-[#1F2937]' : 'bg-white'
    } ${border}`}>
      <div className={`absolute top-0 right-0 w-32 h-32 bg-gradient-to-bl ${color} rounded-bl-full -mr-10 -mt-10 blur-xl opacity-50`} />
      <div className="relative z-10">
        <div className="flex items-start justify-between mb-4">
          <p className={isDarkMode ? 'text-slate-400 font-medium' : 'text-slate-600 font-medium'}>{title}</p>
          <span className="text-2xl">{icon}</span>
        </div>
        <h4 className={`text-4xl font-bold mb-2 ${isDarkMode ? 'text-white' : 'text-slate-900'}`}>{value}</h4>
        <p className="text-sm text-slate-500">{change}</p>
      </div>
    </div>
  );
}

function QuickAction({ icon, text, isDarkMode }: { icon: string, text: string, isDarkMode: boolean }) {
  return (
    <button className={`flex flex-col items-center justify-center p-4 rounded-xl border transition-all group ${
      isDarkMode
        ? 'bg-[#131B2D] border-[#1E2638]/50 hover:bg-[#1E2638] hover:border-[#2A3449]'
        : 'bg-slate-50 border-slate-200 hover:bg-slate-100 hover:border-slate-300'
    }`}>
      <span className="text-2xl mb-2 group-hover:scale-110 transition-transform">{icon}</span>
      <span className={`text-sm font-medium ${isDarkMode ? 'text-slate-300' : 'text-slate-700'}`}>{text}</span>
    </button>
  );
}
