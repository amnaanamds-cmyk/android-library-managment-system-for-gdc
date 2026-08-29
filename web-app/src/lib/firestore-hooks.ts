"use client";

import { useState, useEffect } from "react";
import { 
  collection, 
  query, 
  onSnapshot, 
  addDoc, 
  setDoc,
  updateDoc, 
  deleteDoc, 
  doc, 
  DocumentData,
  where,
  getDocs
} from "firebase/firestore";
import { db } from "./firebase";
import { useAuth } from "./auth-context";

// Generic custom hook to stream collection under the active tenant (institution)
export function useTenantCollection(collectionName: string) {
  const { profile } = useAuth();
  const [data, setData] = useState<DocumentData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!profile?.institutionId) {
      setData([]);
      setLoading(false);
      return;
    }

    setLoading(true);
    const colRef = collection(db, "institutions", profile.institutionId, collectionName);
    const q = query(colRef);

    const unsubscribe = onSnapshot(q, (snapshot) => {
      const items: DocumentData[] = [];
      snapshot.forEach((doc) => {
        items.push({ id: doc.id, ...doc.data() });
      });
      setData(items);
      setLoading(false);
    }, (err) => {
      console.error(`Error in useTenantCollection for ${collectionName}:`, err);
      setError(err.message);
      setLoading(false);
    });

    return () => unsubscribe();
  }, [profile?.institutionId, collectionName]);

  const addRecord = async (record: any) => {
    if (!profile?.institutionId) throw new Error("No active institution");
    const syncId = record.syncId || (Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15));
    const docRef = doc(db, "institutions", profile.institutionId, collectionName, syncId);
    return await setDoc(docRef, {
      ...record,
      syncId: syncId,
      collegeId: profile.institutionId,
      lastUpdated: Date.now(),
      deleted: false,
      syncStatus: "synced"
    });
  };

  const updateRecord = async (id: string, record: any) => {
    if (!profile?.institutionId) throw new Error("No active institution");
    const docRef = doc(db, "institutions", profile.institutionId, collectionName, id);
    return await updateDoc(docRef, {
      ...record,
      lastUpdated: Date.now(),
    });
  };

  const deleteRecord = async (id: string) => {
    if (!profile?.institutionId) throw new Error("No active institution");
    const docRef = doc(db, "institutions", profile.institutionId, collectionName, id);
    // Soft delete for full compatibility with KMP / Python sync engines
    return await updateDoc(docRef, {
      deleted: true,
      lastUpdated: Date.now(),
    });
  };

  return { data: data.filter((item) => !item.deleted), loading, error, addRecord, updateRecord, deleteRecord };
}

// Hook to observe all colleges assigned to a director
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

    const colRef = collection(db, "colleges");
    // If directorate_admin or owner, show all. If director, show only assigned.
    let q = query(colRef);
    if (profile?.role === "director") {
      q = query(colRef, where("directorUid", "==", user.uid));
    }

    const unsubscribe = onSnapshot(q, (snapshot) => {
      const items = snapshot.docs.map(doc => ({ id: doc.id, ...doc.data() }));
      setColleges(items);
      setLoading(false);
    });

    return () => unsubscribe();
  }, [user, profile]);

  return { colleges, loading };
}
