"""
Tests for tools/repair_tenant.py against the Firestore + Auth emulators.

This tool moves real library records between tenants and rewrites the custom
claims that decide what an account can read. Getting it wrong loses a
catalogue, so it is tested against the exact split it exists to repair: one
real institution, plus a second tenant holding stranded records under a UUID
that has no institution document, with accounts pointed at both.

    firebase emulators:start --only auth,firestore
    python tests/tools/test_repair_tenant.py
"""
import os
import sys

HOST = os.environ.get("EMULATOR_HOST", "127.0.0.1")
os.environ["FIRESTORE_EMULATOR_HOST"] = f"{HOST}:8080"
os.environ["FIREBASE_AUTH_EMULATOR_HOST"] = f"{HOST}:9099"
os.environ.setdefault("GCLOUD_PROJECT", "nexlib-e7970")

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, os.path.join(ROOT, "gdc_desktop"))

import firebase_admin
from firebase_admin import credentials, firestore, auth as fb_auth

import repair_tenant

db = repair_tenant.connect("")

REAL = "GDCZIAM112233"
STRAY = "680f54c4-f418-419f-8ff3-367c89fa5835"

PASS = FAIL = 0


def check(label, cond, detail=""):
    global PASS, FAIL
    if cond:
        print(f"  ok   {label}")
        PASS += 1
    else:
        print(f"  FAIL {label}{('  — ' + str(detail)) if detail else ''}")
        FAIL += 1


def wipe():
    for path in (f"institutions/{REAL}", f"institutions/{STRAY}"):
        for sub in repair_tenant.SUBCOLLECTIONS:
            for d in db.collection(f"{path}/{sub}").stream():
                d.reference.delete()
        db.document(path).delete()
    for d in db.collection("users").stream():
        d.reference.delete()
    for u in fb_auth.list_users().iterate_all():
        fb_auth.delete_user(u.uid)


def seed():
    """The reported split: desktop on one tenant, phone on another."""
    wipe()
    db.document(f"institutions/{REAL}").set(
        {"institutionId": REAL, "name": "GDC Zia-ud-Din Sherpao", "status": "active"})

    # 3 books only the phone can see, under the real institution.
    for i in range(3):
        db.document(f"institutions/{REAL}/books/phone-{i}").set(
            {"syncId": f"phone-{i}", "title": f"Phone Book {i}", "deleted": False})
    # 5 books stranded under a UUID with NO institution document.
    for i in range(5):
        db.document(f"institutions/{STRAY}/books/desk-{i}").set(
            {"syncId": f"desk-{i}", "title": f"Desktop Book {i}", "deleted": False})
    db.document(f"institutions/{STRAY}/members/m-1").set({"syncId": "m-1", "name": "Ali"})
    # One record with the same id on both sides: the same record, synced twice.
    for tenant in (REAL, STRAY):
        db.document(f"institutions/{tenant}/books/shared-1").set(
            {"syncId": "shared-1", "title": f"Shared, {tenant} copy", "deleted": False})

    users = [
        ("librarian@gdc.pk", {"role": "admin", "institutionId": STRAY}, STRAY),
        ("staff@gdc.pk", {"role": "librarian", "institutionId": REAL}, REAL),
        ("officer@hed.gkp.pk", {"role": "directorate", "institutionId": None}, ""),
    ]
    uids = {}
    for email, claims, doc_inst in users:
        u = fb_auth.create_user(email=email, password="Passw0rd!")
        fb_auth.set_custom_user_claims(u.uid, claims)
        db.document(f"users/{u.uid}").set(
            {"uid": u.uid, "email": email, "role": claims["role"],
             "institutionId": doc_inst})
        uids[email] = u.uid
    return uids


def books(tenant):
    return {d.id for d in db.collection(f"institutions/{tenant}/books").stream()}


def run(*argv):
    sys.argv = ["repair_tenant.py", "--to", REAL, *argv]
    repair_tenant.main()


print("\n── Dry run changes nothing ──")
uids = seed()
before_real, before_stray = books(REAL), books(STRAY)
run()
check("the canonical tenant is untouched", books(REAL) == before_real)
check("the stray tenant is untouched", books(STRAY) == before_stray)
check("the misrouted account still carries the old claim",
      (fb_auth.get_user(uids["librarian@gdc.pk"]).custom_claims or {})
      .get("institutionId") == STRAY)

print("\n── Apply consolidates everything ──")
run("--apply")
after = books(REAL)
check("the 5 stranded books arrived", {f"desk-{i}" for i in range(5)} <= after,
      sorted(after))
check("the 3 that were already there survived", {f"phone-{i}" for i in range(3)} <= after)
check("nothing was duplicated", len(after) == 3 + 5 + 1, sorted(after))
check("a record present under both ids kept the canonical copy",
      db.document(f"institutions/{REAL}/books/shared-1").get().to_dict()["title"]
      == f"Shared, {REAL} copy")
check("members came across too",
      db.document(f"institutions/{REAL}/members/m-1").get().exists)

check("the misrouted account's profile was repointed",
      db.document(f"users/{uids['librarian@gdc.pk']}").get().to_dict()["institutionId"] == REAL)
check("and its custom claim, which is what actually decides access, was too",
      (fb_auth.get_user(uids["librarian@gdc.pk"]).custom_claims or {})
      .get("institutionId") == REAL)
check("its role was preserved",
      (fb_auth.get_user(uids["librarian@gdc.pk"]).custom_claims or {}).get("role") == "admin")
check("the account that was already correct is unchanged",
      db.document(f"users/{uids['staff@gdc.pk']}").get().to_dict()["institutionId"] == REAL)

# A directorate account is province-wide and deliberately unscoped. Giving it
# an institutionId would silently demote it to a single college.
d_claims = fb_auth.get_user(uids["officer@hed.gkp.pk"]).custom_claims or {}
check("the directorate account was NOT given an institution",
      not d_claims.get("institutionId"), d_claims)
check("and its role was left alone", d_claims.get("role") == "directorate")

print("\n── The source tenant survives until you ask for it to go ──")
check("the stray tenant still holds its records", len(books(STRAY)) == 6, sorted(books(STRAY)))
run("--apply", "--delete-source")
check("--delete-source removes it", len(books(STRAY)) == 0)
check("and the canonical tenant is intact", books(REAL) == after, sorted(books(REAL)))

print("\n── Re-running is safe ──")
run("--apply")
check("a second apply changes nothing", books(REAL) == after)

print("\n── A missing canonical institution is created ──")
wipe()
db.document(f"institutions/{STRAY}/books/only-1").set({"syncId": "only-1", "title": "X"})
check("the target does not exist yet", not db.document(f"institutions/{REAL}").get().exists)
run("--apply")
check("it was created", db.document(f"institutions/{REAL}").get().exists)
check("and the records landed in it", books(REAL) == {"only-1"})

wipe()
print(f"\n{PASS} passed, {FAIL} failed")
sys.exit(0 if FAIL == 0 else 1)
