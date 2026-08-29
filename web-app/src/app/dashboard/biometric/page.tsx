"use client";
import React, { useState } from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";

// Mock Fingerprint scanner for web demonstration
function FingerprintScanner({ onScan }: { onScan: (hash: string) => void }) {
  const [scanning, setScanning] = useState(false);
  const [progress, setProgress] = useState(0);

  const startScan = () => {
    setScanning(true);
    setProgress(0);
    const interval = setInterval(() => {
      setProgress(p => {
        if (p >= 100) {
          clearInterval(interval);
          setScanning(false);
          const hash = Array.from({length: 64}, () => Math.floor(Math.random()*16).toString(16)).join('');
          onScan(hash);
          return 100;
        }
        return p + Math.floor(Math.random() * 10) + 5;
      });
    }, 100);
  };

  return (
    <div className="flex flex-col items-center justify-center p-6 border-2 border-dashed border-[#1E3050] rounded-xl bg-[#0D1F38]/50">
      <div className={`text-6xl mb-4 ${scanning ? "animate-pulse" : ""}`}>👆</div>
      {scanning ? (
        <div className="w-full max-w-xs">
          <div className="flex justify-between text-xs text-blue-400 mb-1">
            <span>Scanning...</span>
            <span>{progress}%</span>
          </div>
          <div className="h-2 bg-[#1E3050] rounded-full overflow-hidden">
            <div className="h-full bg-blue-500 transition-all duration-100" style={{ width: `${progress}%` }} />
          </div>
        </div>
      ) : (
        <button onClick={startScan} className="px-4 py-2 bg-blue-600 text-white rounded-lg font-bold text-sm hover:bg-blue-500">
          Simulate Scan
        </button>
      )}
    </div>
  );
}

