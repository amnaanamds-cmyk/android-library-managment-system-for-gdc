"use client";

import { useState, useEffect, useCallback } from "react";
import { useAuth } from "@/lib/auth-context";
import { db } from "@/lib/firebase";
import {
  doc,
  getDoc,
  collection,
  query,
  where,
  getDocs,
  updateDoc,
  arrayUnion,
  arrayRemove,
  onSnapshot,
  Timestamp,
} from "firebase/firestore";

// ─── Types ──────────────────────────────────────────────────────────────────

interface InstitutionDoc {
  name: string;
  inviteCode: string;
  syncCode?: string;
  syncedWith?: string[];   // array of institution IDs this college is paired with
  createdAt: number;
}

interface SyncedCollegeInfo {
  id: string;
  name: string;
  syncCode: string;
  lastSyncAt?: number;
}

interface SyncSnapshot {
  books: number;
  members: number;
  transactions: number;
}

// ─── Helpers ────────────────────────────────────────────────────────────────

function generateSyncCode(): string {
  // Alphanumeric 8-char code, easier to share verbally
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  return Array.from({ length: 8 }, () => chars[Math.floor(Math.random() * chars.length)]).join("");
}

function formatDate(ts?: number): string {
  if (!ts) return "Never";
  return new Date(ts).toLocaleString("en-PK", {
    day: "2-digit", month: "short", year: "numeric",
    hour: "2-digit", minute: "2-digit",
  });
}

// ─── Main Page ───────────────────────────────────────────────────────────────

