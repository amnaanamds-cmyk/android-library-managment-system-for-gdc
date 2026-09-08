"""
Offline-sync tests for the PyQt6 desktop client.

These cover the four scenarios the KPK spec (section 5.1) says to verify before
a demo, plus the batching and backoff behaviour:

  1. Issue a book with the network down  -> appears instantly, sits in the queue
  2. Reconnect                           -> pushes, and leaves the queue
  3. Two devices edit while both offline -> a conflict record, not an overwrite
  4. Kill the app mid-sync               -> the queued item survives and retries

Firestore is replaced with an in-memory double that records every call, so the
tests exercise the real sync engine and the real SQLite layer. Run:

    python3 -m pytest tests/desktop/ -v
        (or)
    python3 tests/desktop/test_sync_engine.py
"""
import os
import sys
import tempfile
import time
import uuid

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "gdc_desktop"))

from services.database_helper import DatabaseHelper       # noqa: E402
from services.sync_service import RealtimeSyncService     # noqa: E402
from models import Book                                   # noqa: E402


# ── An in-memory stand-in for a Firestore collection ─────────────────────────

class FakeDoc:
    def __init__(self, store, doc_id, owner=None):
        self._store = store
        self.id = doc_id
        self._owner = owner

    def get(self):
        return self

    @property
    def exists(self):
        return self.id in self._store

    def to_dict(self):
        return dict(self._store.get(self.id, {}))

    def set(self, data, merge=False):
        # A direct write fails when the network is down, exactly as a batched
        # one does. Without this the individual-write fallback in _push_batch
        # would appear to succeed while offline.
        if self._owner is not None and self._owner.fail_writes:
            raise ConnectionError("network down")
        if merge and self.id in self._store:
            self._store[self.id].update(data)
        else:
            self._store[self.id] = dict(data)


class FakeCollection:
    def __init__(self, store, owner=None):
        self._store = store
        self._owner = owner

    def document(self, doc_id):
        return FakeDoc(self._store, doc_id, self._owner)

    def collection(self, name):
        return FakeCollection(self._store.setdefault(f"__sub_{name}", {}), self._owner)


class FakeBatch:
    """Batched write. All-or-nothing, like the real thing."""

    def __init__(self, owner):
        self.owner = owner
        self.ops = []

    def set(self, doc_ref, data):
        self.ops.append((doc_ref, data))

    def commit(self):
        if self.owner.fail_writes:
            raise ConnectionError("network down")
        self.owner.batch_commits += 1
        self.owner.batch_sizes.append(len(self.ops))
        for doc_ref, data in self.ops:
            doc_ref.set(data)


class FakeDb:
    def __init__(self, owner):
        self.owner = owner

    def batch(self):
        return FakeBatch(self.owner)


class FakeFirebaseService:
    """Implements only what the sync engine calls."""

    def __init__(self):
        self.mock_mode = False
        self.college_id = "GDC-TEST"
        self.online = True
        self.fail_writes = False
        self.batch_commits = 0
        self.batch_sizes = []
        self.stores = {
            "books": {}, "members": {}, "issued_books": {},
            "reservations": {}, "sync_conflicts": {},
        }
        self.db = FakeDb(self)

    def test_connection(self):
        return self.online

    def _books_ref(self):
        return FakeCollection(self.stores["books"], self) if self.online else None

    def _members_ref(self):
        return FakeCollection(self.stores["members"], self) if self.online else None

    def _issued_ref(self):
        return FakeCollection(self.stores["issued_books"], self) if self.online else None

    def _reservations_ref(self):
        return FakeCollection(self.stores["reservations"], self) if self.online else None

    def _inst_ref(self):
        if not self.online:
            return None
        outer = self

        class Inst:
            def collection(self, name):
                return FakeCollection(outer.stores.setdefault(name, {}), outer)

        return Inst()


# ── Harness ──────────────────────────────────────────────────────────────────

PASS = 0
FAIL = 0


def check(label, condition, detail=""):
    global PASS, FAIL
    if condition:
        print(f"  ok   {label}")
        PASS += 1
    else:
        print(f"  FAIL {label}{(' — ' + detail) if detail else ''}")
        FAIL += 1


def make_env():
    """A DatabaseHelper on a throwaway SQLite file, plus a fake Firestore."""
    tmp = tempfile.NamedTemporaryFile(suffix=".db", delete=False)
    tmp.close()
    db = DatabaseHelper(db_path=tmp.name)
    fb = FakeFirebaseService()
    svc = RealtimeSyncService(db, fb)
    return db, fb, svc, tmp.name


def new_book(title="Offline Title"):
    return Book(syncId=str(uuid.uuid4()), title=title, author="A", accNo="X1")


def queued_ids(db, entity="books"):
    return {i["syncId"] for i in db.get_pending_sync() if i["entityType"] == entity}


# ── 1. Local write while offline ─────────────────────────────────────────────

print("\n── Scenario 1: a write with the network down ──")
db, fb, svc, path = make_env()
fb.online = False

book = new_book("Written Offline")
db.save_book(book)

check("the record is in local SQLite immediately",
      db.get_book_by_sync_id(book.syncId) is not None)
check("the UI can read it back without any network",
      any(b.syncId == book.syncId for b in db.get_books()))
check("it is queued for push", book.syncId in queued_ids(db))

svc._process_pending_push()
check("nothing was pushed while offline", fb.batch_commits == 0)
check("it is still queued after a failed cycle", book.syncId in queued_ids(db))


