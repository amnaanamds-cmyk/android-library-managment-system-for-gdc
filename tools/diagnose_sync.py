"""
Why are two clients showing different data?

Almost always the answer is: they are not looking at the same institution.

Every client reads and writes /institutions/{institutionId}/... and resolves
institutionId from the signed-in account. If the desktop and the phone resolve
DIFFERENT ids, both sync perfectly and neither ever sees the other — which
presents as "sync is broken" when in fact each is faithfully syncing into its
own tenant.

This script reads the project and reports:

  * every institution that exists, and how much data each holds
  * every user account, the institutionId it resolves to, and whether that
    institution actually exists
  * what the desktop client on THIS machine is configured to use (.env)
  * accounts pointing at an institution with no document — the usual cause

It only reads. Nothing is modified.

    cd gdc_desktop
    python ../tools/diagnose_sync.py

Needs serviceAccountKey.json and .env, the same two files the desktop needs.
"""
import os
import sys
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
DESKTOP = os.path.join(HERE, "..", "gdc_desktop")
sys.path.insert(0, DESKTOP)

try:
    import firebase_admin
    from firebase_admin import credentials, firestore, auth as fb_auth
except ImportError:
    sys.exit("firebase-admin is not installed. Run: pip install firebase-admin")

try:
    import config
except Exception as exc:  # pragma: no cover - configuration problem, not logic
    sys.exit(f"Could not load gdc_desktop/config.py: {exc}")


def bold(s):
    return f"\033[1m{s}\033[0m"


def rule(title):
    print(f"\n{bold(title)}\n" + "─" * len(title))


# ── Connect ──────────────────────────────────────────────────────────────────

cred_path = config.FIREBASE_CRED_PATH
if not os.path.isabs(cred_path):
    cred_path = os.path.join(DESKTOP, cred_path)

if not os.path.exists(cred_path):
    sys.exit(
        f"Service account key not found at {cred_path}\n"
        "Firebase Console -> Project Settings -> Service Accounts -> Generate new private key."
    )

firebase_admin.initialize_app(credentials.Certificate(cred_path))
db = firestore.client()

print(bold("\nNEXLIB sync diagnosis"))
print(f"Service account : {cred_path}")
print(f"Desktop .env COLLEGE_ID : {config.COLLEGE_ID!r}")

# ── Institutions that exist ──────────────────────────────────────────────────

rule("Institutions that exist")

institutions = {}
for d in db.collection("institutions").stream():
    institutions[d.id] = d.to_dict() or {}

if not institutions:
    print("  NONE. No institution documents at all — every client is writing")
    print("  into a tenant path whose root document does not exist.")
else:
    for iid, data in sorted(institutions.items()):
        base = f"institutions/{iid}"

        def count(sub):
            try:
                return db.collection(f"{base}/{sub}").count().get()[0][0].value
            except Exception:
                try:
                    return sum(1 for _ in db.collection(f"{base}/{sub}").stream())
                except Exception:
                    return "?"

        print(f"  {bold(iid)}")
        print(f"      name    : {data.get('name') or data.get('collegeName') or '(unnamed)'}")
        print(f"      district: {data.get('district') or '(none)'}")
        print(
            f"      books={count('books')}  members={count('members')}  "
            f"issued={count('issued_books')}  reservations={count('reservations')}"
        )

# ── Where the data actually is ───────────────────────────────────────────────
#
# Subcollections exist independently of their parent document, so data can sit
# under an institution id that has no document. Those tenants are invisible in
# the listing above but are exactly where a "missing" catalogue has gone.

rule("Tenant paths holding data (including ones with no institution document)")

data_tenants = defaultdict(dict)
for group in ("books", "members", "issued_books"):
    try:
        for d in db.collection_group(group).limit(5000).stream():
            parts = d.reference.path.split("/")
            if len(parts) >= 4 and parts[0] == "institutions":
                data_tenants[parts[1]][group] = data_tenants[parts[1]].get(group, 0) + 1
    except Exception as exc:
        print(f"  (could not scan {group}: {exc})")

if not data_tenants:
    print("  No tenant data found anywhere.")