export default function SyncPage() {
  const { user, profile } = useAuth();

  const [myInstitution, setMyInstitution] = useState<(InstitutionDoc & { id: string }) | null>(null);
  const [syncedColleges, setSyncedColleges] = useState<SyncedCollegeInfo[]>([]);
  const [syncSnapshots, setSyncSnapshots] = useState<Record<string, SyncSnapshot>>({});

  const [partnerCode, setPartnerCode] = useState("");
  const [connectLoading, setConnectLoading] = useState(false);
  const [connectError, setConnectError] = useState<string | null>(null);
  const [connectSuccess, setConnectSuccess] = useState<string | null>(null);

  const [syncingId, setSyncingId] = useState<string | null>(null);
  const [syncResults, setSyncResults] = useState<Record<string, string>>({});

  const [codeLoading, setCodeLoading] = useState(false);
  const [copied, setCopied] = useState(false);

  // ── Load own institution ───────────────────────────────────────────────────
  useEffect(() => {
    if (!profile?.institutionId) return;
    const instRef = doc(db, "institutions", profile.institutionId);
    const unsub = onSnapshot(instRef, async (snap) => {
      if (!snap.exists()) return;
      const data = snap.data() as InstitutionDoc;
      setMyInstitution({ ...data, id: snap.id });

      // Load all synced colleges
      if (data.syncedWith && data.syncedWith.length > 0) {
        const details: SyncedCollegeInfo[] = [];
        for (const peerId of data.syncedWith) {
          const peerSnap = await getDoc(doc(db, "institutions", peerId));
          if (peerSnap.exists()) {
            const peerData = peerSnap.data() as InstitutionDoc;
            details.push({
              id: peerId,
              name: peerData.name,
              syncCode: peerData.syncCode || peerData.inviteCode,
              lastSyncAt: (peerSnap.data() as any)?.lastSyncAt,
            });
          }
        }
        setSyncedColleges(details);
      } else {
        setSyncedColleges([]);
      }
    });
    return () => unsub();
  }, [profile?.institutionId]);

  // ── Generate / ensure sync code ───────────────────────────────────────────
  const ensureSyncCode = useCallback(async () => {
    if (!myInstitution) return;
    if (myInstitution.syncCode) return; // Already has one
    setCodeLoading(true);
    try {
      const newCode = generateSyncCode();
      await updateDoc(doc(db, "institutions", myInstitution.id), { syncCode: newCode });
    } finally {
      setCodeLoading(false);
    }
  }, [myInstitution]);

  useEffect(() => {
    if (myInstitution && !myInstitution.syncCode) {
      ensureSyncCode();
    }
  }, [myInstitution, ensureSyncCode]);

  // ── Copy sync code ─────────────────────────────────────────────────────────
  const copyCode = () => {
    if (!myInstitution?.syncCode) return;
    navigator.clipboard.writeText(myInstitution.syncCode);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  // ── Connect to partner college ────────────────────────────────────────────
  const handleConnect = async (e: React.FormEvent) => {
    e.preventDefault();
    const code = partnerCode.trim().toUpperCase();
    if (!code || !myInstitution || !profile?.institutionId) return;
    if (code === myInstitution.syncCode) {
      setConnectError("You cannot sync with yourself!");
      return;
    }
    setConnectLoading(true);
    setConnectError(null);
    setConnectSuccess(null);

    try {
      // Find institution by syncCode
      const q = query(collection(db, "institutions"), where("syncCode", "==", code));
      const snap = await getDocs(q);

      if (snap.empty) {
        // Fallback: try inviteCode for backward compat
        const q2 = query(collection(db, "institutions"), where("inviteCode", "==", code));
        const snap2 = await getDocs(q2);
        if (snap2.empty) {
          setConnectError("No institution found with that sync code. Double-check and try again.");
          return;
        }
        const partnerDoc = snap2.docs[0];
        await linkColleges(profile.institutionId, partnerDoc.id, partnerDoc.data().name);
      } else {
        const partnerDoc = snap.docs[0];
        await linkColleges(profile.institutionId, partnerDoc.id, partnerDoc.data().name);
      }
    } catch (err: any) {
      setConnectError(err.message || "Failed to connect. Try again.");
    } finally {
      setConnectLoading(false);
    }
  };

  const linkColleges = async (myId: string, partnerId: string, partnerName: string) => {
    if (myInstitution?.syncedWith?.includes(partnerId)) {
      setConnectError(`Already connected to ${partnerName}.`);
      return;
    }
    // Bi-directional link
    await updateDoc(doc(db, "institutions", myId), {
      syncedWith: arrayUnion(partnerId),
    });
    await updateDoc(doc(db, "institutions", partnerId), {
      syncedWith: arrayUnion(myId),
    });
    setPartnerCode("");
    setConnectSuccess(`✅ Successfully connected to ${partnerName}! Their data is now available.`);
  };

  // ── Disconnect partner ─────────────────────────────────────────────────────
  const handleDisconnect = async (partner: SyncedCollegeInfo) => {
    if (!confirm(`Disconnect from ${partner.name}? Their data will no longer sync.`)) return;
    if (!profile?.institutionId) return;
    await updateDoc(doc(db, "institutions", profile.institutionId), {
      syncedWith: arrayRemove(partner.id),
    });
    await updateDoc(doc(db, "institutions", partner.id), {
      syncedWith: arrayRemove(profile.institutionId),
    });
  };

  // ── Pull sync data from partner ────────────────────────────────────────────
  const handleSync = async (partner: SyncedCollegeInfo) => {
    setSyncingId(partner.id);
    setSyncResults((prev) => ({ ...prev, [partner.id]: "Syncing..." }));
    try {
      // Read partner's books, members, transactions counts as a "preview sync"
      const [booksSnap, membersSnap, txSnap] = await Promise.all([
        getDocs(query(collection(db, "institutions", partner.id, "books"))),
        getDocs(query(collection(db, "institutions", partner.id, "members"))),
        getDocs(query(collection(db, "institutions", partner.id, "transactions"))),
      ]);
      setSyncSnapshots((prev) => ({
        ...prev,
        [partner.id]: {
          books: booksSnap.size,
          members: membersSnap.size,
          transactions: txSnap.size,
        },
      }));
      // Update lastSyncAt on our institution
      if (profile?.institutionId) {
        await updateDoc(doc(db, "institutions", profile.institutionId), {
          [`lastSyncAt_${partner.id}`]: Date.now(),
        });
      }
      setSyncResults((prev) => ({
        ...prev,
        [partner.id]: `✅ Sync complete — ${booksSnap.size} books, ${membersSnap.size} members, ${txSnap.size} transactions retrieved.`,
      }));
    } catch (err: any) {
      setSyncResults((prev) => ({
        ...prev,
        [partner.id]: `❌ Sync failed: ${err.message}`,
      }));
    } finally {
      setSyncingId(null);
    }
  };

  // ─── Render ────────────────────────────────────────────────────────────────

  const syncCode = myInstitution?.syncCode;

  return (
    <div className="space-y-8 animate-in fade-in duration-500 max-w-5xl mx-auto">
      {/* Header */}
      <div>
        <h1 className="text-3xl font-extrabold text-[#E8EEF8] tracking-tight">
          🔗 College Sync Network
        </h1>
        <p className="text-sm text-slate-400 mt-1">
          Connect your college to partner institutions and sync library data across the network.
        </p>
      </div>

      {/* My Sync Code */}
      <div className="rounded-2xl border border-blue-800/50 bg-gradient-to-br from-[#0A1628] to-[#071020] p-6 shadow-2xl">
        <h2 className="text-lg font-bold text-[#E6C96E] mb-1">
          🏫 Your Institution Sync Code
        </h2>
        <p className="text-xs text-slate-400 mb-5">
          Share this code with other colleges so they can connect to your institution.
          <br />
          Institution: <span className="text-white font-semibold">{myInstitution?.name || "Loading…"}</span>
        </p>

        <div className="flex items-center gap-4">
          {codeLoading || !syncCode ? (
            <div className="flex items-center gap-3 h-16 px-6 rounded-xl border border-blue-800 bg-[#0D1F38]">
              <div className="h-5 w-5 animate-spin rounded-full border-2 border-blue-400 border-t-transparent" />
              <span className="text-slate-400 text-sm">Generating code…</span>
            </div>
          ) : (
            <div className="flex items-center gap-4 px-6 py-4 rounded-xl border border-[#C8A84B]/40 bg-[#0D1F38] shadow-inner">
              <span
                className="font-mono text-3xl font-extrabold tracking-[0.35em] text-[#E6C96E] select-all"
                style={{ letterSpacing: "0.35em" }}
              >
                {syncCode}
              </span>
              <button
                onClick={copyCode}
                className={`px-4 py-2 rounded-lg text-xs font-bold transition-all ${
                  copied
                    ? "bg-emerald-500/20 border border-emerald-500/50 text-emerald-300"
                    : "bg-blue-600/20 border border-blue-600/50 text-blue-300 hover:bg-blue-600/40"
                }`}
              >
                {copied ? "✅ Copied!" : "📋 Copy"}
              </button>
            </div>
          )}
        </div>

        <p className="mt-4 text-xs text-slate-500">
          ⚠️ Keep this code private — only share with trusted partner institutions.
        </p>
      </div>

      {/* Connect to a Partner */}
      <div className="rounded-2xl border border-blue-800/50 bg-[#071020] p-6 shadow-xl">
        <h2 className="text-lg font-bold text-white mb-1">🔌 Connect to a Partner College</h2>
        <p className="text-xs text-slate-400 mb-5">
          Enter the 8-character sync code of the college you want to connect with.
        </p>

        <form onSubmit={handleConnect} className="flex flex-col sm:flex-row gap-3">
          <input
            type="text"
            value={partnerCode}
            onChange={(e) => {
              setPartnerCode(e.target.value.toUpperCase());
              setConnectError(null);
              setConnectSuccess(null);
            }}
            maxLength={8}
            placeholder="e.g. ABCD1234"
            className="flex-1 rounded-xl border border-[#1E3050] bg-[#0D1F38] px-5 py-3 text-center font-mono text-xl font-bold tracking-[0.3em] text-[#E8EEF8] outline-none focus:border-[#C8A84B] uppercase"
          />
          <button
            type="submit"
            disabled={connectLoading || partnerCode.length < 6}
            className="px-6 py-3 rounded-xl bg-gradient-to-r from-[#1E5FD4] to-[#2872F0] text-white font-bold text-sm hover:from-[#2872F0] hover:to-[#3D8EFF] disabled:opacity-50 transition-all whitespace-nowrap shadow-lg"
          >
            {connectLoading ? (
              <span className="flex items-center gap-2">
                <span className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                Connecting…
              </span>
            ) : (
              "🔗 Connect"
            )}
          </button>
        </form>

        {connectError && (
          <div className="mt-3 rounded-lg border border-red-500/30 bg-red-500/10 px-4 py-3 text-xs text-red-300">
            ⚠️ {connectError}
          </div>
        )}
        {connectSuccess && (
          <div className="mt-3 rounded-lg border border-emerald-500/30 bg-emerald-500/10 px-4 py-3 text-xs text-emerald-300 font-semibold">
            {connectSuccess}
          </div>
        )}
      </div>

      {/* Connected Colleges */}
      <div className="rounded-2xl border border-blue-800/50 bg-[#071020] p-6 shadow-xl">
        <h2 className="text-lg font-bold text-white mb-5">
          🌐 Connected Colleges
          <span className="ml-2 px-2 py-0.5 rounded-full bg-blue-900/60 text-blue-300 text-xs font-normal">
            {syncedColleges.length}
          </span>
        </h2>

        {syncedColleges.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-12 text-center text-slate-500">
            <div className="text-5xl mb-3">🏫</div>
            <p className="font-bold text-sm uppercase tracking-widest">No connections yet</p>
            <p className="text-xs mt-1">Connect to a partner college above to start syncing data.</p>
          </div>
        ) : (
          <div className="space-y-4">
            {syncedColleges.map((college) => (
              <CollegeCard
                key={college.id}
                college={college}
                snapshot={syncSnapshots[college.id]}
                syncResult={syncResults[college.id]}
                isSyncing={syncingId === college.id}
                onSync={() => handleSync(college)}
                onDisconnect={() => handleDisconnect(college)}
              />
            ))}
          </div>
        )}
      </div>

      {/* How it works */}
      <div className="rounded-2xl border border-blue-900/30 bg-[#060e1a] p-6">
        <h3 className="font-bold text-slate-300 mb-3 text-sm uppercase tracking-wider">ℹ️ How College Sync Works</h3>
        <ol className="space-y-2 text-xs text-slate-400 list-decimal list-inside">
          <li>Each institution gets a unique <strong className="text-slate-200">8-character Sync Code</strong>.</li>
          <li>Share your code with partner colleges (e.g., sister institutions in the same district).</li>
          <li>When connected, click <strong className="text-slate-200">Sync Now</strong> to pull their latest catalog, member count, and transaction stats.</li>
          <li>Both institutions remain independent — only aggregate data is shared for visibility.</li>
          <li>Disconnect at any time to remove cross-institution access.</li>
        </ol>
      </div>
    </div>
  );
}

