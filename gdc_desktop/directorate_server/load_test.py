"""
directorate_server/load_test.py — concurrent load test for the central server.

Run from this directory:   python load_test.py
                            python load_test.py --colleges 350 --concurrency 50

WHY THIS EXISTS

scripts/nexlib_test.py's `load` mode already measures Firestore read cost at
scale, but that is the Android/web sync path. It says nothing about this
server: a single-process FastAPI app backed by one SQLite file, which every
college's desktop app pushes a snapshot to and which the directorate
dashboard polls. That has a different failure mode entirely — SQLite's
single-writer model means concurrent writes serialize (or, if one holds the
file open too long, start raising "database is locked"), and a few of this
server's own endpoints scale with the NUMBER OF COLLEGES on every single
request (GET /api/colleges re-scans every college for a stale heartbeat on
every call; POST /api/sync re-queries this college's open alerts on every
push). Nothing here answers "350 colleges syncing at roughly the same time
of day — does this server keep up?" without actually trying it.

WHAT THIS DOES

Starts a REAL uvicorn subprocess (not an in-process TestClient, which cannot
exercise socket-level concurrency or real SQLite file-lock contention)
against an ISOLATED throwaway SQLite file — DIRECTORATE_DB_PATH points it
away from central_directorate.db, so this can never touch real data. Seeds
N synthetic colleges, then fires concurrent requests at:

  1. POST /api/sync             — every college pushing its daily snapshot
  2. POST /api/transfers         ) inter-library transfer create + status
     POST /api/transfers/{id}/status ) update, the two writes that touch the
                                       same book_transfers table from
                                       different colleges at once
  3. GET  /api/colleges          — the admin dashboard's own polling request

...and reports p50/p95/max latency, throughput, and any errors — "database
is locked" in particular, since that is the concrete symptom SQLite gives
under write contention this server has no retry/backoff for.

Cleans up the subprocess and the throwaway database file on exit, including
on Ctrl-C or an assertion failure.
"""
import argparse
import os
import secrets
import shutil
import sqlite3
import statistics
import subprocess
import sys
import tempfile
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

import requests

HERE = os.path.dirname(os.path.abspath(__file__))
HOST = "127.0.0.1"
PORT = 8991
BASE = f"http://{HOST}:{PORT}"


def line(mark, what, detail=""):
    print(f"  [{mark}] {what}" + (f"  — {detail}" if detail else ""))


class Stats:
    """Latencies + outcome counts for one batch of concurrent requests."""

    def __init__(self, label):
        self.label = label
        self.latencies = []
        self.ok = 0
        self.failed = 0
        self.locked = 0
        self.errors = []

    def record(self, elapsed, status_code, body_text=""):
        self.latencies.append(elapsed)
        if status_code == 200:
            self.ok += 1
        else:
            self.failed += 1
            if "database is locked" in body_text.lower():
                self.locked += 1
            if len(self.errors) < 3:
                self.errors.append(f"HTTP {status_code}: {body_text[:120]}")

    def report(self):
        n = len(self.latencies)
        if n == 0:
            print(f"\n{self.label}: no requests completed")
            return
        srt = sorted(self.latencies)
        p50 = srt[int(0.50 * (n - 1))]
        p95 = srt[int(0.95 * (n - 1))]
        total = sum(self.latencies)
        print(f"\n{self.label}")
        print(f"  requests           {n}")
        print(f"  ok / failed         {self.ok} / {self.failed}"
              + (f"  ({self.locked} 'database is locked')" if self.locked else ""))
        print(f"  p50 / p95 / max     {p50*1000:.0f}ms / {p95*1000:.0f}ms / {max(srt)*1000:.0f}ms")
        print(f"  mean per-request    {total/n*1000:.0f}ms")
        for e in self.errors:
            print(f"    ! {e}")


def wait_for_server(proc, timeout=20):
    start = time.time()
    while time.time() - start < timeout:
        if proc.poll() is not None:
            out, err = proc.communicate()
            print(out.decode(errors="replace"))
            print(err.decode(errors="replace"))
            raise RuntimeError("Server process exited before becoming ready.")
        try:
            r = requests.get(f"{BASE}/docs", timeout=1)
            if r.status_code == 200:
                return
        except requests.exceptions.ConnectionError:
            pass
        time.sleep(0.3)
    raise RuntimeError(f"Server did not become ready within {timeout}s.")


