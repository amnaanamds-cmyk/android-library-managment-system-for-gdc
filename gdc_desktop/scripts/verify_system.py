"""
scripts/verify_system.py — acceptance and health check for a NEXLIB installation.

Run this in three situations:

  1. Before handing a machine to a college, to prove the install is sound.
  2. After a wipe, to prove nothing stale survived.
  3. Periodically during the pilot, to catch the failure mode that matters most:
     local and cloud quietly drifting apart.

That third case is the reason this exists. The apps carry 357 broad exception
handlers, so a sync that stops working does not crash — it simply stops, and
the librarian sees a library that looks fine while records pile up locally and
never reach the network. Nothing in the UI reports that. This does.

Usage (from the gdc_desktop/ directory):

    python scripts/verify_system.py                 # full check
    python scripts/verify_system.py --local-only    # no cloud calls needed
    python scripts/verify_system.py --college GDC-MARDAN-01
    python scripts/verify_system.py --json          # machine-readable

Exit code is 0 when every check passed, 1 when any check failed. Warnings do
not fail the run — they are things to look at, not things that are broken.
"""
import argparse
import json
import sqlite3
import sys
from datetime import datetime, timezone
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import config  # noqa: E402

PASS, WARN, FAIL, INFO = "PASS", "WARN", "FAIL", "INFO"

# Collections that exist both locally and in Firestore, so a per-collection
# count comparison is meaningful. Local table name -> Firestore subcollection.
SYNCED = {
    "books": "books",
    "members": "members",
    "issued_books": "issued_books",
    "reservations": "reservations",
}


class Report:
    def __init__(self):
        self.rows = []

    def add(self, status, check, detail=""):
        self.rows.append({"status": status, "check": check, "detail": detail})
        return status

    @property
    def failed(self):
        return any(r["status"] == FAIL for r in self.rows)

    def render(self):
        mark = {PASS: "  ok  ", WARN: " warn ", FAIL: " FAIL ", INFO: "      "}
        width = max(len(r["check"]) for r in self.rows) if self.rows else 10
        for r in self.rows:
            line = f"[{mark[r['status']]}] {r['check'].ljust(width)}"
            if r["detail"]:
                line += f"   {r['detail']}"
            print(line)
        counts = {s: sum(1 for r in self.rows if r["status"] == s) for s in (PASS, WARN, FAIL)}
        print()
        print(f"{counts[PASS]} passed, {counts[WARN]} warnings, {counts[FAIL]} failed")


# ── Configuration ────────────────────────────────────────────────────────────

def check_config(rep):
    env = config.APP_DIR / ".env"
    rep.add(PASS if env.exists() else WARN, "Configuration file",
            str(env) if env.exists() else f"no .env at {env} (defaults in use)")

    key = config.APP_DIR / config.FIREBASE_CRED_PATH
    if key.exists():
        # Not a pass: this credential bypasses every security rule, so a copy
        # sitting on a college machine is a finding, not a healthy state.
        rep.add(WARN, "Service account key",
                f"present at {key.name} — full-admin credential, see SECURITY.md")
    else:
        rep.add(INFO, "Service account key", "not present (cloud checks will be skipped)")

    if config.COLLEGE_ID:
        rep.add(WARN, "COLLEGE_ID in .env",
                f"pre-set to '{config.COLLEGE_ID}' — should be blank before delivery")
    else:
        rep.add(PASS, "COLLEGE_ID in .env", "blank, as it should be before first login")

    has_web_key = bool(config.FIREBASE_WEB_API_KEY) and \
        config.FIREBASE_WEB_API_KEY != "your-firebase-web-api-key"
    rep.add(PASS if has_web_key else FAIL, "Firebase web API key",
            "set" if has_web_key else "missing — nobody can sign in")


# ── Local database ───────────────────────────────────────────────────────────

def local_counts(rep, expect_empty):
    db_path = Path(config.LOCAL_DB_PATH)
    if not db_path.exists():
        rep.add(PASS if expect_empty else WARN, "Local database",
                "absent — a fresh machine, created on first run")
        return None

    size_mb = db_path.stat().st_size / 1_048_576
    rep.add(INFO, "Local database", f"{db_path} ({size_mb:.1f} MB)")

    counts = {}
    try:
        con = sqlite3.connect(f"file:{db_path}?mode=ro", uri=True)
        cur = con.cursor()
        for table in SYNCED:
            try:
                live = cur.execute(
                    f"SELECT COUNT(*) FROM {table} WHERE deleted = 0").fetchone()[0]
                tomb = cur.execute(
                    f"SELECT COUNT(*) FROM {table} WHERE deleted = 1").fetchone()[0]
                counts[table] = {"live": live, "tombstones": tomb}
            except sqlite3.Error as e:
                rep.add(WARN, f"Local table: {table}", str(e))
        con.close()
    except sqlite3.Error as e:
        rep.add(FAIL, "Local database readable", str(e))
        return None

    total = sum(c["live"] for c in counts.values())
    if expect_empty:
        rep.add(PASS if total == 0 else FAIL, "Local data cleared",
                "empty" if total == 0 else
                f"{total} records still present — the wipe did not take")
    else:
        rep.add(PASS, "Local record counts",
                ", ".join(f"{t}={c['live']}" for t, c in counts.items()))

    tombs = sum(c["tombstones"] for c in counts.values())
    if tombs:
        rep.add(INFO, "Pending deletions", f"{tombs} tombstone(s) awaiting push")
    return counts