// ─── College Card ─────────────────────────────────────────────────────────────

function CollegeCard({
  college,
  snapshot,
  syncResult,
  isSyncing,
  onSync,
  onDisconnect,
}: {
  college: SyncedCollegeInfo;
  snapshot?: SyncSnapshot;
  syncResult?: string;
  isSyncing: boolean;
  onSync: () => void;
  onDisconnect: () => void;
}) {
  return (
    <div className="rounded-xl border border-blue-900/40 bg-[#0D1B2E] p-5 space-y-4 transition-all hover:border-blue-700/50">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="font-bold text-white text-base">{college.name}</p>
          <p className="text-xs text-slate-400 mt-0.5">
            Code: <span className="font-mono text-[#C8A84B] font-bold tracking-widest">{college.syncCode}</span>
          </p>
          <p className="text-xs text-slate-500 mt-1">
            Last synced: {college.lastSyncAt ? new Date(college.lastSyncAt).toLocaleString() : "Never"}
          </p>
        </div>
        <div className="flex gap-2 shrink-0">
          <button
            onClick={onSync}
            disabled={isSyncing}
            className="px-4 py-2 rounded-lg bg-emerald-600/20 border border-emerald-600/40 text-emerald-300 text-xs font-bold hover:bg-emerald-600/40 disabled:opacity-50 transition-all"
          >
            {isSyncing ? (
              <span className="flex items-center gap-2">
                <span className="h-3 w-3 animate-spin rounded-full border-2 border-emerald-300 border-t-transparent" />
                Syncing…
              </span>
            ) : "🔄 Sync Now"}
          </button>
          <button
            onClick={onDisconnect}
            className="px-4 py-2 rounded-lg bg-red-600/10 border border-red-600/30 text-red-400 text-xs font-bold hover:bg-red-600/20 transition-all"
          >
            ✖ Disconnect
          </button>
        </div>
      </div>

      {/* Snapshot stats */}
      {snapshot && (
        <div className="grid grid-cols-3 gap-3">
          {[
            { label: "Books", value: snapshot.books, icon: "📚" },
            { label: "Members", value: snapshot.members, icon: "👥" },
            { label: "Transactions", value: snapshot.transactions, icon: "🔄" },
          ].map((stat) => (
            <div
              key={stat.label}
              className="rounded-lg bg-[#071428] border border-blue-950 p-3 text-center"
            >
              <div className="text-xl">{stat.icon}</div>
              <div className="text-lg font-extrabold text-white mt-1">{stat.value}</div>
              <div className="text-xs text-slate-400">{stat.label}</div>
            </div>
          ))}
        </div>
      )}

      {/* Sync result message */}
      {syncResult && (
        <div
          className={`rounded-lg px-4 py-2 text-xs font-semibold ${
            syncResult.startsWith("✅")
              ? "bg-emerald-500/10 border border-emerald-500/30 text-emerald-300"
              : syncResult.startsWith("❌")
              ? "bg-red-500/10 border border-red-500/30 text-red-300"
              : "bg-blue-500/10 border border-blue-500/30 text-blue-300"
          }`}
        >
          {syncResult}
        </div>
      )}
    </div>
  );
}