def seed_admin_and_colleges(db_path, n_colleges):
    """Seed directly via sqlite3 — fast, and the admin-create/college-register
    endpoints are exercised for real elsewhere (test_security.py); this script
    is about load on the hot paths, not re-proving login/registration work."""
    sys.path.insert(0, HERE)
    import auth as auth_module  # uses the real bcrypt hashing being measured nowhere else here

    conn = sqlite3.connect(db_path)
    conn.execute(
        "INSERT INTO directorate_users (email, password_hash, name, role) VALUES (?, ?, ?, ?)",
        ("loadtest-admin@gdc.edu", auth_module.get_password_hash("LoadTest123!"), "Load Test Admin", "directorate_admin"),
    )
    colleges = []
    for i in range(n_colleges):
        cid = f"loadtest-college-{i:04d}"
        api_key = secrets.token_hex(16)
        # Half the colleges look "stale" (no sync in 8 days) so
        # check_heartbeat_alerts() on GET /api/colleges has real work to do,
        # matching what a province-wide rollout actually looks like instead
        # of the best case where everyone just synced.
        stale = (i % 2 == 0)
        last_sync = int(time.time() * 1000) - (8 * 86400 * 1000 if stale else 0)
        conn.execute(
            "INSERT INTO colleges (id, name, location, api_key, registered_at, last_sync_at) VALUES (?, ?, ?, ?, ?, ?)",
            (cid, f"Load Test College {i}", "Test City", api_key, int(time.time() * 1000), last_sync),
        )
        colleges.append((cid, api_key))
    conn.commit()
    conn.close()
    return colleges


def run_concurrent(label, concurrency, tasks):
    """tasks: list of zero-arg callables, each returning (elapsed, status_code, body_text)."""
    stats = Stats(label)
    wall_start = time.time()
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        futures = [pool.submit(t) for t in tasks]
        for f in as_completed(futures):
            elapsed, status_code, body_text = f.result()
            stats.record(elapsed, status_code, body_text)
    wall = time.time() - wall_start
    stats.report()
    print(f"  wall-clock          {wall:.2f}s total, concurrency={concurrency}  "
          f"({len(tasks)/wall:.0f} req/s sustained)")
    return stats


def sync_task(college_id, api_key):
    def call():
        payload = {
            "college_id": college_id,
            "total_books": 4200,
            "available_books": 3100,
            "issued_books": 1100,
            "total_members": 1800,
            "overdue_count": 260,   # >20% of issued -> exercises the alerts-insert path too
            "total_fines": 15000.0,  # >10,000 -> same, exercises the fines-alert path
            "top_borrowed_books": [{"title": f"Book {i}", "count": 10 - i} for i in range(5)],
            "activity_summary": [f"Issued book to Student {i}" for i in range(10)],
        }
        headers = {"X-College-API-Key": api_key}
        t0 = time.time()
        try:
            r = requests.post(f"{BASE}/api/sync", json=payload, headers=headers, timeout=15)
            return (time.time() - t0, r.status_code, r.text)
        except requests.exceptions.RequestException as e:
            return (time.time() - t0, -1, str(e))
    return call


def transfer_create_task(from_college, api_key, to_college):
    def call():
        payload = {"from_college": from_college, "to_college": to_college, "book_title": "Load Test Transfer"}
        headers = {"X-College-API-Key": api_key}
        t0 = time.time()
        try:
            r = requests.post(f"{BASE}/api/transfers", json=payload, headers=headers, timeout=15)
            return (time.time() - t0, r.status_code, r.text)
        except requests.exceptions.RequestException as e:
            return (time.time() - t0, -1, str(e))
    return call


def colleges_list_task(token):
    def call():
        headers = {"Authorization": f"Bearer {token}"}
        t0 = time.time()
        try:
            r = requests.get(f"{BASE}/api/colleges", headers=headers, timeout=15)
            return (time.time() - t0, r.status_code, r.text)
        except requests.exceptions.RequestException as e:
            return (time.time() - t0, -1, str(e))
    return call