# ── 2. Reconnect ─────────────────────────────────────────────────────────────

print("\n── Scenario 2: reconnecting drains the queue ──")
fb.online = True
svc._process_pending_push()

check("the record reached Firestore", book.syncId in fb.stores["books"])
check("it left the queue", book.syncId not in queued_ids(db))
check("the document id is the syncId, so a retry overwrites rather than duplicates",
      fb.stores["books"].get(book.syncId, {}).get("syncId") == book.syncId)

# Idempotency: pushing the same record again must not create a second document.
before = len(fb.stores["books"])
db.save_book(db.get_book_by_sync_id(book.syncId))
svc._process_pending_push()
check("re-pushing the same record does not duplicate it",
      len(fb.stores["books"]) == before, f"{before} -> {len(fb.stores['books'])}")


# ── 3. Two devices edit the same record while both offline ───────────────────

print("\n── Scenario 3: a genuine conflict is preserved, not overwritten ──")
db, fb, svc, path = make_env()

shared = new_book("Shared Record")
db.save_book(shared)
svc._process_pending_push()          # both devices start in sync
check("baseline pushed", shared.syncId in fb.stores["books"])

# The OTHER device writes first, while this one is offline.
fb.stores["books"][shared.syncId] = {
    **fb.stores["books"][shared.syncId],
    "title": "Edited On Device B",
    "lastUpdated": int(time.time() * 1000) + 5_000,
}

# This device edits the same record locally and queues it.
local = db.get_book_by_sync_id(shared.syncId)
local.title = "Edited On Device A"
local.lastUpdated = int(time.time() * 1000)
db.save_book(local)

svc._process_pending_push()

remote_title = fb.stores["books"][shared.syncId]["title"]
check("the other device's write was NOT overwritten",
      remote_title == "Edited On Device B", f"remote title is {remote_title!r}")

conflicts = db.get_unresolved_conflicts()
check("a conflict was recorded locally", len(conflicts) == 1)
check("the conflict names the right record",
      bool(conflicts) and conflicts[0]["syncId"] == shared.syncId)
check("a conflict document was written to Firestore",
      len(fb.stores.get("sync_conflicts", {})) == 1)

conflict_doc = next(iter(fb.stores.get("sync_conflicts", {}).values()), {})
check("the conflict carries BOTH versions",
      conflict_doc.get("localValue", {}).get("title") == "Edited On Device A"
      and conflict_doc.get("remoteValue", {}).get("title") == "Edited On Device B")

review = db.get_records_needing_review()
check("the record is flagged for manual review", len(review) == 1)
check("the conflicted record left the queue rather than retrying forever",
      shared.syncId not in queued_ids(db))
check("the local edit is still readable — no work was lost",
      db.get_book_by_sync_id(shared.syncId).title == "Edited On Device A")


# ── 4. Killed mid-sync ───────────────────────────────────────────────────────

print("\n── Scenario 4: killed mid-sync, the queue survives ──")
db, fb, svc, path = make_env()

survivor = new_book("Mid-Sync Kill")
db.save_book(survivor)

fb.fail_writes = True                 # the process dies during the write
svc._process_pending_push()
check("a failed write leaves the record queued", survivor.syncId in queued_ids(db))

# Simulate a restart: brand-new helper and service against the same database.
del db, svc
db2 = DatabaseHelper(db_path=path)
svc2 = RealtimeSyncService(db2, fb)

check("the queue survived the restart", survivor.syncId in queued_ids(db2))

item = next(i for i in db2.get_pending_sync() if i["syncId"] == survivor.syncId)
check("the failure was counted", int(item["retryCount"] or 0) >= 1)
check("a backoff was scheduled", int(item["nextAttemptAt"] or 0) > int(time.time() * 1000))
check("the item is held back until its backoff elapses",
      survivor.syncId not in {i["syncId"] for i in db2.get_due_sync_items()})

# Backoff grows rather than hammering a dead connection.
delays = []
for _ in range(4):
    db2.record_sync_failure("books", survivor.syncId, "still down")
    row = next(i for i in db2.get_pending_sync() if i["syncId"] == survivor.syncId)
    delays.append(int(row["nextAttemptAt"]) - int(time.time() * 1000))
check("backoff increases with each failure", delays == sorted(delays), str(delays))

# Once the network returns and the backoff is cleared, it goes through.
fb.fail_writes = False
db2.record_sync_failure("books", survivor.syncId, "")   # bump, then clear the wait
import sqlite3                                          # noqa: E402
with sqlite3.connect(path) as raw:
    raw.execute("UPDATE sync_queue SET nextAttemptAt = 0")
    raw.commit()

svc2._process_pending_push()
check("the survivor eventually reached Firestore", survivor.syncId in fb.stores["books"])
check("and finally left the queue", survivor.syncId not in queued_ids(db2))


# ── 5. Batching ──────────────────────────────────────────────────────────────

print("\n── Batched pushes ──")
db, fb, svc, path = make_env()
for i in range(10):
    db.save_book(new_book(f"Bulk {i}"))

svc._process_pending_push()
check("10 queued changes went in ONE batch, not 10 round trips",
      fb.batch_commits == 1, f"{fb.batch_commits} commits, sizes {fb.batch_sizes}")
check("all 10 records arrived", len(fb.stores["books"]) == 10)
check("the queue is empty", len(queued_ids(db)) == 0)


print(f"\n{PASS} passed, {FAIL} failed")
sys.exit(0 if FAIL == 0 else 1)
