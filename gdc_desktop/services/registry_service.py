"""
services/registry_service.py — Directorate registry publisher.

Publishes this college's aggregate snapshot to /directorate_index/{collegeId},
the single registry the directorate portal reads.

Why the clients publish this rather than the server: tenant data lives under
/institutions/{collegeId}/... and is readable only by that college's own staff,
Firestore has no cross-tenant aggregate query, and Cloud Functions are not
available on the Firebase Spark plan. So each college's own app computes its
rollup and writes one small document that directorate staff may read.

The document holds counts and a heartbeat only — no book records, no patron
records — which is what makes it safe to expose network-wide.

This replaces three registries that had grown up in parallel and disagreed
(/colleges, /directorate_index and profile fields on /institutions). The other
two are still written as legacy mirrors so older builds keep working, but
/directorate_index is authoritative.

Keep the field names here in step with:
  web-app/src/lib/directorate.ts
  shared/src/commonMain/kotlin/com/college/library/data/DirectorateRegistry.kt
"""
import time
from typing import Optional

import config

# Bump when the document shape changes so readers can tell publishers apart.
REGISTRY_SCHEMA_VERSION = 2

REGISTRY_COLLECTION = "directorate_index"
LEGACY_COLLECTION = "colleges"


class RegistryService:
    """Computes and publishes directorate snapshots for the active institution."""

    def __init__(self, db_helper, firebase_service):
        self.db = db_helper
        self.fb = firebase_service

    # ── Snapshot computation ──────────────────────────────────────────────────

    def build_snapshot(self) -> dict:
        """Compute aggregate counts from the local SQLite mirror.

        Reads locally rather than from Firestore so publishing costs no document
        reads and works from the offline cache — the numbers are as current as
        the last completed sync, which is exactly what the heartbeat reports.
        """
        books = self.db.get_books()
        members = self.db.get_members()
        issues = self.db.get_issues()
        try:
            reservations = self.db.get_reservations()
        except Exception:
            reservations = []

        today = time.strftime("%Y-%m-%d")

        # An open loan is status == "Issued"; matches the Kotlin and TypeScript
        # predicates so all three platforms report the same figure.
        active = [i for i in issues if i.status == "Issued" and not i.deleted]
        overdue = [i for i in active if i.dueDate and i.dueDate < today]

        printed = [b for b in books if not b.deleted and not b.isDigital]
        digital = [b for b in books if not b.deleted and b.isDigital]

        return {
            "booksCount": len(printed),
            "ebooksCount": len(digital),
            "membersCount": len([m for m in members if not m.deleted]),
            "activeLoans": len(active),
            "overdueCount": len(overdue),
            "reservationsCount": len(
                [r for r in reservations if not r.deleted and r.status == "Pending"]
            ),
            "finesOutstanding": round(sum(i.fine or 0.0 for i in active), 2),
        }

    # ── Publishing ────────────────────────────────────────────────────────────

    def publish(self, college_name: Optional[str] = None) -> bool:
        """Publish the current snapshot. Returns True on success.

        Never raises: a college that cannot publish its rollup must still be
        able to run its library.
        """
        if self.fb.mock_mode or not self.fb.db:
            return False

        college_id = self.fb.college_id
        if not college_id:
            # No institution resolved yet (user not logged in). Publishing now
            # would attach this device's counts to the wrong college.
            return False

        try:
            payload = self.build_snapshot()
            payload.update(
                {
                    "institutionId": college_id,
                    "name": college_name or getattr(config, "COLLEGE_NAME", "") or college_id,
                    "location": getattr(config, "COLLEGE_LOCATION", "") or "",
                    "lastSyncAt": int(time.time() * 1000),
                    "lastSyncPlatform": "desktop",
                    "schemaVersion": REGISTRY_SCHEMA_VERSION,
                }
            )

            self.fb.db.collection(REGISTRY_COLLECTION).document(college_id).set(
                payload, merge=True
            )

            # Legacy mirror for builds that still read /colleges.
            self.fb.db.collection(LEGACY_COLLECTION).document(college_id).set(
                {
                    "collegeId": college_id,
                    "collegeName": payload["name"],
                    "name": payload["name"],
                    "location": payload["location"],
                    "booksCount": payload["booksCount"],
                    "membersCount": payload["membersCount"],
                    "circulationCount": payload["activeLoans"],
                    "lastSyncAt": payload["lastSyncAt"],
                },
                merge=True,
            )
            return True
        except Exception as e:
            print(f"Directorate snapshot not published: {e}")
            return False

    def register_institution(self, college_id: str, college_name: str,
                             owner_uid: str = "") -> bool:
        """Create the registry entry for a newly created institution.

        Called from onboarding so a college shows up in the directorate portal
        straight away, with zeroed counts until its first sync publishes real
        ones. Without this a college created on the desktop was invisible to the
        directorate until someone happened to open the college profile screen.
        """
        if self.fb.mock_mode or not self.fb.db or not college_id:
            return False
        now = int(time.time() * 1000)
        try:
            self.fb.db.collection(REGISTRY_COLLECTION).document(college_id).set(
                {
                    "institutionId": college_id,
                    "name": college_name,
                    "booksCount": 0,
                    "ebooksCount": 0,
                    "membersCount": 0,
                    "activeLoans": 0,
                    "overdueCount": 0,
                    "reservationsCount": 0,
                    "finesOutstanding": 0.0,
                    "lastSyncAt": now,
                    "lastSyncPlatform": "desktop",
                    "schemaVersion": REGISTRY_SCHEMA_VERSION,
                },
                merge=True,
            )
            self.fb.db.collection(LEGACY_COLLECTION).document(college_id).set(
                {
                    "collegeId": college_id,
                    "collegeName": college_name,
                    "name": college_name,
                    "ownerUid": owner_uid,
                    "directorUid": owner_uid,
                    "createdAt": now,
                },
                merge=True,
            )
            return True
        except Exception as e:
            print(f"Could not register institution in directorate registry: {e}")
            return False
