// lib/settings.ts
//
// Per-institution library policy, stored at
// /institutions/{collegeId}/settings/library_settings.
//
// These values decide real money and real due dates, so all three platforms
// must agree on them. Before this existed each kept its own copy:
//
//   Android   SharedPreferences only, fine rate defaulting to 1.0, never synced
//   Desktop   wrote only `fineRatePerDay`, defaulting to 5.0
//   Web       a local useState with a hardcoded 5.0 and no save at all
//
// The visible symptom was that the same overdue book produced a different fine
// depending on which device processed the return.
//
// Keep the field names and the fine arithmetic in step with:
//   shared/src/commonMain/kotlin/com/college/library/data/model/LibrarySettings.kt
//   gdc_desktop/services/settings_service.py

"use client";

import { useCallback, useEffect, useState } from "react";
import { doc, onSnapshot, setDoc, DocumentData } from "firebase/firestore";
import { db } from "./firebase";
import { useAuth } from "./auth-context";
import { ROOT_COLLECTIONS, COLLECTIONS } from "./schema";

export const SETTINGS_DOCUMENT = "library_settings";

export const DEFAULT_FINE_RATE = 5.0;
export const DEFAULT_BORROW_DAYS = 14;
export const DEFAULT_MAX_BOOKS = 3;
export const DEFAULT_RESERVATION_HOLD_DAYS = 3;

export interface LibrarySettings {
  fineRatePerDay: number;
  borrowDurationDays: number;
  maxBooksPerMember: number;
  reservationHoldDays: number;
  fineGraceDays: number;
  /** Upper bound on the fine for a single loan; 0 means no cap. */
  maxFinePerLoan: number;
  currencySymbol: string;
  lastUpdated: number;
  updatedBy: string;
  updatedByPlatform: string;
}

export const DEFAULT_SETTINGS: LibrarySettings = {
  fineRatePerDay: DEFAULT_FINE_RATE,
  borrowDurationDays: DEFAULT_BORROW_DAYS,
  maxBooksPerMember: DEFAULT_MAX_BOOKS,
  reservationHoldDays: DEFAULT_RESERVATION_HOLD_DAYS,
  fineGraceDays: 0,
  maxFinePerLoan: 0,
  currencySymbol: "Rs",
  lastUpdated: 0,
  updatedBy: "",
  updatedByPlatform: "",
};

function clampInt(v: unknown, lo: number, hi: number, fallback: number): number {
  const n = typeof v === "number" && Number.isFinite(v) ? Math.trunc(v) : fallback;
  return Math.max(lo, Math.min(hi, n));
}

function clampFloat(v: unknown, lo: number, fallback: number): number {
  const n = typeof v === "number" && Number.isFinite(v) ? v : fallback;
  return Math.max(lo, n);
}

/** Clamp values that would break circulation if mis-entered. Mirrors the
 *  Kotlin `sanitised()` and the Python `sanitised()` exactly. */
export function sanitise(raw: Partial<LibrarySettings> | DocumentData): LibrarySettings {
  return {
    fineRatePerDay: clampFloat(raw.fineRatePerDay, 0, DEFAULT_FINE_RATE),
    borrowDurationDays: clampInt(raw.borrowDurationDays, 1, 365, DEFAULT_BORROW_DAYS),
    maxBooksPerMember: clampInt(raw.maxBooksPerMember, 1, 100, DEFAULT_MAX_BOOKS),
    reservationHoldDays: clampInt(raw.reservationHoldDays, 0, 90, DEFAULT_RESERVATION_HOLD_DAYS),
    fineGraceDays: clampInt(raw.fineGraceDays, 0, 90, 0),
    maxFinePerLoan: clampFloat(raw.maxFinePerLoan, 0, 0),
    currencySymbol: (typeof raw.currencySymbol === "string" ? raw.currencySymbol : "").trim() || "Rs",
    lastUpdated: typeof raw.lastUpdated === "number" ? raw.lastUpdated : 0,
    updatedBy: typeof raw.updatedBy === "string" ? raw.updatedBy : "",
    updatedByPlatform: typeof raw.updatedByPlatform === "string" ? raw.updatedByPlatform : "",
  };
}

/**
 * Fine owed for a loan `daysOverdue` days late.
 *
 * Mirrors LibrarySettings.fineFor() in Kotlin and fine_for() in Python, so the
 * same return produces the same number on every platform.
 */
export function computeFine(settings: LibrarySettings, daysOverdue: number): number {
  const chargeable = daysOverdue - settings.fineGraceDays;
  if (chargeable <= 0) return 0;
  const fine = chargeable * settings.fineRatePerDay;
  const capped = settings.maxFinePerLoan > 0 ? Math.min(fine, settings.maxFinePerLoan) : fine;
  return Math.round(capped * 100) / 100;
}

/**
 * Live library policy for the active institution, plus a save function.
 *
 * Saving requires an admin-level role under the security rules, so a
 * librarian's save is expected to be rejected; `save` reports that rather than
 * failing silently.
 */
export function useLibrarySettings() {
  const { profile } = useAuth();
  const [settings, setSettings] = useState<LibrarySettings>(DEFAULT_SETTINGS);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const institutionId = profile?.institutionId;

  useEffect(() => {
    if (!institutionId) {
      setSettings(DEFAULT_SETTINGS);
      setLoading(false);
      return;
    }
    setLoading(true);
    const ref = doc(
      db,
      ROOT_COLLECTIONS.institutions,
      institutionId,
      COLLECTIONS.settings,
      SETTINGS_DOCUMENT,
    );
    const unsub = onSnapshot(
      ref,
      (snap) => {
        // A missing document is not an error: the institution simply has not
        // set a policy yet, so the shared defaults apply.
        setSettings(snap.exists() ? sanitise(snap.data()) : DEFAULT_SETTINGS);
        setLoading(false);
        setError(null);
      },
      (err) => {
        console.error("useLibrarySettings failed:", err);
        setError(err.message);
        setLoading(false);
      },
    );
    return () => unsub();
  }, [institutionId]);

  const save = useCallback(
    async (next: Partial<LibrarySettings>): Promise<{ ok: boolean; message: string }> => {
      if (!institutionId) {
        return { ok: false, message: "No active institution." };
      }
      const merged = sanitise({ ...settings, ...next });
      try {
        await setDoc(
          doc(
            db,
            ROOT_COLLECTIONS.institutions,
            institutionId,
            COLLECTIONS.settings,
            SETTINGS_DOCUMENT,
          ),
          {
            ...merged,
            lastUpdated: Date.now(),
            updatedBy: profile?.email ?? "",
            updatedByPlatform: "web",
          },
          { merge: true },
        );
        return { ok: true, message: "Saved. Applied on all devices." };
      } catch (err) {
        return {
          ok: false,
          message:
            (err as Error).message.includes("permission")
              ? "Your role cannot change library policy. Ask an administrator."
              : `Could not save: ${(err as Error).message}`,
        };
      }
    },
    [institutionId, settings, profile?.email],
  );

  return { settings, loading, error, save };
}
