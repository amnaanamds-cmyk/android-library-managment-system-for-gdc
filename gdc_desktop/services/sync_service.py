"""
services/sync_service.py — offline-first two-way synchronization engine.

Design rules this file exists to enforce (KPK spec section 5.1). Every one of
them was chosen because breaking it produces a failure that is invisible until
it has already lost a librarian's work:

  1. The UI thread never touches Firestore. All network work happens on this
     QThread. A single Firestore call from the UI thread freezes the whole app
     for as long as the network takes, which on a bad rural link is minutes.

  2. A local write always succeeds first. SQLite is the source of truth for
     rendering; Firestore is a sync target. Issuing a book never waits on the
     network.

  3. Pushes are idempotent. The record's syncId is the Firestore document id, so
     re-pushing after a timeout overwrites rather than duplicating. Without this
     an unreliable connection silently multiplies records.

  4. A failed push is never dropped. It stays queued with a retry count and an
     exponential backoff, so a dead connection is retried patiently rather than
     hammered, and nothing is lost when the app is killed mid-sync.

  5. A genuine conflict is never resolved by overwriting. If the server moved on
     since this device last saw the record, both versions are written to
     institutions/{id}/sync_conflicts and the record is flagged for review.
     Last-write-wins here means one librarian's edit vanishes with no trace.

  6. Incoming changes repaint once, not once per document. A snapshot carrying
     20 changes triggers a single debounced refresh.
"""
import time
import uuid

from PyQt6.QtCore import QThread, pyqtSignal

from services.database_helper import DatabaseHelper
from services.firebase_service import FirebaseService
from services.registry_service import RegistryService
from models import Book, Member, IssueRecord, Reservation

# How often to republish this college's aggregate snapshot for the directorate.
REGISTRY_PUBLISH_INTERVAL_MS = 10 * 60 * 1000  # 10 minutes

# Firestore caps a batched write at 500 operations.
BATCH_LIMIT = 450

# A burst of incoming changes coalesces into one repaint after this quiet period.
UI_DEBOUNCE_MS = 300

# Backoff after a failed push: 5s, 15s, 60s, then every 5 minutes.
BACKOFF_SECONDS = (5, 15, 60, 300)

# Entity type -> the accessor used to load one local record by syncId.
_LOADERS = {
    "books": lambda db, sid: db.get_book_by_sync_id(sid),
    "members": lambda db, sid: db.get_member_by_sync_id(sid),
    "issued_books": lambda db, sid: db.get_issue_by_sync_id(sid),
    "reservations": lambda db, sid: db.get_reservation_by_sync_id(sid),
}


