"""
services/settings_service.py — per-institution library policy.

Reads and writes /institutions/{collegeId}/settings/library_settings, the
single document that decides fines and due dates for every platform.

Before this existed each platform kept its own copy and they disagreed:
  Android   SharedPreferences only, fine rate defaulting to 1.0, never synced
  Desktop   wrote only `fineRatePerDay`, defaulting to 5.0
  Web       a local useState with a hardcoded 5.0 and no save at all
The visible symptom was that the same overdue book produced a different fine
depending on which device processed the return.

Keep the field names and the fine arithmetic in step with:
  shared/src/commonMain/kotlin/com/college/library/data/model/LibrarySettings.kt
  web-app/src/lib/settings.ts
"""
import time
from dataclasses import dataclass, asdict, fields
from typing import Optional

import config

SETTINGS_COLLECTION = "settings"
SETTINGS_DOCUMENT = "library_settings"

DEFAULT_FINE_RATE = 5.0
DEFAULT_BORROW_DAYS = 14
DEFAULT_MAX_BOOKS = 3
DEFAULT_RESERVATION_HOLD_DAYS = 3


@dataclass
class LibrarySettings:
    fineRatePerDay: float = DEFAULT_FINE_RATE
    borrowDurationDays: int = DEFAULT_BORROW_DAYS
    maxBooksPerMember: int = DEFAULT_MAX_BOOKS
    reservationHoldDays: int = DEFAULT_RESERVATION_HOLD_DAYS
    fineGraceDays: int = 0
    maxFinePerLoan: float = 0.0
    currencySymbol: str = "Rs"
    lastUpdated: int = 0
    updatedBy: str = ""
    updatedByPlatform: str = ""

    # ── Fine arithmetic ──────────────────────────────────────────────────────
    def fine_for(self, days_overdue: int) -> float:
        """Fine owed for a loan `days_overdue` days late.

        Mirrors LibrarySettings.fineFor() in the Kotlin module and
        computeFine() in the web app, so a return processed here and the same
        return processed on another platform produce the same number.
        """
        chargeable = days_overdue - self.fineGraceDays
        if chargeable <= 0:
            return 0.0
        fine = chargeable * self.fineRatePerDay
        if self.maxFinePerLoan > 0:
            fine = min(fine, self.maxFinePerLoan)
        return round(fine, 2)

    def sanitised(self) -> "LibrarySettings":
        """Clamp values that would break circulation if mis-entered.

        Mirrors LibrarySettings.sanitised() in the Kotlin module exactly. Note
        the `None`-checks rather than `or` fallbacks: `0 or DEFAULT` is DEFAULT
        in Python, so `int(self.borrowDurationDays or 14)` would turn a stored 0
        into 14 while Kotlin's coerceIn(1, 365) turns it into 1 — reintroducing
        the cross-platform disagreement this document exists to prevent.
        """
        def clamp_int(v, lo, hi, default):
            try:
                n = int(v) if v is not None else default
            except (TypeError, ValueError):
                n = default
            return max(lo, min(hi, n))

        def clamp_float(v, lo, default):
            try:
                f = float(v) if v is not None else default
            except (TypeError, ValueError):
                f = default
            return max(lo, f)

        symbol = (self.currencySymbol or "").strip() or "Rs"

        return LibrarySettings(
            fineRatePerDay=clamp_float(self.fineRatePerDay, 0.0, DEFAULT_FINE_RATE),
            borrowDurationDays=clamp_int(self.borrowDurationDays, 1, 365, DEFAULT_BORROW_DAYS),
            maxBooksPerMember=clamp_int(self.maxBooksPerMember, 1, 100, DEFAULT_MAX_BOOKS),
            reservationHoldDays=clamp_int(self.reservationHoldDays, 0, 90, DEFAULT_RESERVATION_HOLD_DAYS),
            fineGraceDays=clamp_int(self.fineGraceDays, 0, 90, 0),
            maxFinePerLoan=clamp_float(self.maxFinePerLoan, 0.0, 0.0),
            currencySymbol=symbol,
            lastUpdated=self.lastUpdated,
            updatedBy=self.updatedBy,
            updatedByPlatform=self.updatedByPlatform,
        )

    @staticmethod
    def from_dict(d: dict) -> "LibrarySettings":
        known = {f.name for f in fields(LibrarySettings)}
        return LibrarySettings(**{k: v for k, v in (d or {}).items() if k in known}).sanitised()

    def to_dict(self) -> dict:
        return asdict(self)


class SettingsService:
    """Loads, caches and publishes the institution's library policy."""

    def __init__(self, firebase_service):
        self.fb = firebase_service
        self._cache: Optional[LibrarySettings] = None

    def _doc_ref(self):
        if self.fb.mock_mode or not self.fb.db or not self.fb.college_id:
            return None
        return (
            self.fb.db.collection("institutions")
            .document(self.fb.college_id)
            .collection(SETTINGS_COLLECTION)
            .document(SETTINGS_DOCUMENT)
        )

    def get(self, refresh: bool = False) -> LibrarySettings:
        """Current policy. Falls back to the cache, then to defaults, so an
        offline desktop still charges a sane fine rather than zero."""
        if self._cache is not None and not refresh:
            return self._cache

        ref = self._doc_ref()
        if ref is None:
            self._cache = LibrarySettings(
                fineRatePerDay=getattr(config, "DEFAULT_FINE_RATE", DEFAULT_FINE_RATE)
            )
            return self._cache

        try:
            snap = ref.get()
            if snap.exists:
                self._cache = LibrarySettings.from_dict(snap.to_dict())
            else:
                # No policy stored yet — seed from the local .env default so the
                # institution starts from the rate the librarian configured.
                self._cache = LibrarySettings(
                    fineRatePerDay=getattr(config, "DEFAULT_FINE_RATE", DEFAULT_FINE_RATE)
                )
        except Exception as e:
            print(f"Could not read library settings: {e}")
            if self._cache is None:
                self._cache = LibrarySettings(
                    fineRatePerDay=getattr(config, "DEFAULT_FINE_RATE", DEFAULT_FINE_RATE)
                )
        return self._cache

    def save(self, settings: LibrarySettings, user_email: str = "") -> bool:
        """Publish policy so every platform charges the same fine."""
        clean = settings.sanitised()
        clean.lastUpdated = int(time.time() * 1000)
        clean.updatedBy = user_email
        clean.updatedByPlatform = "desktop"

        # Cache first: the change must take effect locally even when offline.
        self._cache = clean

        ref = self._doc_ref()
        if ref is None:
            return False
        try:
            ref.set(clean.to_dict(), merge=True)
            return True
        except Exception as e:
            print(f"Could not publish library settings: {e}")
            return False

    def listen(self, callback):
        """Watch for policy changes made on another device."""
        ref = self._doc_ref()
        if ref is None:
            return None

        def on_snapshot(doc_snapshot, changes, read_time):
            for snap in doc_snapshot:
                if snap.exists:
                    self._cache = LibrarySettings.from_dict(snap.to_dict())
                    try:
                        callback(self._cache)
                    except Exception:
                        pass

        try:
            return ref.on_snapshot(on_snapshot)
        except Exception:
            return None
