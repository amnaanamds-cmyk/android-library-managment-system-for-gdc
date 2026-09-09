"""
Put every account and every record under ONE institution.

Run tools/diagnose_sync.py first. If it reports accounts split across several
institutionIds, or data sitting under an id with no institution document, this
script consolidates them. That situation is the usual cause of "sync is not
working": every client is syncing perfectly, into a tenant the others never
look at.

    cd gdc_desktop
    python ../tools/repair_tenant.py --to GDCZIAM112233            # dry run
    python ../tools/repair_tenant.py --to GDCZIAM112233 --apply

It is a DRY RUN unless you pass --apply. The dry run prints exactly what would
change, and changes nothing.

What it does, in order:

  1. Creates the canonical institution document if it does not exist.
  2. Copies every record from the other tenants into the canonical one, keeping
     each document's id — which is its syncId — so a record that is already
     there is recognised rather than duplicated.
  3. Repoints every account: users/{uid}.institutionId and, where a custom
     claim carries one, the claim too. Claims win at runtime, so leaving those
     behind would undo the whole exercise.
  4. Tells you what to put in gdc_desktop/.env.

What it does NOT do:

  * It never deletes the source tenants unless you pass --delete-source. A
    consolidation you got wrong is recoverable only while the originals exist,
    so verify the apps first and clean up afterwards.
  * It never touches accounts whose role is `directorate`. Those are
    province-wide and deliberately have no institutionId; giving them one
    would scope them to a single college.
  * It never overwrites a record that already exists in the canonical tenant
    unless you pass --overwrite. Same id on both sides means the same record
    synced both ways; the canonical copy is the one clients have been reading.

Needs serviceAccountKey.json and .env, the same two files the desktop needs.
"""
import argparse
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

# Every per-institution subcollection. Missing one here would strand its
# records under the old tenant, so this list must stay in step with
# firestore.rules and gdc_desktop/services/firebase_service.py.
SUBCOLLECTIONS = (
    "books",
    "ebooks",
    "members",
    "issued_books",
    "reservations",
    "settings",
    "audit_log",
    "sync_conflicts",
)

# Firestore caps a batched write at 500 operations.
BATCH_LIMIT = 400


def bold(s):
    return f"\033[1m{s}\033[0m"


def rule(title):
    print(f"\n{bold(title)}\n" + "─" * len(title))


def connect(cred_path):
    """Firestore client. Reuses an app if one is already initialised.

    With FIRESTORE_EMULATOR_HOST set, no service account is needed and none is
    used — which is how this script is tested, and how an administrator can
    rehearse a consolidation against an emulator before touching a live
    project.
    """
    if not firebase_admin._apps:
        if os.environ.get("FIRESTORE_EMULATOR_HOST"):
            firebase_admin.initialize_app(
                _EmulatorCredential(),
                {"projectId": os.environ.get("GCLOUD_PROJECT", "nexlib-emulator")},
            )
        else:
            firebase_admin.initialize_app(credentials.Certificate(cred_path))
    return firestore.client()


class _EmulatorCredential(credentials.Base):
    """No-op credential. The emulators do not authenticate."""

    def get_credential(self):
        from google.auth.credentials import AnonymousCredentials

        return AnonymousCredentials()


def find_tenants_with_data(db):
    """Every institution id that has records, whether or not it has a document.

    Subcollections exist independently of their parent document, so data can
    sit under an id that appears nowhere in the institutions listing. Those are
    precisely the tenants a split has stranded records in.
    """
    found = defaultdict(lambda: defaultdict(int))
    for group in SUBCOLLECTIONS:
        try:
            for d in db.collection_group(group).limit(20000).stream():
                parts = d.reference.path.split("/")
                if len(parts) >= 4 and parts[0] == "institutions":
                    found[parts[1]][group] += 1
        except Exception as exc:
            print(f"  (could not scan {group}: {exc})")
    return found


def copy_tenant(db, source, target, apply_changes, overwrite):
    """Copy every record from one institution to another, keeping document ids."""
    moved, skipped = defaultdict(int), defaultdict(int)
    for sub in SUBCOLLECTIONS:
        src = db.collection(f"institutions/{source}/{sub}")
        dst = db.collection(f"institutions/{target}/{sub}")
        batch, pending = db.batch(), 0
        for doc in src.stream():
            if not overwrite and dst.document(doc.id).get().exists:
                # Same id on both sides is the same record, already synced.
                skipped[sub] += 1
                continue
            moved[sub] += 1
            if not apply_changes:
                continue
            batch.set(dst.document(doc.id), doc.to_dict())
            pending += 1
            if pending >= BATCH_LIMIT:
                batch.commit()
                batch, pending = db.batch(), 0
        if apply_changes and pending:
            batch.commit()
    return moved, skipped


