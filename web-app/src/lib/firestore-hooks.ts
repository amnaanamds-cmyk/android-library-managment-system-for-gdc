"use client";

import { useState, useEffect, useCallback, useRef } from "react";
import {
  collection,
  query,
  onSnapshot,
  setDoc,
  updateDoc,
  doc,
  DocumentData,
  where,
  getDocs,
} from "firebase/firestore";
import { db } from "./firebase";
import { useAuth } from "./auth-context";
import { newSyncId, ROOT_COLLECTIONS } from "./schema";

/** Connection state of a live collection listener, surfaced in the UI. */
export type TenantSyncState = "idle" | "connecting" | "live" | "error";

/**
 * Stream one collection under the active tenant.
 *
 * Records are written with the same envelope the Android and desktop sync
 * engines expect (syncId as the document id, collegeId, lastUpdated, deleted,
 * syncStatus), so a record created here round-trips through the other two
 * platforms unchanged.
 */
export function useTenantCollection(collectionName: string) {
  const { profile } = useAuth();
  const [data, setData] = useState<DocumentData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [syncState, setSyncState] = useState<TenantSyncState>("idle");
  /** Timestamp of the last snapshot actually delivered by the server. */
  const [lastSyncAt, setLastSyncAt] = useState<number | null>(null);

  const institutionId = profile?.institutionId;

  useEffect(() => {
    if (!institutionId) {
      setData([]);
      setLoading(false);
      setSyncState("idle");
      return;
    }

    setLoading(true);
    setError(null);
    setSyncState("connecting");

    const colRef = collection(db, ROOT_COLLECTIONS.institutions, institutionId, collectionName);

    const unsubscribe = onSnapshot(
      query(colRef),
      { includeMetadataChanges: true },
      (snapshot) => {
        const items: DocumentData[] = [];
        snapshot.forEach((d) => items.push({ id: d.id, ...d.data() }));
        setData(items);
        setLoading(false);
        setError(null);
        // fromCache means we are serving the local cache, not the server —
        // that is the honest "offline" signal for the status indicator.
        if (snapshot.metadata.fromCache) {
          setSyncState("connecting");
        } else {
          setSyncState("live");
          setLastSyncAt(Date.now());
        }
      },
      (err) => {
        // Surface the failure instead of leaving a permanently empty table.
        // A permission-denied here almost always means the signed-in user's
        // profile institutionId does not match the tenant being read.
        console.error(`useTenantCollection(${collectionName}) failed:`, err);
        setError(err.message);
        setLoading(false);
        setSyncState("error");
      },
    );

    return () => unsubscribe();
  }, [institutionId, collectionName]);

  const addRecord = useCallback(
    async (record: Record<string, unknown>) => {
      if (!institutionId) throw new Error("No active institution");
      const syncId = (record.syncId as string) || newSyncId();
      const docRef = doc(db, ROOT_COLLECTIONS.institutions, institutionId, collectionName, syncId);
      return setDoc(docRef, {
        ...record,
        syncId,
        collegeId: institutionId,
        lastUpdated: Date.now(),
        deleted: false,
        syncStatus: "synced",
      });
    },
    [institutionId, collectionName],
  );

  const updateRecord = useCallback(
    async (id: string, record: Record<string, unknown>) => {
      if (!institutionId) throw new Error("No active institution");
      const docRef = doc(db, ROOT_COLLECTIONS.institutions, institutionId, collectionName, id);
      return updateDoc(docRef, {
        ...record,
        lastUpdated: Date.now(),
        // Mark the record as freshly written so the other platforms' last-write-wins
        // resolvers treat it as authoritative rather than a stale echo.
        syncStatus: "synced",
      });
    },
    [institutionId, collectionName],
  );

  const deleteRecord = useCallback(
    async (id: string) => {
      if (!institutionId) throw new Error("No active institution");
      const docRef = doc(db, ROOT_COLLECTIONS.institutions, institutionId, collectionName, id);
      // Soft delete: the KMP and Python sync engines propagate `deleted` and
      // rely on the document continuing to exist. A hard delete would
      // resurrect the record from any offline peer that still holds it.
      return updateDoc(docRef, {
        deleted: true,
        lastUpdated: Date.now(),
        syncStatus: "synced",
      });
    },
    [institutionId, collectionName],
  );

  return {
    data: data.filter((item) => !item.deleted),
    /** Including soft-deleted records, for audit and reconciliation views. */
    rawData: data,
    loading,
    error,
    syncState,
    lastSyncAt,
    addRecord,
    updateRecord,
    deleteRecord,
  };
}

