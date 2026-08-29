"use client";

import React, { useState } from "react";

export default function FineWaiverAIPage() {
  const [studentId, setStudentId] = useState("");
  const [reason, setReason] = useState("");
  const [analyzing, setAnalyzing] = useState(false);
  const [result, setResult] = useState<null | { recommendation: string, discount: number, logic: string }>(null);

  const handleAnalyze = (e: React.FormEvent) => {
    e.preventDefault();
    setAnalyzing(true);
    setResult(null);
    
    // Simulate AI processing time
    setTimeout(() => {
      setResult({
        recommendation: "Approve Partial Waiver",
        discount: 50,
        logic: "Student has a 98% on-time return rate historically. The stated reason ('Medical Emergency') aligns with institutional leniency policies. A 50% waiver is recommended to maintain accountability while showing empathy."
      });
      setAnalyzing(false);
    }, 2000);
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-500 max-w-5xl mx-auto">
      <div>
        <h1 className="text-3xl font-extrabold text-[#E8EEF8]">⚖️ Fine Waiver AI</h1>
        <p className="text-sm text-slate-400">Smart analysis for student fine waiver requests based on history and policy</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        <div className="rounded-xl border border-blue-950 bg-[#070F1E] p-6 shadow-xl h-fit">
          <h3 className="text-lg font-bold text-white mb-4">Submit Waiver Case</h3>
          <form onSubmit={handleAnalyze} className="space-y-4">
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-slate-400">Student ID / Roll No</label>
              <input
                type="text"
                required
                value={studentId}
                onChange={(e) => setStudentId(e.target.value)}
                className="mt-2 w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2.5 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]"
                placeholder="e.g. FA20-BSE-001"
              />
            </div>
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-slate-400">Stated Reason for Delay</label>
              <textarea
                required
                rows={4}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                className="mt-2 w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2.5 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B] resize-none"
                placeholder="Student claims they were hospitalized..."
              />
            </div>
            <button
              type="submit"
              disabled={analyzing || !studentId || !reason}
              className="w-full rounded-lg bg-gradient-to-r from-purple-600 to-indigo-600 py-3 text-sm font-bold text-white transition-all hover:from-purple-500 hover:to-indigo-500 shadow-lg disabled:opacity-50"
            >
              {analyzing ? "🧠 Analyzing..." : "✨ Generate AI Recommendation"}
            </button>
          </form>
        </div>

        <div className="rounded-xl border border-blue-950 bg-[#070F1E] p-6 shadow-xl flex flex-col justify-center min-h-[300px]">
          {!result && !analyzing && (
            <div className="text-center text-slate-500">
              <span className="text-4xl block mb-4">⚖️</span>
              <p className="font-bold">Awaiting Case Submission</p>
              <p className="text-xs mt-2">Enter details on the left to receive an AI-driven policy recommendation.</p>
            </div>
          )}
          
          {analyzing && (
            <div className="text-center">
              <div className="h-12 w-12 border-4 border-indigo-500 border-t-transparent rounded-full animate-spin mx-auto mb-4" />
              <p className="text-indigo-400 font-bold animate-pulse">Cross-referencing student history...</p>
            </div>
          )}

          {result && !analyzing && (
            <div className="animate-in slide-in-from-bottom-4 duration-500 space-y-4">
              <h3 className="text-sm font-bold uppercase tracking-wider text-slate-400 border-b border-[#1E3050] pb-2">AI Assessment</h3>
              
              <div className="bg-[#0D1F38] rounded-lg p-4 border border-[#1E3050]">
                <div className="flex justify-between items-center mb-2">
                  <span className="text-emerald-400 font-bold text-lg">{result.recommendation}</span>
                  <span className="bg-emerald-500/20 text-emerald-400 px-3 py-1 rounded-full text-xs font-bold">
                    {result.discount}% Discount
                  </span>
                </div>
                <p className="text-sm text-slate-300 leading-relaxed mt-4">
                  {result.logic}
                </p>
              </div>

              <div className="flex gap-2 pt-4">
                <button className="flex-1 bg-emerald-600 text-white rounded-lg py-2 text-sm font-bold hover:bg-emerald-500 transition-colors">
                  Approve AI Suggestion
                </button>
                <button className="flex-1 bg-red-600/20 text-red-400 border border-red-900/50 rounded-lg py-2 text-sm font-bold hover:bg-red-600/30 transition-colors">
                  Reject (Full Fine)
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
