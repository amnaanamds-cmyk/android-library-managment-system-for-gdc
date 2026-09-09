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
    <div className="flex flex-col items-center justify-center p-6 border-2 border-dashed border-line rounded-xl bg-surface/50">
      <div className={`text-6xl mb-4 ${scanning ? "animate-pulse" : ""}`}>👆</div>
      {scanning ? (
        <div className="w-full max-w-xs">
          <div className="flex justify-between text-xs text-accent mb-1">
            <span>Scanning...</span>
            <span>{progress}%</span>
          </div>
          <div className="h-2 bg-line rounded-full overflow-hidden">
            <div className="h-full bg-accent-bg transition-all duration-100" style={{ width: `${progress}%` }} />
          </div>
        </div>
      ) : (
        <button onClick={startScan} className="px-4 py-2 bg-accent-bg text-on-accent rounded-lg font-bold text-sm hover:bg-accent-bg">
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
        <h1 className="text-3xl font-extrabold text-ink">🔐 Biometric Verification</h1>
        <p className="text-sm text-muted">Enrol member fingerprints to prevent library card misuse.</p>
      </div>

      <div className="grid grid-cols-4 gap-4">
        {[
          { label: "Total Members", value: members?.length || 0, color: "text-accent" },
          { label: "Enrolled", value: enrolledCount, color: "text-positive" },
          { label: "Not Enrolled", value: (members?.length || 0) - enrolledCount, color: "text-warning" },
          { label: "Enrolment Rate", value: members?.length ? Math.round((enrolledCount / members.length) * 100) + "%" : "0%", color: "text-accent" },
        ].map(s => (
          <div key={s.label} className="rounded-xl border border-line bg-surface-2 p-4 text-center">
            <div className={`text-2xl font-extrabold ${s.color}`}>{s.value}</div>
            <div className="text-xs text-muted mt-1">{s.label}</div>
          </div>
        ))}
      </div>

      <div className="rounded-xl border border-line bg-surface-2/10 p-4 text-xs text-accent">
        ℹ️ <b>Hardware Note:</b> For production use, connect a USB fingerprint reader. The scanner simulation generates a random SHA-256 hash for testing.
      </div>

      {enrollingMember && (
        <div className="fixed inset-0 bg-black/60 z-50 flex items-center justify-center p-4">
          <div className="bg-surface-2 border border-line p-6 rounded-2xl w-full max-w-md">
            <h2 className="text-xl font-bold text-ink mb-2">Enrol Fingerprint</h2>
            <p className="text-muted text-sm mb-6">Enrolling: <span className="font-bold text-ink">{enrollingMember.name}</span></p>
            
            <FingerprintScanner onScan={handleEnrolScan} />
            
            {enrolHash && (
              <div className="mt-4 p-3 bg-positive-soft/30 border border-positive rounded text-positive text-xs text-center font-mono break-all">
                Template: {enrolHash.substring(0, 32)}...
              </div>
            )}
            
            <div className="flex justify-end gap-3 mt-6">
              <button onClick={() => {setEnrollingMember(null); setEnrolHash("");}} className="px-4 py-2 text-body hover:text-ink">Cancel</button>
              <button onClick={saveEnrolment} disabled={!enrolHash} className="px-4 py-2 bg-positive text-on-accent rounded font-bold disabled:opacity-50">Save Biometric</button>
            </div>
          </div>
        </div>
      )}

      {verifyingMember && (
        <div className="fixed inset-0 bg-black/60 z-50 flex items-center justify-center p-4">
          <div className="bg-surface-2 border border-line p-6 rounded-2xl w-full max-w-md">
            <h2 className="text-xl font-bold text-ink mb-2">Verify Member</h2>
            <p className="text-muted text-sm mb-6">Verifying: <span className="font-bold text-ink">{verifyingMember.name}</span></p>
            
            <FingerprintScanner onScan={handleVerifyScan} />
            
            {verifyResult && (
              <div className={`mt-4 p-3 text-center font-bold rounded border ${verifyResult.success ? 'bg-positive-soft/30 border-positive text-positive' : 'bg-danger-soft/30 border-danger text-danger'}`}>
                {verifyResult.msg}
              </div>
            )}
            
            <div className="flex justify-center mt-6">
              <button onClick={() => {setVerifyingMember(null); setVerifyResult(null);}} className="px-6 py-2 bg-line text-ink rounded font-bold hover:bg-[#2A4065]">Close</button>
            </div>
          </div>
        </div>
      )}

      <div className="flex gap-3">
        <input value={search} onChange={e => setSearch(e.target.value)} placeholder="🔍 Search members..."
          className="flex-1 rounded-lg border border-line bg-surface-2 px-4 py-2 text-sm text-ink outline-none focus:border-accent" />
        <select value={filter} onChange={e => setFilter(e.target.value)}
          className="rounded-lg border border-line bg-surface-2 px-3 py-2 text-sm text-ink outline-none">
          <option>All</option><option>Enrolled</option><option>Not Enrolled</option>
        </select>
      </div>

      <div className="rounded-xl border border-line bg-surface-2 overflow-hidden shadow-xl">
        {loading ? (
          <div className="py-12 flex justify-center"><div className="h-8 w-8 animate-spin rounded-full border-4 border-accent border-t-transparent" /></div>
        ) : (
          <table className="w-full text-left text-sm text-body">
            <thead className="text-xs uppercase bg-surface/40 text-muted">
              <tr>
                <th className="px-4 py-3">Member ID</th>
                <th className="px-4 py-3">Name</th>
                <th className="px-4 py-3">Role</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Enrolled Date</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line/40">
              {filtered.map((member: any) => (
                <tr key={member.id} className="hover:bg-surface-2/10">
                  <td className="px-4 py-3 font-mono text-muted">{member.memberId || "—"}</td>
                  <td className="px-4 py-3 font-semibold text-ink">{member.name}</td>
                  <td className="px-4 py-3">{member.role || "Student"}</td>
                  <td className="px-4 py-3">
                    {member.biometricHash
                      ? <span className="text-xs font-bold text-positive">🔐 Enrolled</span>
                      : <span className="text-xs font-bold text-warning">⏳ Not Enrolled</span>}
                  </td>
                  <td className="px-4 py-3 text-xs">{member.biometricEnrolDate || "—"}</td>
                  <td className="px-4 py-3 text-right space-x-2">
                    <button onClick={() => setVerifyingMember(member)}
                      className="px-2 py-1 rounded bg-accent-bg/10 text-accent text-xs font-bold hover:bg-accent-bg/20">
                      Verify
                    </button>
                    {!member.biometricHash ? (
                      <button onClick={() => setEnrollingMember(member)}
                        className="px-2 py-1 rounded bg-positive/10 text-positive text-xs font-bold hover:bg-positive/20">
                        Enrol
                      </button>
                    ) : (
                      <button onClick={() => removeBiometric(member)}
                        className="px-2 py-1 rounded bg-danger/10 text-danger text-xs font-bold hover:bg-danger/20">
                        Remove
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {filtered.length === 0 && <tr><td colSpan={6} className="text-center py-8 text-muted">No members found.</td></tr>}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