export default function BiometricPage() {
  const { data: members, loading, updateRecord } = useTenantCollection("members");
  const [search, setSearch] = useState("");
  const [filter, setFilter] = useState("All");
  
  const [enrollingMember, setEnrollingMember] = useState<any>(null);
  const [verifyingMember, setVerifyingMember] = useState<any>(null);
  
  const [enrolHash, setEnrolHash] = useState("");
  const [verifyResult, setVerifyResult] = useState<{success: boolean; msg: string} | null>(null);

  const filtered = (members || []).filter((m: any) => {
    const q = search.toLowerCase();
    const matchQ = !q || (m.name || "").toLowerCase().includes(q) || (m.memberId || "").toLowerCase().includes(q);
    const matchF = filter === "All" || (filter === "Enrolled" && m.biometricHash) || (filter === "Not Enrolled" && !m.biometricHash);
    return matchQ && matchF;
  });

  const enrolledCount = (members || []).filter((m: any) => m.biometricHash).length;

  const handleEnrolScan = (hash: string) => {
    setEnrolHash(hash);
  };

  const saveEnrolment = async () => {
    if (!enrollingMember || !enrolHash) return;
    await updateRecord(enrollingMember.id, {
      biometricHash: enrolHash,
      biometricEnrolDate: new Date().toISOString().split('T')[0]
    });
    alert("Biometric enrolled successfully!");
    setEnrollingMember(null);
    setEnrolHash("");
  };

  const handleVerifyScan = (hash: string) => {
    if (!verifyingMember) return;
    const stored = verifyingMember.biometricHash;
    if (!stored) {
      setVerifyResult({ success: true, msg: "⚠️ No biometric enrolled — Proceeding manually" });
    } else if (stored === hash) {
      setVerifyResult({ success: true, msg: "✅ MATCH — Access Granted" });
      updateRecord(verifyingMember.id, { biometricLastVerified: new Date().toISOString().replace('T', ' ').substring(0, 16) });
    } else {
      // Simulate real verification where we might randomly match for demo purposes if not exact
      const score = Math.floor(Math.random() * 30) + 70;
      if (score >= 75) {
         setVerifyResult({ success: true, msg: `✅ MATCH (${score}%) — Access Granted` });
         updateRecord(verifyingMember.id, { biometricLastVerified: new Date().toISOString().replace('T', ' ').substring(0, 16) });
      } else {
         setVerifyResult({ success: false, msg: `❌ MISMATCH (${score}%) — Access Denied` });
      }
    }
  };

  const removeBiometric = async (member: any) => {
    if (!confirm(`Remove biometric data for ${member.name}?`)) return;
    await updateRecord(member.id, { biometricHash: "", biometricEnrolDate: "" });
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-500 max-w-6xl mx-auto">
      <div>
        <h1 className="text-3xl font-extrabold text-[#E8EEF8]">🔐 Biometric Verification</h1>
        <p className="text-sm text-slate-400">Enrol member fingerprints to prevent library card misuse.</p>
      </div>

      <div className="grid grid-cols-4 gap-4">
        {[
          { label: "Total Members", value: members?.length || 0, color: "text-blue-400" },
          { label: "Enrolled", value: enrolledCount, color: "text-emerald-400" },
          { label: "Not Enrolled", value: (members?.length || 0) - enrolledCount, color: "text-amber-400" },
          { label: "Enrolment Rate", value: members?.length ? Math.round((enrolledCount / members.length) * 100) + "%" : "0%", color: "text-violet-400" },
        ].map(s => (
          <div key={s.label} className="rounded-xl border border-blue-950 bg-[#070F1E] p-4 text-center">
            <div className={`text-2xl font-extrabold ${s.color}`}>{s.value}</div>
            <div className="text-xs text-slate-400 mt-1">{s.label}</div>
          </div>
        ))}
      </div>

      <div className="rounded-xl border border-blue-950 bg-blue-900/10 p-4 text-xs text-blue-200">
        ℹ️ <b>Hardware Note:</b> For production use, connect a USB fingerprint reader. The scanner simulation generates a random SHA-256 hash for testing.
      </div>

      {enrollingMember && (
        <div className="fixed inset-0 bg-black/60 z-50 flex items-center justify-center p-4">
          <div className="bg-[#070F1E] border border-blue-900 p-6 rounded-2xl w-full max-w-md">
            <h2 className="text-xl font-bold text-white mb-2">Enrol Fingerprint</h2>
            <p className="text-slate-400 text-sm mb-6">Enrolling: <span className="font-bold text-white">{enrollingMember.name}</span></p>
            
            <FingerprintScanner onScan={handleEnrolScan} />
            
            {enrolHash && (
              <div className="mt-4 p-3 bg-emerald-900/30 border border-emerald-900 rounded text-emerald-400 text-xs text-center font-mono break-all">
                Template: {enrolHash.substring(0, 32)}...
              </div>
            )}
            
            <div className="flex justify-end gap-3 mt-6">
              <button onClick={() => {setEnrollingMember(null); setEnrolHash("");}} className="px-4 py-2 text-slate-300 hover:text-white">Cancel</button>
              <button onClick={saveEnrolment} disabled={!enrolHash} className="px-4 py-2 bg-emerald-600 text-white rounded font-bold disabled:opacity-50">Save Biometric</button>
            </div>
          </div>
        </div>
      )}

      {verifyingMember && (
        <div className="fixed inset-0 bg-black/60 z-50 flex items-center justify-center p-4">
          <div className="bg-[#070F1E] border border-blue-900 p-6 rounded-2xl w-full max-w-md">
            <h2 className="text-xl font-bold text-white mb-2">Verify Member</h2>
            <p className="text-slate-400 text-sm mb-6">Verifying: <span className="font-bold text-white">{verifyingMember.name}</span></p>
            
            <FingerprintScanner onScan={handleVerifyScan} />
            
            {verifyResult && (
              <div className={`mt-4 p-3 text-center font-bold rounded border ${verifyResult.success ? 'bg-emerald-900/30 border-emerald-900 text-emerald-400' : 'bg-red-900/30 border-red-900 text-red-400'}`}>
                {verifyResult.msg}
              </div>
            )}
            
            <div className="flex justify-center mt-6">
              <button onClick={() => {setVerifyingMember(null); setVerifyResult(null);}} className="px-6 py-2 bg-[#1E3050] text-white rounded font-bold hover:bg-[#2A4065]">Close</button>
            </div>
          </div>
        </div>
      )}

      <div className="flex gap-3">
        <input value={search} onChange={e => setSearch(e.target.value)} placeholder="🔍 Search members..."
          className="flex-1 rounded-lg border border-[#1E3050] bg-[#070F1E] px-4 py-2 text-sm text-[#E8EEF8] outline-none focus:border-blue-500" />
        <select value={filter} onChange={e => setFilter(e.target.value)}
          className="rounded-lg border border-[#1E3050] bg-[#070F1E] px-3 py-2 text-sm text-[#E8EEF8] outline-none">
          <option>All</option><option>Enrolled</option><option>Not Enrolled</option>
        </select>
      </div>

      <div className="rounded-xl border border-blue-950 bg-[#070F1E] overflow-hidden shadow-xl">
        {loading ? (
          <div className="py-12 flex justify-center"><div className="h-8 w-8 animate-spin rounded-full border-4 border-blue-500 border-t-transparent" /></div>
        ) : (
          <table className="w-full text-left text-sm text-slate-300">
            <thead className="text-xs uppercase bg-[#0D1F38]/40 text-slate-400">
              <tr>
                <th className="px-4 py-3">Member ID</th>
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3">Role</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Enrolled Date</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-blue-950/40">
              {filtered.map((member: any) => (
                <tr key={member.id} className="hover:bg-blue-950/10">
                  <td className="px-4 py-3 font-mono text-slate-400">{member.memberId || "—"}</td>
                  <td className="px-4 py-3 font-semibold text-white">{member.name}</td>
                  <td className="px-4 py-3">{member.role || "Student"}</td>
                  <td className="px-4 py-3">
                    {member.biometricHash
                      ? <span className="text-xs font-bold text-emerald-400">🔐 Enrolled</span>
                      : <span className="text-xs font-bold text-amber-400">⏳ Not Enrolled</span>}
                  </td>
                  <td className="px-4 py-3 text-xs">{member.biometricEnrolDate || "—"}</td>
                  <td className="px-4 py-3 text-right space-x-2">
                    <button onClick={() => setVerifyingMember(member)}
                      className="px-2 py-1 rounded bg-blue-500/10 text-blue-400 text-xs font-bold hover:bg-blue-500/20">
                      Verify
                    </button>
                    {!member.biometricHash ? (
                      <button onClick={() => setEnrollingMember(member)}
                        className="px-2 py-1 rounded bg-emerald-500/10 text-emerald-400 text-xs font-bold hover:bg-emerald-500/20">
                        Enrol
                      </button>
                    ) : (
                      <button onClick={() => removeBiometric(member)}
                        className="px-2 py-1 rounded bg-red-500/10 text-red-400 text-xs font-bold hover:bg-red-500/20">
                        Remove
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {filtered.length === 0 && <tr><td colSpan={6} className="text-center py-8 text-slate-500">No members found.</td></tr>}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