def check_stale_journal(rep):
    """A -wal or -shm left beside a deleted database corrupts the next one."""
    base = Path(config.LOCAL_DB_PATH)
    orphans = [p.name for p in (base.parent / f"{base.name}-wal",
                                base.parent / f"{base.name}-shm") if p.exists()]
    if orphans and not base.exists():
        rep.add(FAIL, "Stale SQLite journal",
                f"{', '.join(orphans)} without a .db — delete these before first run")
    else:
        rep.add(PASS, "SQLite journal files", "consistent")


# ── Cloud ────────────────────────────────────────────────────────────────────

def check_cloud(rep, college, local, expect_empty):
    try:
        import firebase_admin
        from firebase_admin import credentials, auth, firestore
    except ImportError:
        rep.add(WARN, "Cloud checks", "firebase-admin not installed — skipped")
        return

    key = config.APP_DIR / config.FIREBASE_CRED_PATH
    if not key.exists():
        rep.add(WARN, "Cloud checks", "no service account key — skipped")
        return

    try:
        if not firebase_admin._apps:
            firebase_admin.initialize_app(credentials.Certificate(str(key)))
        db = firestore.client()
        project = firebase_admin.get_app().project_id
        rep.add(PASS, "Firebase connection", f"project {project}")
    except Exception as e:
        rep.add(FAIL, "Firebase connection", str(e))
        return

    # Directorate account. Without one, nothing can ever be approved.
    try:
        admins = [u for u in db.collection("users")
                  .where("role", "in", ["directorate_admin", "DirectorateAdmin"])
                  .stream()]
        if admins:
            rep.add(PASS, "Directorate account",
                    ", ".join(a.to_dict().get("email", a.id) for a in admins[:3]))
        else:
            rep.add(FAIL, "Directorate account",
                    "none — no college can be approved until one exists")
    except Exception as e:
        rep.add(WARN, "Directorate account", f"could not check: {e}")

    try:
        auth_n = sum(1 for _ in auth.list_users().iterate_all())
        rep.add(INFO, "Firebase Auth accounts", str(auth_n))
    except Exception as e:
        rep.add(WARN, "Firebase Auth accounts", str(e))

    institutions = [d.id for d in db.collection("institutions").list_documents()]
    if expect_empty:
        rep.add(PASS if not institutions else FAIL, "Firestore cleared",
                "empty" if not institutions else
                f"{len(institutions)} institution(s) remain: {', '.join(institutions[:5])}")
        return
    rep.add(INFO, "Institutions in Firestore",
            f"{len(institutions)}: {', '.join(institutions[:6])}" if institutions else "none")

    # Ownerless institutions are the signature of a republish from stale local
    # data rather than a real registration — worth surfacing by name.
    ownerless = []
    for cid in institutions:
        doc = db.collection("institutions").document(cid).get()
        if doc.exists and not (doc.to_dict() or {}).get("ownerUid"):
            ownerless.append(cid)
    if ownerless:
        rep.add(WARN, "Institutions without an owner",
                f"{', '.join(ownerless)} — likely republished by a stale local database")

    # Approval state per college.
    for cid in institutions:
        ap = db.collection("directorate_approvals").document(cid).get()
        status = (ap.to_dict() or {}).get("status", "pending") if ap.exists else "pending"
        rep.add(INFO if status == "approved" else WARN, f"Approval: {cid}", status)

    if not college:
        college = institutions[0] if len(institutions) == 1 else None
    if not college or local is None:
        return

    # The check this tool exists for: has local drifted from cloud?
    rep.add(INFO, "Divergence check", f"comparing local against {college}")
    for table, sub in SYNCED.items():
        if table not in local:
            continue
        try:
            remote = sum(
                1 for d in db.collection("institutions").document(college)
                .collection(sub).stream()
                if not (d.to_dict() or {}).get("deleted", False))
        except Exception as e:
            rep.add(WARN, f"Cloud count: {sub}", str(e))
            continue
        mine = local[table]["live"]
        if mine == remote:
            rep.add(PASS, f"In sync: {sub}", f"{mine} both sides")
        else:
            gap = mine - remote
            rep.add(FAIL, f"Divergence: {sub}",
                    f"local {mine} vs cloud {remote} "
                    f"({'+' if gap > 0 else ''}{gap}) — records are not reaching the network")


def main():
    ap = argparse.ArgumentParser(description="NEXLIB installation health check")
    ap.add_argument("--local-only", action="store_true", help="skip all cloud checks")
    ap.add_argument("--college", default="", help="institution id to compare against")
    ap.add_argument("--expect-empty", action="store_true",
                    help="assert a clean slate: fail if any data is found")
    ap.add_argument("--json", action="store_true", help="emit JSON instead of a table")
    args = ap.parse_args()

    rep = Report()
    print(f"NEXLIB system check — {datetime.now(timezone.utc):%Y-%m-%d %H:%M} UTC\n")

    check_config(rep)
    check_stale_journal(rep)
    local = local_counts(rep, args.expect_empty)
    if not args.local_only:
        check_cloud(rep, args.college.strip().upper(), local, args.expect_empty)

    if args.json:
        print(json.dumps(rep.rows, indent=2))
    else:
        rep.render()

    if rep.failed:
        print("\nOne or more checks FAILED — do not hand this machine over yet.")
    sys.exit(1 if rep.failed else 0)


if __name__ == "__main__":
    main()