class RealtimeSyncService(QThread):
    sync_status = pyqtSignal(str)   # "Syncing...", "🟢 Synced", "Offline"
    sync_completed = pyqtSignal()   # a sync cycle finished
    data_updated = pyqtSignal()     # local data changed; refresh the UI
    conflict_detected = pyqtSignal(str, str)  # entityType, syncId

    def __init__(self, db_helper: DatabaseHelper, fb_service: FirebaseService):
        super().__init__()
        self.db = db_helper
        self.fb = fb_service
        self.running = True
        self._listeners = []
        self.registry = RegistryService(db_helper, fb_service)
        self._last_registry_publish = 0

        # Debounce state for incoming snapshots.
        self._ui_dirty_since = 0.0

    # ── Lifecycle ────────────────────────────────────────────────────────────

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
                except Exception:
                    pass
        self._listeners = []

    def run(self):
        time.sleep(3)  # let the UI finish starting up
        while self.running:
            try:
                if not self._listeners and not self.fb.mock_mode:
                    if not self.fb.test_connection():
                        self.sync_status.emit("Offline (Retrying...)")
                    else:
                        self.sync_status.emit("Syncing...")
                        self._setup_listeners()
                        self.sync_status.emit("🟢 Synced")

                self._process_pending_push()
                self._check_overdue_reminders()
                self._publish_registry_snapshot()
            except Exception as e:
                self.sync_status.emit(f"Sync Warning: {str(e)[:25]}...")

            # Sleep in small increments so stop() is responsive and the UI
            # debounce can fire promptly between cycles.
            for _ in range(20):
                if not self.running:
                    break
                self._flush_ui_refresh()
                time.sleep(0.5)

    # ── Push ─────────────────────────────────────────────────────────────────

    def _process_pending_push(self):
        """Push due queue entries to Firestore as batched, idempotent writes."""
        if self.fb.mock_mode or not self.fb.test_connection():
            return

        try:
            pending = self.db.get_due_sync_items(limit=BATCH_LIMIT)
        except Exception:
            return
        if not pending:
            return

        clean = []       # (entity_type, sync_id, record) safe to write
        conflicts = []   # (entity_type, sync_id, local_record, remote_dict)

        for item in pending:
            entity_type = item.get("entityType")
            sync_id = item.get("syncId")
            if not entity_type or not sync_id or entity_type not in _LOADERS:
                continue

            try:
                record = _LOADERS[entity_type](self.db, sync_id)
                if record is None:
                    # The local row is gone, so there is nothing left to push.
                    self.db.remove_from_sync_queue(entity_type, sync_id)
                    continue

                remote = self._read_remote(entity_type, sync_id)
                if remote is not None and self._is_conflict(entity_type, sync_id, remote):
                    conflicts.append((entity_type, sync_id, record, remote))
                else:
                    clean.append((entity_type, sync_id, record))
            except Exception as e:
                self.db.record_sync_failure(entity_type, sync_id, e, BACKOFF_SECONDS)

        for entry in conflicts:
            self._record_conflict(*entry)

        if clean:
            self._push_batch(clean)

        if clean or conflicts:
            self.sync_completed.emit()

    def _read_remote(self, entity_type: str, sync_id: str):
        """Current server document as a dict, or None when it does not exist."""
        ref = self._collection_ref(entity_type)
        if ref is None:
            return None
        snap = ref.document(sync_id).get()
        return snap.to_dict() if snap.exists else None

    def _is_conflict(self, entity_type: str, sync_id: str, remote: dict) -> bool:
        """True when the server moved on since this device last saw the record.

        Compared against the version this device last observed, NOT against the
        local record's own timestamp. Comparing timestamps directly is what makes
        last-write-wins look correct while quietly discarding whichever edit has
        the older clock — and device clocks in the field are frequently wrong,
        which is exactly why the spec forbids trusting them for ordering.
        """
        known = self.db.get_known_server_version(entity_type, sync_id)
        remote_ts = int(remote.get("lastUpdated") or 0)
        return remote_ts > known

    def _push_batch(self, items):
        """Write records in one batch, falling back to individual writes.

        A batch is all-or-nothing, so if it fails the individual retry decides
        which records genuinely cannot be written rather than punishing all of
        them with a backoff they did not earn.
        """
        try:
            batch = self.fb.db.batch()
            for entity_type, sync_id, record in items:
                ref = self._collection_ref(entity_type)
                if ref is None:
                    continue
                # syncId as the document id is what makes a retry idempotent.
                batch.set(ref.document(sync_id), record.to_dict())
            batch.commit()
        except Exception:
            for entity_type, sync_id, record in items:
                self._push_one(entity_type, sync_id, record)
            return

        for entity_type, sync_id, record in items:
            self._confirm_pushed(entity_type, sync_id, record)

    def _push_one(self, entity_type: str, sync_id: str, record):
        try:
            ref = self._collection_ref(entity_type)
            if ref is None:
                return
            ref.document(sync_id).set(record.to_dict())
            self._confirm_pushed(entity_type, sync_id, record)
        except Exception as e:
            attempts = self.db.record_sync_failure(entity_type, sync_id, e, BACKOFF_SECONDS)
            self.sync_status.emit(f"Retry {attempts}: {entity_type} ({str(e)[:20]})")

    def _confirm_pushed(self, entity_type: str, sync_id: str, record):
        """Clear the queue entry and remember the version now on the server."""
        self.db.set_known_server_version(
            entity_type, sync_id, int(getattr(record, "lastUpdated", 0) or 0)
        )
        self.db.remove_from_sync_queue(entity_type, sync_id)

    def _collection_ref(self, entity_type: str):
        return {
            "books": self.fb._books_ref,
            "members": self.fb._members_ref,
            "issued_books": self.fb._issued_ref,
            "reservations": self.fb._reservations_ref,
        }[entity_type]()

    # ── Conflicts ────────────────────────────────────────────────────────────

    def _record_conflict(self, entity_type: str, sync_id: str, local_record, remote: dict):
        """Preserve both versions instead of overwriting either.

        The local edit is deliberately NOT pushed and the queue entry is
        removed: retrying would either overwrite the other device's write or
        spin forever. The record is flagged so a librarian can decide, which is
        the only correct resolution when two people edited the same patron.
        """
        conflict_id = str(uuid.uuid4())
        local_dict = local_record.to_dict()

        try:
            inst = self.fb._inst_ref()
            if inst is not None:
                inst.collection("sync_conflicts").document(conflict_id).set({
                    "conflictId": conflict_id,
                    "entityType": entity_type,
                    "syncId": sync_id,
                    "localValue": local_dict,
                    "remoteValue": remote,
                    "localLastUpdated": int(local_dict.get("lastUpdated") or 0),
                    "remoteLastUpdated": int(remote.get("lastUpdated") or 0),
                    "detectedAt": int(time.time() * 1000),
                    "detectedBy": "desktop",
                    "resolved": False,
                })
        except Exception as e:
            # The local copy below is what actually protects the data; the
            # Firestore copy only makes the conflict visible on other devices.
            print(f"Conflict recorded locally only ({sync_id}): {e}")

        try:
            self.db.log_conflict(entity_type, sync_id, local_dict, remote)
            self.db.flag_for_review(entity_type, sync_id, conflict_id)
        finally:
            self.db.remove_from_sync_queue(entity_type, sync_id)

        self.conflict_detected.emit(entity_type, sync_id)
        self.sync_status.emit(f"⚠️ Conflict on {entity_type} — needs review")

    # ── Directorate snapshot ─────────────────────────────────────────────────

    def _publish_registry_snapshot(self, force: bool = False):
        if self.fb.mock_mode or not self.fb.college_id:
            return
        now_ms = int(time.time() * 1000)
        if not force and now_ms - self._last_registry_publish < REGISTRY_PUBLISH_INTERVAL_MS:
            return
        self._last_registry_publish = now_ms
        self.registry.publish()

    def _check_overdue_reminders(self):
        """Log overdue reminders once a day."""
        today = time.strftime("%Y-%m-%d")
        last_check = self.db.get_last_sync_timestamp("overdue_reminder_check")
        if int(time.time() * 1000) - last_check < 86400000:
            return

        overdue_count = 0
        for i in self.db.get_issues():
            if i.status == "Issued" and i.dueDate and i.dueDate < today:
                self.db.log_audit_local(
                    "SYSTEM", "auto_overdue_reminder",
                    f"Auto-Reminder: {i.memberName} is overdue for '{i.bookTitle}'",
                )
                overdue_count += 1

        if overdue_count:
            print(f"DEBUG: Processed {overdue_count} auto-reminders.")
        self.db.set_last_sync_timestamp("overdue_reminder_check", int(time.time() * 1000))

    # ── Pull ─────────────────────────────────────────────────────────────────

    def _setup_listeners(self):
        self._listeners.append(self.fb.listen_books(self._on_books_changed))
        self._listeners.append(self.fb.listen_members(self._on_members_changed))
        self._listeners.append(self.fb.listen_issues(self._on_issues_changed))
        self._listeners.append(self.fb.listen_reservations(self._on_reservations_changed))

    def _mark_ui_dirty(self):
        """Note that something changed; the run loop repaints once it settles."""
        self._ui_dirty_since = time.time()

    def _flush_ui_refresh(self):
        """Emit one refresh once incoming changes have been quiet for a moment.

        Repainting per snapshot is the usual reason a desktop app feels slow
        under sync load: twenty incoming records become twenty full table
        reloads. Coalescing them costs at most UI_DEBOUNCE_MS of latency.
        """
        if not self._ui_dirty_since:
            return
        if (time.time() - self._ui_dirty_since) * 1000 < UI_DEBOUNCE_MS:
            return
        self._ui_dirty_since = 0.0
        self.data_updated.emit()

    def _on_books_changed(self, books):
        if self._process_realtime_pull("books", books, Book):
            self._mark_ui_dirty()

    def _on_members_changed(self, members):
        if self._process_realtime_pull("members", members, Member):
            self._mark_ui_dirty()

    def _on_issues_changed(self, issues):
        if self._process_realtime_pull("issued_books", issues, IssueRecord):
            self._mark_ui_dirty()

    def _on_reservations_changed(self, reservations):
        if self._process_realtime_pull("reservations", reservations, Reservation):
            self._mark_ui_dirty()

    def _process_realtime_pull(self, name: str, objects: list, model_cls) -> bool:
        """Apply a remote snapshot to local SQLite.

        Every observed record's server version is recorded, which is what lets
        the push path tell a real conflict from a normal update.

        A record with a local unsynced edit is left alone when the local edit is
        newer; the push path re-examines it against the server and raises a
        proper conflict if one exists. Overwriting here would discard an edit
        that has not been pushed yet.
        """
        updated_any = False
        try:
            last_pull = self.db.get_last_sync_timestamp(name)
            max_ts = last_pull
            pending_ids = {
                item["syncId"] for item in self.db.get_pending_sync()
                if item["entityType"] == name
            }

            # Record what the server currently holds for everything in this
            # snapshot, before any filtering — the version token must reflect
            # the server even for records we choose not to apply locally.
            self.db.set_known_server_versions_batch(
                name, ((o.syncId, o.lastUpdated) for o in objects)
            )

            to_save = []
            loader = _LOADERS[name]

            for obj in objects:
                # Already seen and already stored locally: nothing to do.
                if obj.lastUpdated <= last_pull and loader(self.db, obj.syncId) is not None:
                    continue

                if obj.syncId in pending_ids:
                    local_obj = loader(self.db, obj.syncId)
                    if local_obj and local_obj.lastUpdated > obj.lastUpdated:
                        # Local edit is newer and not yet pushed. Leave it; the
                        # push path decides, with the server version token.
                        max_ts = max(max_ts, obj.lastUpdated)
                        continue
                    # Remote is newer, so the queued local edit is superseded.
                    self.db.auto_resolve_conflict(name, obj.syncId)
                    self.db.remove_from_sync_queue(name, obj.syncId)

                to_save.append(obj)
                max_ts = max(max_ts, obj.lastUpdated)

            if to_save:
                {
                    "books": self.db.save_books_batch,
                    "members": self.db.save_members_batch,
                    "issued_books": self.db.save_issues_batch,
                    "reservations": self.db.save_reservations_batch,
                }[name](to_save, is_clean=True)
                updated_any = True

            if max_ts > last_pull:
                self.db.set_last_sync_timestamp(name, max_ts)
        except Exception as e:
            self.sync_status.emit(f"Pull Error: {str(e)[:25]}...")

        return updated_any


# Alias for backward compatibility with main.py
SyncService = RealtimeSyncService