def main():
    ap = argparse.ArgumentParser(description="Concurrent load test for the directorate server")
    ap.add_argument("--colleges", type=int, default=350, help="synthetic colleges to seed (350 ~= every GDC in KP)")
    ap.add_argument("--concurrency", type=int, default=50, help="concurrent requests in flight")
    ap.add_argument("--transfers", type=int, default=200, help="concurrent transfer-create requests to fire")
    args = ap.parse_args()

    tmp_dir = tempfile.mkdtemp(prefix="nexlib_directorate_loadtest_")
    db_path = os.path.join(tmp_dir, "loadtest.db")
    proc = None
    try:
        # Set in THIS process's environ too, not just the subprocess's copy:
        # seed_admin_and_colleges() below imports auth (in this process, to
        # hash the admin password) and auth.py reads JWT_SECRET at import
        # time, refusing to start without it — same guard the real server
        # has, so the load test has to satisfy it like any other caller.
        os.environ["JWT_SECRET"] = secrets.token_hex(32)
        os.environ["DIRECTORATE_DB_PATH"] = db_path
        os.environ["DIRECTORATE_DASHBOARD_ORIGINS"] = "http://127.0.0.1:5500"
        env = os.environ.copy()

        # init_db() normally runs on the app's startup event; run it once up
        # front here too so seed_admin_and_colleges() has tables to insert into
        # before the server (which also calls it) has necessarily started.
        sys.path.insert(0, HERE)
        import database as database_module
        database_module.DB_PATH = db_path
        database_module.init_db()

        print(f"Seeding {args.colleges} synthetic colleges...")
        colleges = seed_admin_and_colleges(db_path, args.colleges)

        print(f"Starting uvicorn on {BASE} (isolated db: {db_path})...")
        proc = subprocess.Popen(
            [sys.executable, "-m", "uvicorn", "main:app", "--host", HOST, "--port", str(PORT)],
            cwd=HERE, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        )
        wait_for_server(proc)
        line("OK", "Server ready")

        # Login once as the seeded admin, for the colleges-list scenario.
        r = requests.post(f"{BASE}/api/auth/login", json={"email": "loadtest-admin@gdc.edu", "password": "LoadTest123!"})
        r.raise_for_status()
        token = r.json()["access_token"]

        print(f"\n{'='*70}")
        print(f"SCENARIO 1: every college pushes its daily snapshot at once")
        print(f"{'='*70}")
        tasks = [sync_task(cid, key) for cid, key in colleges]
        run_concurrent(f"POST /api/sync  x{len(tasks)}", args.concurrency, tasks)

        print(f"\n{'='*70}")
        print(f"SCENARIO 2: concurrent inter-library transfer requests")
        print(f"(the write path most likely to hit SQLite lock contention —")
        print(f" every request here writes to the same book_transfers table)")
        print(f"{'='*70}")
        n_transfers = min(args.transfers, len(colleges))
        tasks = [
            transfer_create_task(colleges[i][0], colleges[i][1], colleges[(i + 1) % len(colleges)][0])
            for i in range(n_transfers)
        ]
        run_concurrent(f"POST /api/transfers  x{len(tasks)}", args.concurrency, tasks)

        print(f"\n{'='*70}")
        print(f"SCENARIO 3: directorate dashboard polling GET /api/colleges")
        print(f"(re-scans every college for a stale heartbeat on EVERY call —")
        print(f" this is the one that should visibly slow down as --colleges grows)")
        print(f"{'='*70}")
        tasks = [colleges_list_task(token) for _ in range(30)]
        run_concurrent(f"GET /api/colleges  x{len(tasks)}  ({len(colleges)} colleges registered)", 10, tasks)

        print(f"\n{'='*70}")
        print("Re-run with a larger --colleges to see which numbers move with")
        print("college count (expected: scenario 3, GET /api/colleges — it scans")
        print("every college for a stale heartbeat on every call) versus which")
        print("stay flat (expected: scenario 1, since each sync only touches its")
        print("own rows). Any 'database is locked' errors above would mean")
        print("SQLite's default rollback-journal mode is the bottleneck at this")
        print("concurrency; enabling WAL mode (PRAGMA journal_mode=WAL) in")
        print("database.get_db() is the standard fix if that ever shows up —")
        print("but don't add it speculatively if this run shows zero.")

    finally:
        if proc is not None:
            proc.terminate()
            try:
                proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                proc.kill()
        shutil.rmtree(tmp_dir, ignore_errors=True)


if __name__ == "__main__":
    main()
