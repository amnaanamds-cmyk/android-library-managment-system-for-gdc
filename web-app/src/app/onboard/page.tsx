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
import { canViewDirectorate } from "@/lib/roles";

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
        if (canViewDirectorate(profile?.role)) {
          throw new Error(
            "This is a directorate account and cannot create a college. " +
              "Sign in with a college account instead.",
          );
        }
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

      // Update user document. Never write role for an account that already
      // holds a directorate role: joining a college would otherwise demote a
      // directorate_admin to "staff" and quietly destroy its access.
      if (user) {
        if (canViewDirectorate(profile?.role)) {
          throw new Error(
            "This is a directorate account and cannot join a college. " +
              "Sign in with a college account instead.",
          );
        }
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
    <div className="flex min-h-screen items-center justify-center bg-gradient-to-br from-[#060C18] via-[#0D1B2A] to-[#060C18] px-4 py-12">
      <div className="w-full max-w-md space-y-8 rounded-2xl border border-blue-900/50 bg-[#0D1C37]/90 p-8 shadow-2xl backdrop-blur">
        <div className="text-center">
          <h2 className="text-3xl font-extrabold text-[#E6C96E]">Welcome to NEXLIB</h2>
          <p className="mt-2 text-sm text-slate-400">
            Set up or join an institution to continue
          </p>
        </div>

        <div className="flex border-b border-blue-900/30">
          <button
            onClick={() => { setTab("create"); setErrorMsg(null); }}
            className={`flex-1 py-3 text-center text-sm font-semibold transition-colors ${
              tab === "create" ? "border-b-2 border-[#C8A84B] text-white" : "text-slate-400 hover:text-white"
            }`}
          >
            Create Institution
          </button>
          <button
            onClick={() => { setTab("join"); setErrorMsg(null); }}
            className={`flex-1 py-3 text-center text-sm font-semibold transition-colors ${
              tab === "join" ? "border-b-2 border-[#C8A84B] text-white" : "text-slate-400 hover:text-white"
            }`}
          >
            Join Institution
          </button>
        </div>

        {tab === "create" ? (
          <form onSubmit={handleCreate} className="space-y-6">
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-[#A0B4CC]">
                College / Institution Name
              </label>
              <input
                type="text"
                required
                value={collegeName}
                onChange={(e) => setCollegeName(e.target.value)}
                className="mt-2 w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-3 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]"
                placeholder="e.g. Govt Degree College Sherpao"
              />
            </div>
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-[#A0B4CC] mt-4">
                College Unique ID
              </label>
              <input
                type="text"
                required
                value={collegeId}
                onChange={(e) => setCollegeId(e.target.value)}
                className="mt-2 w-full uppercase rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-3 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]"
                placeholder="e.g. GDC-MARDAN-01"
              />
            </div>
            <button
              type="submit"
              disabled={loading}
              className="w-full rounded-lg bg-gradient-to-r from-[#1E5FD4] to-[#2872F0] py-3 text-sm font-bold text-white transition-all hover:from-[#2872F0] focus:outline-none disabled:opacity-50"
            >
              {loading ? "Creating..." : "Create & Enter"}
            </button>
            
            {createdInst && (
              <div className="mt-8 flex flex-col items-center justify-center rounded-xl bg-white p-6">
                <h3 className="mb-4 text-lg font-bold text-[#1E3A8A]">Device Link QR</h3>
                <QRCodeCanvas value={createdInst.qrData} size={200} />
                <p className="mt-4 text-center text-sm font-semibold text-slate-700">
                  College ID: {createdInst.id}
                </p>
                <p className="mt-2 text-center text-xs text-slate-500">
                  Scan this code with NEXLIB Android to login instantly.
                </p>
                <button
                  type="button"
                  onClick={() => router.push("/dashboard")}
                  className="mt-6 w-full rounded-lg bg-[#2872F0] py-3 text-sm font-bold text-white"
                >
                  Continue to Dashboard
                </button>
              </div>
            )}
          </form>
        ) : (
          <form onSubmit={handleJoin} className="space-y-6">
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-[#A0B4CC]">
                College Unique ID
              </label>
              <input
                type="text"
                required
                value={inviteCode}
                onChange={(e) => setInviteCode(e.target.value)}
                className="mt-2 w-full uppercase text-center tracking-widest rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-3 text-lg font-bold text-[#E8EEF8] outline-none focus:border-[#C8A84B]"
                placeholder="GDC-MARDAN-01"
              />
            </div>
            <button
              type="submit"
              disabled={loading}
              className="w-full rounded-lg bg-gradient-to-r from-[#1E5FD4] to-[#2872F0] py-3 text-sm font-bold text-white transition-all hover:from-[#2872F0] focus:outline-none disabled:opacity-50"
            >
              {loading ? "Joining..." : "Join & Enter"}
            </button>
            
            <div className="relative flex items-center py-2">
              <div className="flex-grow border-t border-[#1E3050]"></div>
              <span className="flex-shrink-0 mx-4 text-slate-500 text-xs">OR</span>
              <div className="flex-grow border-t border-[#1E3050]"></div>
            </div>

            {!showScanner ? (
              <button
                type="button"
                onClick={() => setShowScanner(true)}
                className="w-full rounded-lg border border-[#C8A84B] py-3 text-sm font-bold text-[#C8A84B] transition-all hover:bg-[#C8A84B]/10 focus:outline-none"
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
                  className="mt-4 text-sm text-slate-400 hover:text-white"
                >
                  Cancel Scan
                </button>
              </div>
            )}
          </form>
        )}

        {errorMsg && (
          <div className="rounded-lg border border-red-500/30 bg-red-500/10 p-3 text-center text-xs text-red-300">
            ⚠️ {errorMsg}
          </div>
        )}
      </div>
    </div>
  );
}
