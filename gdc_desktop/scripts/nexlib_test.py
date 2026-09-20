"""
scripts/nexlib_test.py — the test and diagnosis tool for NEXLIB.

Three jobs, one tool:

    python scripts/nexlib_test.py crash
    python scripts/nexlib_test.py sync  --email librarian@x.edu --password ...
    python scripts/nexlib_test.py load  --colleges 10 --books 5000

WHY EACH EXISTS

  crash  The desktop app catches unhandled exceptions and writes them to a log
         (main.py), but nobody reads a log. This groups them by fault so the
         same crash happening 40 times reads as one line, not forty.

  sync   "Sync is not working" is not a diagnosis. Sync is eight stages, and a
         failure in any one looks identical from the UI. This walks the stages
         in order as a REAL CLIENT — signing in over the REST API and carrying
         that user's token — and stops at the first one that fails, naming it.
         Testing as a client is the whole point: the desktop app talks to
         Firestore through the Admin SDK, which bypasses every security rule,
         so a rules problem is invisible to it and breaks only Android and web.

  load   Measures what the sync design actually costs, rather than trusting a
         projection. It reports documents read per college per day and the
         annual bill at provincial scale — the numbers the proposal stands on.
         Run it before and after making the listeners incremental: the gap
         between the two runs is the evidence for the whole cost case.

Load mode writes real data to real Firestore. It prefixes every synthetic
college with LOADTEST- and `--cleanup` removes exactly those, so it can never
touch a college's records.
"""
import argparse
import json
import re
import sqlite3
import sys
import time
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import config  # noqa: E402

try:
    import requests
except ImportError:
    print("requests is not installed. Run: pip install requests")
    sys.exit(1)

IDENTITY = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={}"
FIRESTORE = "https://firestore.googleapis.com/v1/projects/{pid}/databases/(default)/documents"
LOAD_PREFIX = "LOADTEST-"

# Firestore document-read pricing, and the free daily allowance. Both move —
# re-check at firebase.google.com/pricing before quoting anything from here.
USD_PER_READ = 0.06 / 100_000
USD_PER_WRITE = 0.18 / 100_000
FREE_READS_PER_DAY = 50_000
PKR_PER_USD = 280

OK, BAD, NOTE = "  ok  ", " FAIL ", "      "


def line(mark, what, detail=""):
    print(f"[{mark}] {what}" + (f"   {detail}" if detail else ""))


def project_id():
    """Read the project id from the service account key, without initialising
    the Admin SDK — sync mode must work on a machine that has no key at all."""
    key = config.APP_DIR / config.FIREBASE_CRED_PATH
    if key.exists():
        try:
            return json.loads(key.read_text()).get("project_id", "")
        except Exception:
            pass
    bucket = config.FIREBASE_STORAGE_BUCKET or ""
    return bucket.split(".")[0] if bucket else ""


# ─────────────────────────────────────────────────────────────────────────────
# CRASH
# ─────────────────────────────────────────────────────────────────────────────