/**
 * Aggregate sync health for the whole session, driven by the browser's network
 * state plus whether Firestore is currently serving from cache.
 *
 * The dashboard header previously hardcoded a green "Real-time Sync Active"
 * dot, so a user whose writes were all being rejected still saw a healthy
 * indicator. This reports the real state.
 */
export function useSyncHealth() {
  const { profile } = useAuth();
  const [online, setOnline] = useState(true);
  const [state, setState] = useState<TenantSyncState>("idle");
  const [lastSyncAt, setLastSyncAt] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    setOnline(typeof navigator === "undefined" ? true : navigator.onLine);
    const goOnline = () => setOnline(true);
    const goOffline = () => setOnline(false);
    window.addEventListener("online", goOnline);
    window.addEventListener("offline", goOffline);
    return () => {
      mounted.current = false;
      window.removeEventListener("online", goOnline);
      window.removeEventListener("offline", goOffline);
    };
  }, []);

  // Probe the tenant with a cheap listener on the institution document itself
  // rather than a whole collection, so the indicator costs one document read.
  useEffect(() => {
    if (!profile?.institutionId) {
      setState("idle");
      return;
    }
    setState("connecting");
    const unsub = onSnapshot(
      doc(db, ROOT_COLLECTIONS.institutions, profile.institutionId),
      { includeMetadataChanges: true },
      (snap) => {
        if (!mounted.current) return;
        if (snap.metadata.fromCache) {
          setState("connecting");
        } else {
          setState("live");
          setLastSyncAt(Date.now());
          setError(null);
        }
      },
      (err) => {
        if (!mounted.current) return;
        setState("error");
        setError(err.message);
      },
    );
    return () => unsub();
  }, [profile?.institutionId]);

  const effective: TenantSyncState = !online ? "connecting" : state;

  return { state: effective, online, lastSyncAt, error };
}

/**
 * Colleges visible to the signed-in director.
 *
 * @deprecated Use `useDirectorateNetwork` from `lib/directorate` instead. This
 * shim reads the legacy /colleges registry and is kept only so that any page
 * still importing it keeps compiling. It resolves against the same normalised
 * field names as the new registry.
 */
export function useDirectorColleges() {
  const { user, profile } = useAuth();
  const [colleges, setColleges] = useState<DocumentData[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!user) {
      setColleges([]);
      setLoading(false);
      return;
    }

    const colRef = collection(db, ROOT_COLLECTIONS.colleges);
    // A plain director sees only colleges explicitly assigned to them; anyone
    // higher sees the whole registry.
    const q =
      profile?.role === "director"
        ? query(colRef, where("directorUid", "==", user.uid))
        : query(colRef);

    const unsubscribe = onSnapshot(
      q,
      (snapshot) => {
        setColleges(snapshot.docs.map((d) => ({ id: d.id, ...d.data() })));
        setLoading(false);
      },
      (err) => {
        console.error("useDirectorColleges failed:", err);
        setLoading(false);
      },
    );

    return () => unsubscribe();
  }, [user, profile]);

  return { colleges, loading };
}

/** One-shot count of a tenant sub-collection, used when building snapshots. */
export async function countTenantCollection(
  institutionId: string,
  collectionName: string,
): Promise<number> {
  const snap = await getDocs(
    collection(db, ROOT_COLLECTIONS.institutions, institutionId, collectionName),
  );
  return snap.docs.filter((d) => !d.data().deleted).length;
}
