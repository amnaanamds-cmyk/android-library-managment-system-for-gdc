"""
services/sync_service.py — Offline-first Two-Way Synchronization Engine.
Periodically pushes offline changes to Firestore and pulls remote changes.
Handles conflict resolution (latest serverTimestamp / lastUpdated wins).
"""
import time
from PyQt6.QtCore import QThread, pyqtSignal
from firebase_admin import firestore

from services.database_helper import DatabaseHelper
from services.firebase_service import FirebaseService
from services.registry_service import RegistryService
from models import Book, Member, IssueRecord, Reservation

# How often to republish this college's aggregate snapshot for the directorate.
REGISTRY_PUBLISH_INTERVAL_MS = 10 * 60 * 1000  # 10 minutes

# Firestore batches accept at most 500 writes; stay under that even when an
# item also gets an audit-log write alongside it in the same batch.
PUSH_BATCH_SIZE = 200


class RealtimeSyncService(QThread):
    sync_status = pyqtSignal(str)   # emits messages: "Syncing...", "Sync Complete", "Offline"
    sync_completed = pyqtSignal()   # fires when a sync cycle completes
    data_updated = pyqtSignal()     # fires when real-time listeners receive new data

    def __init__(self, db_helper: DatabaseHelper, fb_service: FirebaseService):
        super().__init__()
        self.db = db_helper
        self.fb = fb_service
        self.running = True
        self._listeners = []
        self.registry = RegistryService(db_helper, fb_service)
        self._last_registry_publish = 0

    def force_reconnect(self):
        self.sync_status.emit("Force Syncing...")
        self._stop_listeners()
        if self.fb.test_connection():
            self._setup_listeners()
            # A manual reconnect is an explicit "make me current" request, so
            # refresh the directorate snapshot without waiting for the interval.
            self._publish_registry_snapshot(force=True)
            self.sync_status.emit("🟢 Synced")
        else:
            self.sync_status.emit("Offline (Retrying...)")

    def stop(self):
        self.running = False
        self._stop_listeners()

    def _stop_listeners(self):
        for listener in self._listeners:
            if listener:
                try:
                    listener.unsubscribe()
                except:
                    pass
        self._listeners = []

    def run(self):
        time.sleep(3)  # initial delay on startup
        while self.running:
            try:
                # 1. Test connection and setup listeners once
                if not self._listeners and not self.fb.mock_mode:
                    if not self.fb.test_connection():
                        self.sync_status.emit("Offline (Retrying...)")
                    else:
                        self.sync_status.emit("Syncing...")
                        self._setup_listeners()
                        self.sync_status.emit("🟢 Synced")

                # 2. Push pending local changes from SQLite queue to Firestore
                self._process_pending_push()

                # Feature 2: Overdue Auto-Reminder Scheduler
                self._check_overdue_reminders()

                # 3. Publish this college's aggregate snapshot for the
                #    directorate portal. The Spark plan has no Cloud Functions,
                #    so the rollup has to come from the clients themselves.
                self._publish_registry_snapshot()

            except Exception as e:
                self.sync_status.emit(f"Sync Warning: {str(e)[:25]}...")

            # Sleep in small increments to respond to stop request
            for _ in range(10):
                if not self.running:
                    break
                time.sleep(1.0)

    # Which local-DB lookup and Firestore collection each queue entity uses,
    # and whether individual saves through FirebaseService also write an
    # audit-log entry (books/members do; issued_books/reservations don't).
    _PUSH_ENTITY_CONFIG = {
        "books": ("get_book_by_sync_id", "_books_ref", True),
        "members": ("get_member_by_sync_id", "_members_ref", True),
        "issued_books": ("get_issue_by_sync_id", "_issued_ref", False),
        "reservations": ("get_reservation_by_sync_id", "_reservations_ref", False),
    }

    def _process_pending_push(self):
        """Push local changes queued in sync_queue to Firestore.

        Pushes are grouped into Firestore batch writes (up to
        PUSH_BATCH_SIZE documents each) instead of one write — plus one
        audit-log write for books/members — per item. The old one-write-per-
        item loop meant a few hundred queued books cost a few hundred
        sequential network round trips: slow on any connection, and on a slow
        one, closing the app mid-push left Firestore holding only whatever
        had completed so far while the rest silently stayed queued. Batching
        cuts that to a handful of round trips, and items only leave the local
        queue once their batch actually commits, so an interrupted push still
        resumes correctly next run instead of silently dropping the rest.
        """
        if self.fb.mock_mode or not self.fb.test_connection():
            return

        try:
            pending_items = self.db.get_pending_sync()
        except Exception:
            return

        if not pending_items:
            return

        grouped: dict[str, list[str]] = {}
        for item in pending_items:
            entity_type = item.get("entityType")
            sync_id = item.get("syncId")
            if not entity_type or not sync_id or entity_type not in self._PUSH_ENTITY_CONFIG:
                continue
            grouped.setdefault(entity_type, []).append(sync_id)

        for entity_type, sync_ids in grouped.items():
            try:
                self._batch_push_entity(entity_type, sync_ids)
            except Exception as e:
                print(f"Batch push failed for {entity_type}: {e}")

    def _batch_push_entity(self, entity_type: str, sync_ids: list):
        fetch_name, ref_name, with_audit = self._PUSH_ENTITY_CONFIG[entity_type]
        fetch_by_id = getattr(self.db, fetch_name)
        collection_ref = getattr(self.fb, ref_name)()
        if not collection_ref:
            return
        audit_ref = self.fb._audit_ref() if with_audit else None
        now_ms = int(time.time() * 1000)

        for start in range(0, len(sync_ids), PUSH_BATCH_SIZE):
            chunk = sync_ids[start:start + PUSH_BATCH_SIZE]
            batch = self.fb.db.batch()
            committed_ids = []
            for sync_id in chunk:
                try:
                    entity = fetch_by_id(sync_id)
                except Exception:
                    entity = None
                if not entity:
                    continue
                entity.lastUpdated = now_ms
                data = entity.to_dict()
                # Every synced document carries this server timestamp as the
                # authoritative clock for LWW conflict resolution (see
                # shared/.../FirestoreService.kt) — must be set on every write.
                data["lastModified"] = firestore.SERVER_TIMESTAMP
                batch.set(collection_ref.document(sync_id), data)
                if audit_ref is not None:
                    label = getattr(entity, "title", None) or getattr(entity, "name", None) or sync_id
                    batch.set(audit_ref.document(), {
                        "userEmail": "",
                        "action": f"{entity_type[:-1] if entity_type.endswith('s') else entity_type}_save",
                        "detail": f"{label} ({sync_id})",
                        "timestamp": now_ms,
                        "timestampStr": time.strftime("%Y-%m-%d %H:%M:%S"),
                    })
                committed_ids.append(sync_id)

            if not committed_ids:
                continue
            try:
                batch.commit()
            except Exception as e:
                print(f"Batch commit failed for {entity_type} ({len(committed_ids)} items): {e}")
                continue  # left queued — retried on the next sync cycle

            for sync_id in committed_ids:
                try:
                    self.db.remove_from_sync_queue(entity_type, sync_id)
                except Exception:
                    pass

    def _publish_registry_snapshot(self, force: bool = False):
        """Republish the directorate snapshot, at most once per interval."""
        if self.fb.mock_mode or not self.fb.college_id:
            return
        now_ms = int(time.time() * 1000)
        if not force and now_ms - self._last_registry_publish < REGISTRY_PUBLISH_INTERVAL_MS:
            return
        self._last_registry_publish = now_ms
        self.registry.publish()

    def _check_overdue_reminders(self):
        """Automatically check for overdue books and log reminders once a day."""
        today = time.strftime("%Y-%m-%d")
        last_check = self.db.get_last_sync_timestamp("overdue_reminder_check")

        # Only run check once every 24 hours
        if int(time.time() * 1000) - last_check < 86400000:
            return

        issues = self.db.get_issues()
        overdue_count = 0
        for i in issues:
            if i.status == "Issued" and i.dueDate and i.dueDate < today:
                # Mock: In a real app, integrate with Twilio/WhatsApp API here
                self.db.log_audit_local("SYSTEM", "auto_overdue_reminder",
                                       f"Auto-Reminder: {i.memberName} is overdue for '{i.bookTitle}'")
                overdue_count += 1

        if overdue_count > 0:
            print(f"DEBUG: Processed {overdue_count} auto-reminders.")

        self.db.set_last_sync_timestamp("overdue_reminder_check", int(time.time() * 1000))

    def _setup_listeners(self):
        self._listeners.append(self.fb.listen_books(self._on_books_changed))
        self._listeners.append(self.fb.listen_members(self._on_members_changed))
        self._listeners.append(self.fb.listen_issues(self._on_issues_changed))
        self._listeners.append(self.fb.listen_reservations(self._on_reservations_changed))

    def _emit_data_updated(self):
        now = time.time()
        if not hasattr(self, '_last_update_emit'):
            self._last_update_emit = 0
        if now - self._last_update_emit >= 1.0:
            self._last_update_emit = now
            self.data_updated.emit()

    def _on_books_changed(self, books):
        if self._process_realtime_pull("books", books, Book):
            self._emit_data_updated()

    def _on_members_changed(self, members):
        if self._process_realtime_pull("members", members, Member):
            self._emit_data_updated()

    def _on_issues_changed(self, issues):
        if self._process_realtime_pull("issued_books", issues, IssueRecord):
            self._emit_data_updated()

    def _on_reservations_changed(self, reservations):
        if self._process_realtime_pull("reservations", reservations, Reservation):
            self._emit_data_updated()

    def _process_realtime_pull(self, name: str, objects: list, model_cls) -> bool:
        """Apply remote records from a real-time Firestore snapshot to local SQLite in batch.

        Conflict resolution rule (Last-Write-Wins by lastUpdated timestamp):
          - If local is pending AND local.lastUpdated > remote.lastUpdated → local wins;
            log the conflict and skip the overwrite, keeping the local version in the queue.
          - Otherwise (remote is newer or equal) → remote wins; overwrite local, remove
            from queue, and auto-resolve any open conflict log entry for this syncId.
        
        Returns True if any local records were inserted/updated.
        """
        updated_any = False
        try:
            last_pull = self.db.get_last_sync_timestamp(name)
            max_ts = last_pull
            pending_ids = {item["syncId"] for item in self.db.get_pending_sync() if item["entityType"] == name}

            to_save = []

            for obj in objects:
                # If timestamp is old, only skip if record is ALREADY present in local DB.
                if obj.lastUpdated <= last_pull:
                    local_exists = False
                    if name == "books":
                        local_exists = self.db.get_book_by_sync_id(obj.syncId) is not None
                    elif name == "members":
                        local_exists = self.db.get_member_by_sync_id(obj.syncId) is not None
                    elif name == "issued_books":
                        local_exists = self.db.get_issue_by_sync_id(obj.syncId) is not None
                    elif name == "reservations":
                        local_exists = self.db.get_reservation_by_sync_id(obj.syncId) is not None
                    if local_exists:
                        continue

                # ── Conflict detection ──
                if obj.syncId in pending_ids:
                    local_obj = None
                    if name == "books":
                        local_obj = next((b for b in self.db.get_books(include_deleted=True) if b.syncId == obj.syncId), None)
                    elif name == "members":
                        local_obj = next((m for m in self.db.get_members(include_deleted=True) if m.syncId == obj.syncId), None)
                    elif name == "issued_books":
                        local_obj = next((i for i in self.db.get_issues(include_deleted=True) if i.syncId == obj.syncId), None)
                    elif name == "reservations":
                        local_obj = next((r for r in self.db.get_reservations(include_deleted=True) if r.syncId == obj.syncId), None)

                    if local_obj and local_obj.lastUpdated > obj.lastUpdated:
                        # Local is newer → local wins. Log conflict, advance cursor,
                        # but DO NOT overwrite local data and leave it in the queue.
                        self.db.log_conflict(name, obj.syncId, local_obj.to_dict(), obj.to_dict())
                        max_ts = max(max_ts, obj.lastUpdated)
                        continue

                    # Remote is newer (or equal) → remote wins.
                    self.db.auto_resolve_conflict(name, obj.syncId)
                    self.db.remove_from_sync_queue(name, obj.syncId)

                to_save.append(obj)
                max_ts = max(max_ts, obj.lastUpdated)

            if to_save:
                if name == "books":
                    self.db.save_books_batch(to_save, is_clean=True)
                elif name == "members":
                    self.db.save_members_batch(to_save, is_clean=True)
                elif name == "issued_books":
                    self.db.save_issues_batch(to_save, is_clean=True)
                elif name == "reservations":
                    self.db.save_reservations_batch(to_save, is_clean=True)
                updated_any = True

            if max_ts > last_pull:
                self.db.set_last_sync_timestamp(name, max_ts)
        except Exception as e:
            self.sync_status.emit(f"Pull Error: {str(e)[:25]}...")

        return updated_any


# Alias for backward compatibility with main.py
SyncService = RealtimeSyncService

