"""
services/operations_service.py — Firestore-backed "operations" collections.

Covers the library-management features outside the core catalogue and
circulation loop: the gate log, acquisitions, book transfers, serials,
inter-library loans, the purchase wishlist, reading-room seats, lost property
and events.

Why this exists: on the desktop these features were stored in local SQLite
ONLY. A purchase order raised here, a serial subscription recorded here, an ILL
request logged here — none of it ever reached Firestore, so the Android and web
apps could not see any of it. The web app meanwhile wrote the same features
straight to Firestore under camelCase field names. The two halves of the system
were storing the same concepts in different places under different names, so
"three apps sharing data" was not true for any of these features.

Everything now lives at /institutions/{collegeId}/{collection}/{syncId} with the
standard sync envelope, and field names are camelCase to match the web app and
the Kotlin models.

Keep in step with:
  shared/src/commonMain/kotlin/com/college/library/data/model/Operations.kt
  web-app/src/lib/operations.ts
"""
import time
import uuid
from typing import Any, Callable, Dict, List, Optional

# ─── Collections ─────────────────────────────────────────────────────────────

VISITOR_LOG = "visitor_log"
PURCHASE_ORDERS = "purchase_orders"
BOOK_TRANSFERS = "book_transfers"
SERIALS = "serials"
ILL_REQUESTS = "ill_requests"
WISHLIST = "wishlist"
READING_ROOM = "reading_room"
LOST_FOUND = "lost_found"
EVENTS = "library_events"

ALL_COLLECTIONS = [
    VISITOR_LOG, PURCHASE_ORDERS, BOOK_TRANSFERS, SERIALS,
    ILL_REQUESTS, WISHLIST, READING_ROOM, LOST_FOUND, EVENTS,
]

# ─── Status vocabularies, matching the Kotlin companion objects ──────────────

PO_STATUSES = ["Pending", "Approved", "Ordered", "Shipped", "Received", "Cancelled"]
TRANSFER_STATUSES = ["requested", "approved", "dispatched", "received", "rejected"]
SERIAL_STATUSES = ["Active", "Lapsed", "Cancelled"]
SERIAL_FREQUENCIES = ["Daily", "Weekly", "Fortnightly", "Monthly", "Quarterly", "Annual"]
ILL_STATUSES = ["Pending", "Approved", "Dispatched", "Fulfilled", "Returned", "Rejected"]
WISHLIST_STATUSES = ["Requested", "UnderReview", "Approved", "Ordered", "Declined"]
LOST_FOUND_STATUSES = ["Lost", "Found", "Claimed", "Disposed"]
EVENT_STATUSES = ["Planned", "Ongoing", "Completed", "Cancelled"]


def now_ms() -> int:
    return int(time.time() * 1000)


def today_stamp() -> str:
    return time.strftime("%Y-%m-%d")