def cmd_crash(args):
    """Group the log's crashes by fault so repeats collapse into one line."""
    candidates = [
        Path("gdc_library.log"),                 # launched from the app folder
        config.APP_DIR / "gdc_library.log",      # beside the .exe
        Path.home() / "GDCLibrary50" / "gdc_library.log",
    ]
    if args.log:
        candidates.insert(0, Path(args.log))
    logs = [p for p in candidates if p.exists()]

    if not logs:
        line(NOTE, "No log file found. Looked in:")
        for p in candidates:
            print(f"        {p}")
        print("\nThe log path is relative to the working directory, so a shortcut")
        print("launch can put it somewhere unexpected. Pass --log <path> if you")
        print("know where it is.")
        return 0

    faults, errors, total_lines = Counter(), Counter(), 0
    first_seen, last_seen = {}, {}
    ts_re = re.compile(r"^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})")

    for log in logs:
        line(NOTE, "Reading", f"{log} ({log.stat().st_size/1024:.0f} KB)")
        text = log.read_text(encoding="utf-8", errors="replace")
        total_lines += text.count("\n")

        # Unhandled exceptions: the global handler writes the whole traceback,
        # and the final line is the one that names the fault.
        for block in re.split(r"Unhandled exception:", text)[1:]:
            tb = [l for l in block.splitlines() if l.strip()]
            fault = next((l.strip() for l in reversed(tb[:40])
                          if re.match(r"^[A-Za-z_.]+(Error|Exception|Warning)\b", l.strip())),
                         tb[-1].strip() if tb else "unknown")
            faults[fault[:140]] += 1

        for l in text.splitlines():
            # Skip the CRITICAL line that opens a traceback: it is already
            # counted, by its actual fault, in the crash tally above. Counting
            # it here too would list every crash twice under a heading that
            # names none of them.
            if "Unhandled exception" in l:
                continue
            if " - ERROR - " in l or " - CRITICAL - " in l:
                msg = l.split(" - ", 3)[-1].strip()
                errors[msg[:120]] += 1
                m = ts_re.match(l)
                if m:
                    first_seen.setdefault(msg[:120], m.group(1))
                    last_seen[msg[:120]] = m.group(1)

    print()
    if not faults and not errors:
        line(OK, "No crashes or errors recorded", f"{total_lines:,} log lines scanned")
        return 0

    if faults:
        print(f"CRASHES — {sum(faults.values())} total, {len(faults)} distinct\n")
        for fault, n in faults.most_common(args.top):
            print(f"  {n:>4} x  {fault}")
        print()
    if errors:
        print(f"ERRORS — {sum(errors.values())} total, {len(errors)} distinct\n")
        for msg, n in errors.most_common(args.top):
            seen = last_seen.get(msg, "")
            print(f"  {n:>4} x  {msg}")
            if seen and first_seen.get(msg) != seen:
                print(f"         first {first_seen[msg]}, last {seen}")
        print()
    print("Send the top entries with the log file when reporting a defect.")
    return 1 if faults else 0


# ─────────────────────────────────────────────────────────────────────────────
# SYNC — eight stages, stop at the first failure
# ─────────────────────────────────────────────────────────────────────────────

def _rest_get(pid, token, path, params=None):
    return requests.get(f"{FIRESTORE.format(pid=pid)}/{path}",
                        headers={"Authorization": f"Bearer {token}"},
                        params=params or {}, timeout=20)


