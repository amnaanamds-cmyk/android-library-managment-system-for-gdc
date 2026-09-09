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
        <h1 className="text-3xl font-extrabold text-ink tracking-tight">
          🔗 College Sync Network
        </h1>
        <p className="text-sm text-muted mt-1">
          Connect your college to partner institutions and sync library data across the network.
        </p>
      </div>

      {/* My Sync Code */}
      <div className="rounded-xl border border-line bg-surface p-6">
        <h2 className="text-lg font-bold text-accent-strong mb-1">
          🏫 Your Institution Sync Code
        </h2>
        <p className="text-xs text-muted mb-5">
          Share this code with other colleges so they can connect to your institution.
          <br />
          Institution: <span className="text-ink font-semibold">{myInstitution?.name || "Loading…"}</span>
        </p>

        <div className="flex items-center gap-4">
          {codeLoading || !syncCode ? (
            <div className="flex items-center gap-3 h-16 px-6 rounded-xl border border-line bg-surface">
              <div className="h-5 w-5 animate-spin rounded-full border-2 border-accent border-t-transparent" />
              <span className="text-muted text-sm">Generating code…</span>
            </div>
          ) : (
            <div className="flex items-center gap-4 px-6 py-4 rounded-xl border border-accent/40 bg-surface shadow-inner">
              <span
                className="font-mono text-3xl font-extrabold tracking-[0.35em] text-accent-strong select-all"
                style={{ letterSpacing: "0.35em" }}
              >
                {syncCode}
              </span>
              <button
                onClick={copyCode}
                className={`px-4 py-2 rounded-lg text-xs font-bold transition-all ${
                  copied
                    ? "bg-positive/20 border border-positive/50 text-positive"
                    : "bg-accent-bg/20 border border-line/50 text-accent hover:bg-accent-bg/40"
                }`}
              >
                {copied ? "✅ Copied!" : "📋 Copy"}
              </button>
            </div>
          )}
        </div>

        <p className="mt-4 text-xs text-muted">
          ⚠️ Keep this code private — only share with trusted partner institutions.
        </p>
      </div>

      {/* Connect to a Partner */}
      <div className="rounded-2xl border border-line/50 bg-app p-6 shadow-xl">
        <h2 className="text-lg font-bold text-ink mb-1">🔌 Connect to a Partner College</h2>
        <p className="text-xs text-muted mb-5">
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
            className="flex-1 rounded-xl border border-line bg-surface px-5 py-3 text-center font-mono text-xl font-bold tracking-[0.3em] text-ink outline-none focus:border-accent uppercase"
          />
          <button
            type="submit"
            disabled={connectLoading || partnerCode.length < 6}
            className="bg-accent-bg px-6 py-3 rounded-xl text-on-accent font-bold text-sm disabled:opacity-50 transition-all whitespace-nowrap shadow-lg"
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
          <div className="mt-3 rounded-lg border border-danger/30 bg-danger/10 px-4 py-3 text-xs text-danger">
            ⚠️ {connectError}
          </div>
        )}
        {connectSuccess && (
          <div className="mt-3 rounded-lg border border-positive/30 bg-positive/10 px-4 py-3 text-xs text-positive font-semibold">
            {connectSuccess}
          </div>
        )}
      </div>

      {/* Connected Colleges */}
      <div className="rounded-2xl border border-line/50 bg-app p-6 shadow-xl">
        <h2 className="text-lg font-bold text-ink mb-5">
          🌐 Connected Colleges
          <span className="ml-2 px-2 py-0.5 rounded-full bg-surface-2/60 text-accent text-xs font-normal">
            {syncedColleges.length}
          </span>
        </h2>

        {syncedColleges.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-12 text-center text-muted">
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
      <div className="rounded-2xl border border-line bg-surface p-6">
        <h3 className="font-bold text-body mb-3 text-sm uppercase tracking-wider">ℹ️ How College Sync Works</h3>
        <ol className="space-y-2 text-xs text-muted list-decimal list-inside">
          <li>Each institution gets a unique <strong className="text-body">8-character Sync Code</strong>.</li>
          <li>Share your code with partner colleges (e.g., sister institutions in the same district).</li>
          <li>When connected, click <strong className="text-body">Sync Now</strong> to pull their latest catalog, member count, and transaction stats.</li>
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
    <div className="rounded-xl border border-line bg-surface p-5 space-y-4 transition-colors hover:border-accent">
      <div className="flex items-start justify-between gap-4">
        <div>
          <p className="font-bold text-ink text-base">{college.name}</p>
          <p className="text-xs text-muted mt-0.5">
            Code: <span className="font-mono text-accent font-bold tracking-widest">{college.syncCode}</span>
          </p>
          <p className="text-xs text-muted mt-1">
            Last synced: {college.lastSyncAt ? new Date(college.lastSyncAt).toLocaleString() : "Never"}
          </p>
        </div>
        <div className="flex gap-2 shrink-0">
          <button
            onClick={onSync}
            disabled={isSyncing}
            className="px-4 py-2 rounded-lg bg-positive/20 border border-positive/40 text-positive text-xs font-bold hover:bg-positive/40 disabled:opacity-50 transition-all"
          >
            {isSyncing ? (
              <span className="flex items-center gap-2">
                <span className="h-3 w-3 animate-spin rounded-full border-2 border-positive border-t-transparent" />
                Syncing…
              </span>
            ) : "🔄 Sync Now"}
          </button>
          <button
            onClick={onDisconnect}
            className="px-4 py-2 rounded-lg bg-danger/10 border border-danger/30 text-danger text-xs font-bold hover:bg-danger/20 transition-all"
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
              className="rounded-lg bg-app border border-line p-3 text-center"
            >
              <div className="text-xl">{stat.icon}</div>
              <div className="text-lg font-extrabold text-ink mt-1">{stat.value}</div>
              <div className="text-xs text-muted">{stat.label}</div>
            </div>
          ))}
        </div>
      )}

      {/* Sync result message */}
      {syncResult && (
        <div
          className={`rounded-lg px-4 py-2 text-xs font-semibold ${
            syncResult.startsWith("✅")
              ? "bg-positive/10 border border-positive/30 text-positive"
              : syncResult.startsWith("❌")
              ? "bg-danger/10 border border-danger/30 text-danger"
              : "bg-accent-bg/10 border border-accent/30 text-accent"
          }`}
        >
          {syncResult}
        </div>
      )}
    </div>
  );
}