class OperationsService:
    """CRUD over the operations collections for the active institution.

    Every method degrades to a safe no-op when Firebase is unavailable or no
    institution has been resolved, so the desktop app keeps running offline
    rather than raising into a Qt slot.
    """

    def __init__(self, firebase_service):
        self.fb = firebase_service

    # ── Internals ────────────────────────────────────────────────────────────

    def _col(self, name: str):
        if self.fb.mock_mode or not self.fb.db or not self.fb.college_id:
            return None
        return (
            self.fb.db.collection("institutions")
            .document(self.fb.college_id)
            .collection(name)
        )

    def _envelope(self, sync_id: str) -> Dict[str, Any]:
        return {
            "syncId": sync_id,
            "collegeId": self.fb.college_id,
            "lastUpdated": now_ms(),
            "deleted": False,
            "syncStatus": "synced",
        }

    # ── Generic operations ───────────────────────────────────────────────────

    def list(self, collection: str, include_deleted: bool = False) -> List[Dict[str, Any]]:
        """All records in a collection. Returns [] rather than raising."""
        col = self._col(collection)
        if col is None:
            return []
        try:
            rows = []
            for doc in col.stream():
                data = doc.to_dict() or {}
                if not include_deleted and data.get("deleted"):
                    continue
                data["syncId"] = doc.id
                rows.append(data)
            return rows
        except Exception as e:
            print(f"Could not read {collection}: {e}")
            return []

    def save(self, collection: str, fields: Dict[str, Any],
             sync_id: Optional[str] = None) -> Optional[str]:
        """Create or replace a record. Returns its syncId, or None on failure."""
        col = self._col(collection)
        if col is None:
            return None
        doc_id = sync_id or fields.get("syncId") or str(uuid.uuid4())
        try:
            payload = dict(fields)
            payload.update(self._envelope(doc_id))
            col.document(doc_id).set(payload, merge=True)
            return doc_id
        except Exception as e:
            print(f"Could not save to {collection}: {e}")
            return None

    def update(self, collection: str, sync_id: str, fields: Dict[str, Any]) -> bool:
        """Patch specific fields, refreshing the sync timestamp."""
        col = self._col(collection)
        if col is None or not sync_id:
            return False
        try:
            payload = dict(fields)
            payload["lastUpdated"] = now_ms()
            payload["syncStatus"] = "synced"
            col.document(sync_id).set(payload, merge=True)
            return True
        except Exception as e:
            print(f"Could not update {collection}/{sync_id}: {e}")
            return False

    def soft_delete(self, collection: str, sync_id: str) -> bool:
        """Mark a record deleted.

        Never a hard delete: the Android and web sync engines propagate the
        `deleted` flag and rely on the document continuing to exist, so removing
        it outright would let an offline peer resurrect the record.
        """
        return self.update(collection, sync_id, {"deleted": True})

    def listen(self, collection: str, callback: Callable[[List[Dict[str, Any]]], None]):
        """Real-time listener. Returns the watch handle, or None."""
        col = self._col(collection)
        if col is None:
            return None

        def on_snapshot(col_snapshot, changes, read_time):
            rows = []
            for doc in col_snapshot:
                data = doc.to_dict() or {}
                if data.get("deleted"):
                    continue
                data["syncId"] = doc.id
                rows.append(data)
            try:
                callback(rows)
            except Exception:
                pass

        try:
            return col.on_snapshot(on_snapshot)
        except Exception:
            return None

    # ── Gate log ─────────────────────────────────────────────────────────────

    def sign_in_visitor(self, name: str, visitor_type: str = "Student",
                        purpose: str = "", member_id: str = "") -> Optional[str]:
        return self.save(VISITOR_LOG, {
            "name": name,
            "visitorType": visitor_type,
            "purpose": purpose,
            "memberId": member_id,
            "entryTime": now_ms(),
            "exitTime": None,
            "dateStr": today_stamp(),
        })

    def sign_out_visitor(self, sync_id: str) -> bool:
        return self.update(VISITOR_LOG, sync_id, {"exitTime": now_ms()})

    def todays_visitors(self) -> List[Dict[str, Any]]:
        today = today_stamp()
        rows = [r for r in self.list(VISITOR_LOG) if r.get("dateStr") == today]
        return sorted(rows, key=lambda r: r.get("entryTime") or 0, reverse=True)

    # ── Acquisitions ─────────────────────────────────────────────────────────

    def raise_purchase_order(self, vendor: str, book_title: str, quantity: int,
                             unit_price: float, isbn: str = "",
                             notes: str = "") -> Optional[str]:
        return self.save(PURCHASE_ORDERS, {
            "vendorName": vendor,
            "bookTitle": book_title,
            "isbn": isbn,
            "quantity": int(quantity),
            "unitPrice": float(unit_price),
            "totalAmount": round(int(quantity) * float(unit_price), 2),
            "status": "Pending",
            "orderDate": now_ms(),
            "expectedDate": "",
            "notes": notes,
        })

    def set_purchase_order_status(self, sync_id: str, status: str) -> bool:
        return self.update(PURCHASE_ORDERS, sync_id, {"status": status})

    def purchase_orders(self) -> List[Dict[str, Any]]:
        return sorted(
            self.list(PURCHASE_ORDERS),
            key=lambda r: r.get("orderDate") or 0,
            reverse=True,
        )

    # ── Book transfers ───────────────────────────────────────────────────────

    def request_transfer(self, to_college: str, book_title: str, book_isbn: str = "",
                         quantity: int = 1, notes: str = "") -> Optional[str]:
        stamp = now_ms()
        return self.save(BOOK_TRANSFERS, {
            "fromCollege": self.fb.college_id,
            "toCollege": to_college,
            "bookTitle": book_title,
            "bookIsbn": book_isbn,
            "quantity": int(quantity),
            "status": "requested",
            "requestedAt": stamp,
            "updatedAt": stamp,
            "notes": notes,
        })

    def set_transfer_status(self, sync_id: str, status: str) -> bool:
        return self.update(BOOK_TRANSFERS, sync_id, {
            "status": status,
            "updatedAt": now_ms(),
        })

    def transfers(self) -> List[Dict[str, Any]]:
        return sorted(
            self.list(BOOK_TRANSFERS),
            key=lambda r: r.get("requestedAt") or 0,
            reverse=True,
        )

    # ── Serials ──────────────────────────────────────────────────────────────

    def add_serial(self, title: str, issn: str = "", frequency: str = "Monthly",
                   publisher: str = "", subscription_end: str = "") -> Optional[str]:
        return self.save(SERIALS, {
            "title": title,
            "issn": issn,
            "frequency": frequency,
            "publisher": publisher,
            "status": "Active",
            "subscriptionEnd": subscription_end,
            "lastIssueReceived": "",
            "issuesReceived": 0,
        })

    def receive_serial_issue(self, sync_id: str, current_count: int = 0) -> bool:
        return self.update(SERIALS, sync_id, {
            "lastIssueReceived": today_stamp(),
            "issuesReceived": int(current_count) + 1,
        })

    def serials(self) -> List[Dict[str, Any]]:
        return sorted(self.list(SERIALS), key=lambda r: (r.get("title") or "").lower())

    # ── Inter-library loans ──────────────────────────────────────────────────

    def raise_ill_request(self, book_title: str, target_institution: str,
                          author: str = "", isbn: str = "", member_id: str = "",
                          member_name: str = "", notes: str = "") -> Optional[str]:
        return self.save(ILL_REQUESTS, {
            "bookTitle": book_title,
            "author": author,
            "isbn": isbn,
            "memberId": member_id,
            "memberName": member_name,
            "targetInstitution": target_institution,
            "status": "Pending",
            "requestDate": now_ms(),
            "fulfilledDate": None,
            "notes": notes,
        })

    def set_ill_status(self, sync_id: str, status: str) -> bool:
        fields: Dict[str, Any] = {"status": status}
        if status == "Fulfilled":
            fields["fulfilledDate"] = now_ms()
        return self.update(ILL_REQUESTS, sync_id, fields)

    def ill_requests(self) -> List[Dict[str, Any]]:
        return sorted(
            self.list(ILL_REQUESTS),
            key=lambda r: r.get("requestDate") or 0,
            reverse=True,
        )

    # ── Wishlist ─────────────────────────────────────────────────────────────

    def add_wishlist_item(self, title: str, author: str = "", isbn: str = "",
                          member_id: str = "", member_name: str = "",
                          reason: str = "") -> Optional[str]:
        """Record a purchase suggestion.

        If the same title is already on the list, upvote it instead of creating
        a duplicate — the vote count is what tells an acquisitions librarian
        which titles are actually in demand.
        """
        wanted = (title or "").strip().lower()
        for existing in self.list(WISHLIST):
            if (existing.get("title") or "").strip().lower() == wanted:
                self.update(WISHLIST, existing["syncId"], {
                    "votes": int(existing.get("votes") or 1) + 1,
                })
                return existing["syncId"]

        return self.save(WISHLIST, {
            "title": title,
            "author": author,
            "isbn": isbn,
            "requestedByMemberId": member_id,
            "requestedByName": member_name,
            "reason": reason,
            "status": "Requested",
            "votes": 1,
            "requestedAt": now_ms(),
        })

    def set_wishlist_status(self, sync_id: str, status: str) -> bool:
        return self.update(WISHLIST, sync_id, {"status": status})

    def wishlist(self) -> List[Dict[str, Any]]:
        return sorted(
            self.list(WISHLIST),
            key=lambda r: (int(r.get("votes") or 0), r.get("requestedAt") or 0),
            reverse=True,
        )

    # ── Enterprise: reading room, lost property, events ──────────────────────

    def assign_seat(self, seat_number: int, occupant_name: str,
                    member_id: str = "") -> Optional[str]:
        existing = next(
            (s for s in self.list(READING_ROOM)
             if int(s.get("seatNumber") or 0) == int(seat_number)),
            None,
        )
        fields = {
            "seatNumber": int(seat_number),
            "occupantName": occupant_name,
            "occupantMemberId": member_id,
            "occupiedAt": now_ms(),
        }
        if existing:
            return existing["syncId"] if self.update(READING_ROOM, existing["syncId"], fields) else None
        return self.save(READING_ROOM, fields)

    def free_seat(self, seat_number: int) -> bool:
        existing = next(
            (s for s in self.list(READING_ROOM)
             if int(s.get("seatNumber") or 0) == int(seat_number)),
            None,
        )
        if not existing:
            return False
        return self.update(READING_ROOM, existing["syncId"], {
            "occupantName": "",
            "occupantMemberId": "",
            "occupiedAt": None,
        })

    def seats(self) -> List[Dict[str, Any]]:
        return sorted(self.list(READING_ROOM), key=lambda r: int(r.get("seatNumber") or 0))

    def log_lost_found(self, item_name: str, location: str = "",
                       description: str = "", status: str = "Found",
                       reported_by: str = "") -> Optional[str]:
        return self.save(LOST_FOUND, {
            "itemName": item_name,
            "description": description,
            "location": location,
            "status": status,
            "reportedBy": reported_by,
            "reportedAt": now_ms(),
            "claimedBy": "",
        })

    def set_lost_found_status(self, sync_id: str, status: str,
                              claimed_by: str = "") -> bool:
        fields: Dict[str, Any] = {"status": status}
        if claimed_by:
            fields["claimedBy"] = claimed_by
        return self.update(LOST_FOUND, sync_id, fields)

    def lost_found(self) -> List[Dict[str, Any]]:
        return sorted(
            self.list(LOST_FOUND),
            key=lambda r: r.get("reportedAt") or 0,
            reverse=True,
        )

    def add_event(self, title: str, event_date: str, venue: str = "",
                  description: str = "", organiser: str = "") -> Optional[str]:
        return self.save(EVENTS, {
            "title": title,
            "eventDate": event_date,
            "venue": venue,
            "description": description,
            "organiser": organiser,
            "status": "Planned",
            "attendees": 0,
        })

    def set_event_status(self, sync_id: str, status: str) -> bool:
        return self.update(EVENTS, sync_id, {"status": status})

    def events(self) -> List[Dict[str, Any]]:
        return sorted(
            self.list(EVENTS),
            key=lambda r: r.get("eventDate") or "",
            reverse=True,
        )

    # ── One-time migration from the old local-only tables ────────────────────

    def migrate_local_tables(self, db_helper) -> Dict[str, int]:
        """Copy legacy local-SQLite rows into Firestore, once.

        The desktop app stored these features locally for its whole life, so an
        existing install has real data that would otherwise be invisible to the
        other two apps after this change. Rows are matched on a natural key so
        running the migration twice does not duplicate anything.

        Returns a per-collection count of rows migrated.
        """
        migrated = {c: 0 for c in ALL_COLLECTIONS}
        if self._col(VISITOR_LOG) is None:
            return migrated

        def already_there(collection, predicate) -> bool:
            return any(predicate(r) for r in self.list(collection))

        # Serials — keyed on title.
        try:
            for row in db_helper.get_serials():
                title = row.get("title") or ""
                if not title:
                    continue
                if already_there(SERIALS, lambda r: (r.get("title") or "") == title):
                    continue
                if self.save(SERIALS, {
                    "title": title,
                    "issn": row.get("issn") or "",
                    "frequency": row.get("frequency") or "Monthly",
                    "publisher": row.get("publisher") or "",
                    "status": row.get("status") or "Active",
                    "subscriptionEnd": "",
                    "lastIssueReceived": "",
                    "issuesReceived": 0,
                }):
                    migrated[SERIALS] += 1
        except Exception as e:
            print(f"Serials migration skipped: {e}")

        # Purchase orders — keyed on vendor + order timestamp.
        try:
            for row in db_helper.get_purchase_orders():
                vendor = row.get("vendorName") or ""
                order_date = row.get("orderDate") or 0
                if already_there(
                    PURCHASE_ORDERS,
                    lambda r: r.get("vendorName") == vendor and r.get("orderDate") == order_date,
                ):
                    continue
                if self.save(PURCHASE_ORDERS, {
                    "vendorName": vendor,
                    "bookTitle": row.get("bookTitle") or "",
                    "isbn": "",
                    "quantity": int(row.get("quantity") or 1),
                    "unitPrice": 0.0,
                    "totalAmount": float(row.get("totalAmount") or 0.0),
                    "status": row.get("status") or "Pending",
                    "orderDate": order_date,
                    "expectedDate": "",
                    "notes": "Migrated from local records.",
                }):
                    migrated[PURCHASE_ORDERS] += 1
        except Exception as e:
            print(f"Purchase order migration skipped: {e}")

        # ILL requests — keyed on title + request timestamp.
        try:
            for row in db_helper.get_ill_requests():
                title = row.get("bookTitle") or ""
                req_date = row.get("requestDate") or 0
                if already_there(
                    ILL_REQUESTS,
                    lambda r: r.get("bookTitle") == title and r.get("requestDate") == req_date,
                ):
                    continue
                if self.save(ILL_REQUESTS, {
                    "bookTitle": title,
                    "author": row.get("author") or "",
                    "isbn": "",
                    "memberId": str(row.get("memberId") or ""),
                    "memberName": "",
                    "targetInstitution": row.get("targetInstitution") or "",
                    "status": row.get("status") or "Pending",
                    "requestDate": req_date,
                    "fulfilledDate": None,
                    "notes": "Migrated from local records.",
                }):
                    migrated[ILL_REQUESTS] += 1
        except Exception as e:
            print(f"ILL migration skipped: {e}")

        # Visitor log and transfers use snake_case columns locally; map them.
        try:
            with db_helper._get_conn() as conn:
                for row in conn.execute("SELECT * FROM visitor_log").fetchall():
                    r = dict(row)
                    entry = r.get("entry_time") or 0
                    name = r.get("name") or ""
                    if already_there(
                        VISITOR_LOG,
                        lambda x: x.get("name") == name and x.get("entryTime") == entry,
                    ):
                        continue
                    if self.save(VISITOR_LOG, {
                        "name": name,
                        "visitorType": r.get("visitor_type") or "Guest",
                        "purpose": r.get("purpose") or "",
                        "memberId": "",
                        "entryTime": entry,
                        "exitTime": r.get("exit_time"),
                        "dateStr": r.get("date_str") or "",
                    }):
                        migrated[VISITOR_LOG] += 1

                for row in conn.execute("SELECT * FROM book_transfers").fetchall():
                    r = dict(row)
                    title = r.get("book_title") or ""
                    requested = r.get("requested_at") or 0
                    if already_there(
                        BOOK_TRANSFERS,
                        lambda x: x.get("bookTitle") == title and x.get("requestedAt") == requested,
                    ):
                        continue
                    if self.save(BOOK_TRANSFERS, {
                        "fromCollege": r.get("from_college") or "",
                        "toCollege": r.get("to_college") or "",
                        "bookTitle": title,
                        "bookIsbn": r.get("book_isbn") or "",
                        "quantity": 1,
                        "status": r.get("status") or "requested",
                        "requestedAt": requested,
                        "updatedAt": r.get("updated_at") or requested,
                        "notes": "Migrated from local records.",
                    }):
                        migrated[BOOK_TRANSFERS] += 1
        except Exception as e:
            print(f"Visitor/transfer migration skipped: {e}")

        return migrated