def cmd_sync(args):
    pid = args.project or project_id()
    stage = 0

    def fail(msg, fix):
        print()
        line(BAD, f"Stage {stage} — this is where sync is breaking")
        print(f"\n  {msg}\n\n  Fix: {fix}\n")
        return 1

    stage = 1
    if not config.FIREBASE_WEB_API_KEY or \
            config.FIREBASE_WEB_API_KEY == "your-firebase-web-api-key":
        return fail("No Firebase web API key in .env.",
                    "Project Settings -> General -> Web API Key, into .env")
    if not pid:
        return fail("Could not determine the Firebase project id.",
                    "Pass --project <id>, or set FIREBASE_STORAGE_BUCKET in .env")
    line(OK, "1. Configuration", f"project {pid}")

    stage = 2
    r = requests.post(IDENTITY.format(config.FIREBASE_WEB_API_KEY),
                      json={"email": args.email, "password": args.password,
                            "returnSecureToken": True}, timeout=20)
    data = r.json()
    if "error" in data:
        code = data["error"].get("message", "")
        # The remedy differs completely by cause, so match it rather than
        # printing one generic hint that is wrong most of the time.
        if "API key" in code:
            fix = ("The web API key in .env is wrong or belongs to another "
                   "project. Firebase Console -> Project Settings -> General "
                   "-> Web API Key.")
        elif code.startswith("INVALID_LOGIN_CREDENTIALS") or code.startswith("INVALID_PASSWORD"):
            fix = ("Wrong password, OR no such account — Firebase deliberately "
                   "reports both identically so addresses cannot be probed. "
                   "Confirm the account exists: manage_directorate.py list-auth")
        elif code.startswith("EMAIL_NOT_FOUND"):
            fix = "No account with this email. Create it, or check for a typo."
        elif code.startswith("USER_DISABLED"):
            fix = "The account is disabled in Firebase Auth. Re-enable it."
        elif "OPERATION_NOT_ALLOWED" in code:
            fix = ("Email/password sign-in is switched off for this project. "
                   "Firebase Console -> Authentication -> Sign-in method.")
        elif "TOO_MANY_ATTEMPTS" in code:
            fix = "Rate limited after repeated failures. Wait and retry."
        else:
            fix = "Check the account and the project configuration in .env."
        return fail(f"Sign-in refused: {code}", fix)
    token, uid = data["idToken"], data["localId"]
    line(OK, "2. Authentication", f"signed in as {args.email} (uid {uid[:10]}…)")

    stage = 3
    r = _rest_get(pid, token, f"users/{uid}")
    if r.status_code == 403:
        return fail("Rules denied reading this account's own profile.",
                    "firestore.rules is not deployed, or /users is misconfigured. "
                    "Run: firebase deploy --only firestore:rules")
    if r.status_code == 404:
        return fail("No /users profile for this account.",
                    "The account exists in Auth but has no Firestore profile, so "
                    "no rule can resolve its role or college. Repair with: "
                    "manage_directorate.py repair-user <email> <role> <collegeId>")
    fields = r.json().get("fields", {})
    role = fields.get("role", {}).get("stringValue", "")
    cid = fields.get("institutionId", {}).get("stringValue", "")
    if not cid:
        return fail(f"Profile exists (role '{role}') but institutionId is blank.",
                    "This is the 'Not linked' state — the app has no college to "
                    "sync with. Complete onboarding, or: manage_directorate.py "
                    "set-institution <email> <collegeId>")
    line(OK, "3. User profile", f"role '{role}', institution '{cid}'")

    stage = 4
    r = _rest_get(pid, token, f"institutions/{cid}")
    if r.status_code != 200:
        return fail(f"Cannot read institutions/{cid} (HTTP {r.status_code}).",
                    "The profile points at a college that does not exist — "
                    "usually a stale institutionId after a wipe. Re-run onboarding.")
    owner = r.json().get("fields", {}).get("ownerUid", {}).get("stringValue", "")
    line(OK, "4. Institution", f"{cid}" + ("" if owner else "  (no ownerUid — see below)"))

    stage = 5
    counts = {}
    for sub in ("books", "members", "issued_books", "reservations"):
        r = _rest_get(pid, token, f"institutions/{cid}/{sub}", {"pageSize": 1})
        if r.status_code == 403:
            return fail(f"Rules denied READING institutions/{cid}/{sub}.",
                        "The account's role is not accepted by canReadCollege(). "
                        "Check its role against firestore.rules, then redeploy.")
        counts[sub] = "reachable" if r.status_code == 200 else f"HTTP {r.status_code}"
    line(OK, "5. Read access", ", ".join(f"{k} {v}" for k, v in counts.items()))

    stage = 6
    probe = f"institutions/{cid}/_sync/selftest"
    body = {"fields": {"probe": {"stringValue": datetime.now(timezone.utc).isoformat()}}}
    r = requests.patch(f"{FIRESTORE.format(pid=pid)}/{probe}",
                       headers={"Authorization": f"Bearer {token}"},
                       json=body, timeout=20)
    if r.status_code == 403:
        return fail(f"Rules denied WRITING to {cid}.",
                    "Reads work but writes do not: canManageCollege() is rejecting "
                    "this role. This is the classic 'everything looks fine but "
                    "nothing uploads' fault.")
    if r.status_code != 200:
        return fail(f"Write probe failed (HTTP {r.status_code}): {r.text[:160]}",
                    "Check connectivity and the rules deployment timestamp.")
    requests.delete(f"{FIRESTORE.format(pid=pid)}/{probe}",
                    headers={"Authorization": f"Bearer {token}"}, timeout=20)
    line(OK, "6. Write access", "probe written and removed")

    stage = 7
    db_path = Path(config.LOCAL_DB_PATH)
    pending = tombstones = None
    if db_path.exists():
        try:
            con = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
            tombstones = sum(
                con.execute(f"SELECT COUNT(*) FROM {t} WHERE deleted = 1").fetchone()[0]
                for t in ("books", "members", "issued_books", "reservations"))
            con.close()
            line(OK, "7. Local database", f"{tombstones} tombstone(s) awaiting push")
        except sqlite3.Error as e:
            line(NOTE, "7. Local database", f"unreadable: {e}")
    else:
        line(NOTE, "7. Local database", "none on this machine")

    stage = 8
    print()
    line(OK, "All eight stages passed")
    print("\n  Sync is healthy for this account. If a device still looks stuck:")
    print("   - the badge reads 'Not linked' -> sign out and back in; the")
    print("     institution id is written only at login")
    print("   - the badge reads 'Offline' -> Firebase did not initialise at all,")
    print("     which is a build problem (google-services.json), not a network one")
    if not owner:
        print("\n  Note: this institution has no ownerUid. That is the signature of")
        print("  a document republished from a stale local database rather than")
        print("  created by real registration.")
    return 0


