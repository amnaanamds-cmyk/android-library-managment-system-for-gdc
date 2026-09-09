"use client";

import { useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { db } from "@/lib/firebase";
import { 
  collection, 
  doc, 
  setDoc, 
  query, 
  where, 
  getDocs, 
  updateDoc 
} from "firebase/firestore";
import { useRouter } from "next/navigation";
import { QRCodeCanvas } from "qrcode.react";
import QRScanner from "@/components/qr-scanner";
import { publishSnapshot } from "@/lib/directorate";

export default function OnboardPage() {
  const { user, profile, refreshProfile } = useAuth();
  const [tab, setTab] = useState<"create" | "join">("create");
  const [collegeName, setCollegeName] = useState("");
  const [collegeId, setCollegeId] = useState("");
  const [inviteCode, setInviteCode] = useState("");
  const [loading, setLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [createdInst, setCreatedInst] = useState<{id: string, qrData: string} | null>(null);
  const [showScanner, setShowScanner] = useState(false);
  const router = useRouter();

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!collegeName.trim() || !collegeId.trim()) return;
    setLoading(true);
    setErrorMsg(null);

    const cid = collegeId.trim().toUpperCase();
    if (!/^[A-Z0-9\-]+$/.test(cid)) {
      setErrorMsg("College Unique ID can only contain letters, numbers, and hyphens.");
      setLoading(false);
      return;
    }

    try {
      // Check if this ID already exists
      const { getDoc } = await import("firebase/firestore");
      const existing = await getDoc(doc(db, "institutions", cid));
      if (existing.exists()) {
        setErrorMsg("This College Unique ID is already in use. Please choose another or join it.");
        setLoading(false);
        return;
      }

      // Create institution document. ownerUid is what the security rules use
      // to recognise this account as the institution's owner — without it the
      // creator has no privileged route to their own settings.
      await setDoc(doc(db, "institutions", cid), {
        name: collegeName.trim(),
        inviteCode: cid, // For backward compatibility if any old code relies on it
        ownerUid: user?.uid || "",
        createdAt: Date.now(),
      });

      // Update the user profile BEFORE publishing the registry snapshot: the
      // rules authorise the snapshot write from this profile's institutionId,
      // so publishing first would be denied.
      if (user) {
        await updateDoc(doc(db, "users", user.uid), {
          institutionId: cid,
          role: "owner",
        });
      }

      // Register in the canonical directorate registry so the college appears
      // in the directorate portal immediately, with zeroed counts until its
      // first real sync publishes them.
      await publishSnapshot(cid, { name: collegeName.trim() }, "web");

      // Legacy mirror, for older builds that still read /colleges.
      await setDoc(
        doc(db, "colleges", cid),
        {
          collegeId: cid,
          collegeName: collegeName.trim(),
          name: collegeName.trim(),
          ownerUid: user?.uid || "",
          directorUid: user?.uid || "",
          createdAt: Date.now(),
        },
        { merge: true },
      );

      await refreshProfile();
      // Instead of navigating immediately, show the QR code
      const qrData = `NEXLIB_LINK|${cid}|owner|${Date.now()}`;
      setCreatedInst({ id: cid, qrData });
    } catch (err: any) {
      console.error(err);
      setErrorMsg(err.message || "Failed to create institution");
    } finally {
      setLoading(false);
    }
  };

  const handleJoin = async (e: React.FormEvent) => {
    e.preventDefault();
    const cid = inviteCode.trim().toUpperCase();
    if (!cid) return;
    setLoading(true);
    setErrorMsg(null);

    try {
      const { getDoc } = await import("firebase/firestore");
      const instDoc = await getDoc(doc(db, "institutions", cid));

      if (!instDoc.exists()) {
        throw new Error("Invalid College Unique ID. Institution not found.");
      }

      const instId = instDoc.id;

      // Update user document
      if (user) {
        await updateDoc(doc(db, "users", user.uid), {
          institutionId: instId,
          role: "staff",
        });
      }

      await refreshProfile();
      router.push("/dashboard");
    } catch (err: any) {
      console.error(err);
      setErrorMsg(err.message || "Failed to join institution");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-app via-app to-app px-4 py-12">
      <div className="w-full max-w-md space-y-8 rounded-2xl border border-line/50 bg-surface-2/90 p-8 shadow-2xl backdrop-blur">
        <div className="text-center">
          <h2 className="text-3xl font-extrabold text-accent-strong">Welcome to NEXLIB</h2>
          <p className="mt-2 text-sm text-muted">
            Set up or join an institution to continue
          </p>
        </div>

        <div className="flex border-b border-line/30">
          <button
            onClick={() => { setTab("create"); setErrorMsg(null); }}
            className={`flex-1 py-3 text-center text-sm font-semibold transition-colors ${
              tab === "create" ? "border-b-2 border-accent text-ink" : "text-muted hover:text-ink"
            }`}
          >
            Create Institution
          </button>
          <button
            onClick={() => { setTab("join"); setErrorMsg(null); }}
            className={`flex-1 py-3 text-center text-sm font-semibold transition-colors ${
              tab === "join" ? "border-b-2 border-accent text-ink" : "text-muted hover:text-ink"
            }`}
          >
            Join Institution
          </button>
        </div>

        {tab === "create" ? (
          <form onSubmit={handleCreate} className="space-y-6">
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-muted">
                College / Institution Name
              </label>
              <input
                type="text"
                required
                value={collegeName}
                onChange={(e) => setCollegeName(e.target.value)}
                className="mt-2 w-full rounded-lg border border-line bg-surface px-4 py-3 text-sm text-ink outline-none focus:border-accent"
                placeholder="e.g. Govt Degree College Sherpao"
              />
            </div>
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-muted mt-4">
                College Unique ID
              </label>
              <input
                type="text"
                required
                value={collegeId}
                onChange={(e) => setCollegeId(e.target.value)}
                className="mt-2 w-full uppercase rounded-lg border border-line bg-surface px-4 py-3 text-sm text-ink outline-none focus:border-accent"
                placeholder="e.g. GDC-MARDAN-01"
              />
            </div>
            <button
              type="submit"
              disabled={loading}
              className="bg-accent-bg w-full rounded-lg py-3 text-sm font-bold text-on-accent transition-all focus:outline-none disabled:opacity-50"
            >
              {loading ? "Creating..." : "Create & Enter"}
            </button>
            
            {createdInst && (
              <div className="mt-8 flex flex-col items-center justify-center rounded-xl bg-white p-6">
                <h3 className="mb-4 text-lg font-bold text-[#1E3A8A]">Device Link QR</h3>
                <QRCodeCanvas value={createdInst.qrData} size={200} />
                <p className="mt-4 text-center text-sm font-semibold text-body">
                  College ID: {createdInst.id}
                </p>
                <p className="mt-2 text-center text-xs text-muted">
                  Scan this code with NEXLIB Android to login instantly.
                </p>
                <button
                  type="button"
                  onClick={() => router.push("/dashboard")}
                  className="mt-6 w-full rounded-lg bg-accent-bg py-3 text-sm font-bold text-ink"
                >
                  Continue to Dashboard
                </button>
              </div>
            )}
          </form>
        ) : (
          <form onSubmit={handleJoin} className="space-y-6">
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-muted">
                College Unique ID
              </label>
              <input
                type="text"
                required
                value={inviteCode}
                onChange={(e) => setInviteCode(e.target.value)}
                className="mt-2 w-full uppercase text-center tracking-widest rounded-lg border border-line bg-surface px-4 py-3 text-lg font-bold text-ink outline-none focus:border-accent"
                placeholder="GDC-MARDAN-01"
              />
            </div>
            <button
              type="submit"
              disabled={loading}
              className="bg-accent-bg w-full rounded-lg py-3 text-sm font-bold text-on-accent transition-all focus:outline-none disabled:opacity-50"
            >
              {loading ? "Joining..." : "Join & Enter"}
            </button>
            
            <div className="relative flex items-center py-2">
              <div className="flex-grow border-t border-line"></div>
              <span className="flex-shrink-0 mx-4 text-muted text-xs">OR</span>
              <div className="flex-grow border-t border-line"></div>
            </div>

            {!showScanner ? (
              <button
                type="button"
                onClick={() => setShowScanner(true)}
                className="w-full rounded-lg border border-accent py-3 text-sm font-bold text-accent transition-all hover:bg-accent-bg/10 focus:outline-none"
              >
                Scan QR Code
              </button>
            ) : (
              <div className="flex flex-col items-center">
                <QRScanner 
                  onScan={(data) => {
                    if (data.startsWith("NEXLIB_LINK|")) {
                      const parts = data.split("|");
                      if (parts.length >= 2) {
                        setInviteCode(parts[1]);
                        setShowScanner(false);
                      }
                    } else {
                      setErrorMsg("Invalid QR code format.");
                    }
                  }} 
                  onError={(err) => console.log("Scan error", err)}
                />
                <button
                  type="button"
                  onClick={() => setShowScanner(false)}
                  className="mt-4 text-sm text-muted hover:text-ink"
                >
                  Cancel Scan
                </button>
              </div>
            )}
          </form>
        )}

        {errorMsg && (
          <div className="rounded-lg border border-danger/30 bg-danger/10 p-3 text-center text-xs text-danger">
            ⚠️ {errorMsg}
          </div>
        )}
      </div>
    </div>
  );
}