for iid, counts in sorted(data_tenants.items(), key=lambda kv: -sum(kv[1].values())):
    orphan = "" if iid in institutions else "   <-- NO institution document"
    summary = "  ".join(f"{k}={v}" for k, v in sorted(counts.items()))
    print(f"  {iid:<45} {summary}{orphan}")

# ── Accounts and the institution each resolves to ────────────────────────────

rule("Accounts and the institution each one resolves to")

profiles = {}
for d in db.collection("users").stream():
    profiles[d.id] = d.to_dict() or {}

rows = []
try:
    page = fb_auth.list_users()
    while page:
        for u in page.users:
            p = profiles.get(u.uid, {})
            claims = u.custom_claims or {}
            rows.append(
                {
                    "email": u.email or "(no email)",
                    "uid": u.uid,
                    "claim_inst": claims.get("institutionId") or "",
                    "claim_role": claims.get("role") or "",
                    "doc_inst": p.get("institutionId") or p.get("collegeId") or "",
                    "doc_role": p.get("role") or "",
                    "has_doc": u.uid in profiles,
                }
            )
        page = page.get_next_page()
except Exception as exc:
    print(f"  Could not list auth users ({exc}); falling back to profile documents.")
    for uid, p in profiles.items():
        rows.append(
            {
                "email": p.get("email", "(unknown)"),
                "uid": uid,
                "claim_inst": "",
                "claim_role": "",
                "doc_inst": p.get("institutionId") or p.get("collegeId") or "",
                "doc_role": p.get("role") or "",
                "has_doc": True,
            }
        )

for r in sorted(rows, key=lambda r: r["email"]):
    # Claims win at runtime; the profile document is the fallback.
    effective = r["claim_inst"] or r["doc_inst"]
    note = ""
    if not effective:
        note = "   <-- NO institution: this account cannot sync at all"
    elif effective not in institutions:
        note = "   <-- institution document MISSING"
    print(f"  {r['email']:<34} {effective or '(none)':<40}{note}")
    print(f"      uid={r['uid']}")
    print(
        f"      claims: institutionId={r['claim_inst'] or '-'} role={r['claim_role'] or '-'}"
        f"   |   users/{{uid}}: institutionId={r['doc_inst'] or '-'} role={r['doc_role'] or '-'}"
    )
    if r["claim_inst"] and r["doc_inst"] and r["claim_inst"] != r["doc_inst"]:
        print("      MISMATCH: the claim and the profile document disagree.")
        print("      Claims win, so this account uses the claim's institution.")

# ── Verdict ──────────────────────────────────────────────────────────────────

rule("Verdict")

effective_ids = {(r["claim_inst"] or r["doc_inst"]) for r in rows if (r["claim_inst"] or r["doc_inst"])}
problems = []

if len(effective_ids) > 1:
    problems.append(
        f"Accounts are split across {len(effective_ids)} institutions: "
        + ", ".join(sorted(effective_ids))
        + ".\n     Two clients signed in as different accounts here will NEVER see each\n"
        "     other's data, however well sync is working."
    )

if config.COLLEGE_ID and config.COLLEGE_ID not in institutions:
    problems.append(
        f"The desktop .env COLLEGE_ID is {config.COLLEGE_ID!r}, which has no\n"
        "     institution document. The desktop is syncing into a tenant nobody else reads."
    )

orphans = [iid for iid in data_tenants if iid not in institutions]
if orphans:
    problems.append(
        "Data exists under institution ids with no institution document: "
        + ", ".join(orphans)
        + ".\n     That data is effectively stranded."
    )

if not problems:
    print("  Every account resolves to the same existing institution.")
    print("  If clients still disagree, the cause is not tenant routing —")
    print("  check the sync status badge and the desktop's serviceAccountKey.json.")
else:
    for i, p in enumerate(problems, 1):
        print(f"  {i}. {p}")
    print(
        "\n  To fix: decide which institutionId is canonical, then point every\n"
        "  account at it (users/{uid}.institutionId, and the custom claim if set)\n"
        "  and set COLLEGE_ID in gdc_desktop/.env to the same value.\n"
        "  Existing data under the other ids must be migrated or abandoned —\n"
        "  changing an account's institutionId does NOT move its records."
    )
print()