# ─────────────────────────────────────────────────────────────────────────────
# LOAD
# ─────────────────────────────────────────────────────────────────────────────

def _admin():
    try:
        import firebase_admin
        from firebase_admin import credentials, firestore
    except ImportError:
        print("firebase-admin is not installed. Run: pip install firebase-admin")
        sys.exit(1)
    key = config.APP_DIR / config.FIREBASE_CRED_PATH
    if not key.exists():
        print(f"Service account key not found at {key} — load mode needs it.")
        sys.exit(1)
    if not firebase_admin._apps:
        firebase_admin.initialize_app(credentials.Certificate(str(key)))
    return firestore.client()


def cmd_load(args):
    db = _admin()

    if args.cleanup:
        removed = 0
        for doc in db.collection("institutions").list_documents():
            if not doc.id.startswith(LOAD_PREFIX):
                continue
            for sub in doc.collections():
                while True:
                    batch, n = db.batch(), 0
                    for d in sub.limit(400).stream():
                        batch.delete(d.reference); n += 1
                    if not n:
                        break
                    batch.commit(); removed += n
            doc.delete(); removed += 1
            db.collection("directorate_index").document(doc.id).delete()
        line(OK, "Cleanup complete", f"{removed} synthetic document(s) removed")
        return 0

    members = max(1, args.books // 13)
    loans = max(1, args.books // 3)
    per_college = args.books + members + loans
    print(f"Seeding {args.colleges} college(s) x {per_college:,} documents "
          f"({args.books:,} books, {members:,} members, {loans:,} loans)\n")

    started = time.time()
    written = 0
    for c in range(args.colleges):
        cid = f"{LOAD_PREFIX}{c+1:03d}"
        db.collection("institutions").document(cid).set(
            {"name": f"Load Test College {c+1}", "ownerUid": "loadtest",
             "createdAt": int(time.time() * 1000)})
        written += 1
        for sub, count, shape in (
                ("books", args.books, lambda i: {
                    "syncId": f"b{i}", "title": f"Test Book {i}",
                    "author": f"Author {i % 500}", "isbn": f"978{i:010d}",
                    "category": f"Cat {i % 40}", "status": "Available",
                    "price": 500.0, "deleted": False, "collegeId": cid,
                    "lastUpdated": int(time.time() * 1000), "syncStatus": "synced"}),
                ("members", members, lambda i: {
                    "syncId": f"m{i}", "name": f"Student {i}",
                    "memberId": f"STU{i:05d}", "deleted": False, "collegeId": cid,
                    "lastUpdated": int(time.time() * 1000), "syncStatus": "synced"}),
                ("issued_books", loans, lambda i: {
                    "syncId": f"i{i}", "bookId": i, "memberId": i % max(1, members),
                    "status": "Issued" if i % 4 else "Returned", "fine": 0.0,
                    "dueDate": "2026-12-31", "deleted": False, "collegeId": cid,
                    "lastUpdated": int(time.time() * 1000), "syncStatus": "synced"})):
            col = db.collection("institutions").document(cid).collection(sub)
            for start in range(0, count, 450):
                batch = db.batch()
                for i in range(start, min(start + 450, count)):
                    batch.set(col.document(shape(i)["syncId"]), shape(i))
                batch.commit()
                written += min(450, count - start)
        print(f"  {cid}  seeded ({written:,} documents written so far)")

    elapsed = time.time() - started
    print(f"\nSeeded {written:,} documents in {elapsed:.0f}s "
          f"({written/max(elapsed,1):.0f} writes/sec)\n")

    # Read amplification: exactly what an unfiltered collection listener does
    # on attach. This is the measurement the cost model rests on.
    print("Measuring read amplification (one full-collection attach)…")
    cid = f"{LOAD_PREFIX}001"
    t0 = time.time()
    read = sum(1 for sub in ("books", "members", "issued_books", "reservations")
               for _ in db.collection("institutions").document(cid).collection(sub).stream())
    pull = time.time() - t0

    daily = read * args.devices * args.sessions
    prov_month = daily * args.province * 30
    usd_year = prov_month * 12 * USD_PER_READ
    print(f"\n  documents per attach      {read:>12,}")
    print(f"  attach time               {pull:>12.1f}s")
    print(f"  reads/day/college         {daily:>12,}   "
          f"({daily/FREE_READS_PER_DAY:.1f}x the free daily allowance)")
    print(f"  reads/month, {args.province} colleges {prov_month:>12,}")
    print(f"  projected cost/year       {'PKR ' + format(usd_year * PKR_PER_USD, ',.0f'):>12}")
    print(f"\n  Assumes {args.devices} devices x {args.sessions} app starts/day, "
          f"PKR {PKR_PER_USD}/USD.")
    print("  Re-run this after making the listeners incremental. The difference")
    print("  between the two figures is the evidence for the cost case.")
    print(f"\n  Remove the synthetic data with:  "
          f"python scripts/nexlib_test.py load --cleanup")
    return 0


def main():
    ap = argparse.ArgumentParser(description="NEXLIB test and diagnosis tool")
    sub = ap.add_subparsers(dest="command", required=True)

    c = sub.add_parser("crash", help="group crashes and errors from the app log")
    c.add_argument("--log", default="", help="explicit path to gdc_library.log")
    c.add_argument("--top", type=int, default=10, help="how many distinct faults to show")

    s = sub.add_parser("sync", help="locate which stage of sync is failing")
    s.add_argument("--email", required=True)
    s.add_argument("--password", required=True)
    s.add_argument("--project", default="", help="Firebase project id, if not derivable")

    l = sub.add_parser("load", help="seed synthetic colleges and measure read cost")
    l.add_argument("--colleges", type=int, default=5)
    l.add_argument("--books", type=int, default=5000)
    l.add_argument("--devices", type=int, default=3, help="devices per college")
    l.add_argument("--sessions", type=int, default=3, help="app starts per device per day")
    l.add_argument("--province", type=int, default=350, help="colleges to project to")
    l.add_argument("--cleanup", action="store_true", help="delete all LOADTEST- data")

    args = ap.parse_args()
    print(f"NEXLIB test tool — {datetime.now(timezone.utc):%Y-%m-%d %H:%M} UTC\n")
    return {"crash": cmd_crash, "sync": cmd_sync, "load": cmd_load}[args.command](args)


if __name__ == "__main__":
    sys.exit(main())