def main():
    ap = argparse.ArgumentParser(
        description="Consolidate every account and record under one institution.",
    )
    ap.add_argument("--to", required=True, metavar="INSTITUTION_ID",
                    help="the canonical institutionId everything should end up under")
    ap.add_argument("--apply", action="store_true",
                    help="actually write. Without this it is a dry run.")
    ap.add_argument("--overwrite", action="store_true",
                    help="overwrite records that already exist in the target")
    ap.add_argument("--delete-source", action="store_true",
                    help="delete the source tenants after copying. Verify the apps first.")
    args = ap.parse_args()

    on_emulator = bool(os.environ.get("FIRESTORE_EMULATOR_HOST"))
    cred_path = ""
    if not on_emulator:
        try:
            import config
        except Exception as exc:
            sys.exit(f"Could not load gdc_desktop/config.py: {exc}")

        cred_path = config.FIREBASE_CRED_PATH
        if not os.path.isabs(cred_path):
            cred_path = os.path.join(DESKTOP, cred_path)
        if not os.path.exists(cred_path):
            sys.exit(
                f"Service account key not found at {cred_path}\n"
                "Firebase Console -> Project Settings -> Service Accounts -> "
                "Generate new private key."
            )

    db = connect(cred_path)
    target = args.to

    print(bold("\nNEXLIB tenant repair"))
    if on_emulator:
        print(f"Target                : EMULATOR at {os.environ['FIRESTORE_EMULATOR_HOST']}")
    print(f"Canonical institution : {target}")
    print(f"Mode                  : {bold('APPLY — this will write') if args.apply else 'dry run (nothing will be written)'}")

    # ── 1. The canonical institution has to exist ────────────────────────────
    rule("1. Canonical institution")
    target_ref = db.document(f"institutions/{target}")
    if target_ref.get().exists:
        print(f"  institutions/{target} exists.")
    else:
        print(f"  institutions/{target} does NOT exist — it will be created.")
        if args.apply:
            target_ref.set({"institutionId": target, "name": target, "status": "active"},
                           merge=True)
            print("  created.")

    # ── 2. Move stranded records ─────────────────────────────────────────────
    rule("2. Records under other institutions")
    tenants = find_tenants_with_data(db)
    sources = [t for t in tenants if t != target]

    if not sources:
        print("  None. Every record is already under the canonical institution.")
    for source in sorted(sources):
        counts = "  ".join(f"{k}={v}" for k, v in sorted(tenants[source].items()))
        print(f"\n  {bold(source)}  ({counts})")
        moved, skipped = copy_tenant(db, source, target, args.apply, args.overwrite)
        verb = "copied" if args.apply else "would copy"
        for sub in SUBCOLLECTIONS:
            if moved[sub] or skipped[sub]:
                note = f" ({skipped[sub]} already in the target, left alone)" if skipped[sub] else ""
                print(f"      {sub:<16} {verb} {moved[sub]}{note}")

        if args.delete_source:
            print(f"      {'deleting' if args.apply else 'would delete'} institutions/{source}")
            if args.apply:
                for sub in SUBCOLLECTIONS:
                    col = db.collection(f"institutions/{source}/{sub}")
                    batch, pending = db.batch(), 0
                    for doc in col.stream():
                        batch.delete(doc.reference)
                        pending += 1
                        if pending >= BATCH_LIMIT:
                            batch.commit()
                            batch, pending = db.batch(), 0
                    if pending:
                        batch.commit()
                db.document(f"institutions/{source}").delete()

    # ── 3. Repoint accounts ──────────────────────────────────────────────────
    rule("3. Accounts")
    profiles = {d.id: (d.to_dict() or {}) for d in db.collection("users").stream()}

    users = []
    try:
        page = fb_auth.list_users()
        while page:
            users.extend(page.users)
            page = page.get_next_page()
    except Exception as exc:
        print(f"  Could not list auth users ({exc}). Custom claims cannot be repaired,")
        print("  and claims win at runtime — fix them in the Firebase console.")

    auth_by_uid = {u.uid: u for u in users}
    touched = 0
    for uid in sorted(set(profiles) | set(auth_by_uid)):
        prof = profiles.get(uid, {})
        user = auth_by_uid.get(uid)
        claims = (user.custom_claims or {}) if user else {}
        role = claims.get("role") or prof.get("role") or ""
        email = (user.email if user else prof.get("email")) or "(no email)"

        if role == "directorate":
            # Province-wide by design, and deliberately unscoped.
            print(f"  {email:<36} directorate — left alone")
            continue

        doc_inst = prof.get("institutionId") or prof.get("collegeId") or ""
        claim_inst = claims.get("institutionId") or ""
        if doc_inst == target and claim_inst in ("", target):
            continue

        touched += 1
        print(f"  {email:<36} {doc_inst or '(none)'} -> {target}")
        if claim_inst and claim_inst != target:
            print(f"      custom claim institutionId {claim_inst} -> {target}"
                  "   (claims win at runtime, so this one matters most)")
        if args.apply:
            db.document(f"users/{uid}").set(
                {"uid": uid, "institutionId": target}, merge=True)
            if user is not None:
                fb_auth.set_custom_user_claims(
                    uid, {**claims, "institutionId": target, "role": role or "staff"})

    if touched == 0:
        print("  Every account already resolves to the canonical institution.")

    # ── 4. What is left for you ──────────────────────────────────────────────
    rule("4. Finish up")
    print(f"  gdc_desktop/.env       COLLEGE_ID={target}")
    if not args.apply:
        print("\n  Nothing was written. Re-run with --apply to make these changes.")
    else:
        print("\n  Written. Every client must sign out and sign back in — a custom")
        print("  claim is baked into the ID token and does not change until the")
        print("  token is refreshed.")
        if sources and not args.delete_source:
            print("\n  The old tenants were left in place. Once the apps agree, re-run")
            print("  with --delete-source to remove them.")
    print()


if __name__ == "__main__":
    main()
