"""
scripts/manage_directorate.py — one-off admin CLI for the directorate rollout.

Run this once from a machine that has serviceAccountKey.json (the same file
gdc_desktop uses). It talks to Firebase directly via the Admin SDK, so it
bypasses Firestore Security Rules the same way the desktop app does — only
run it with a service account key you trust.

Usage (from the gdc_desktop/ directory):

    python scripts/manage_directorate.py list-users
    python scripts/manage_directorate.py list-colleges
    python scripts/manage_directorate.py create-directorate <email> <password>
    python scripts/manage_directorate.py promote <email>
    python scripts/manage_directorate.py demote <email>

"create-directorate" is the usual way to make your first (and any additional)
directorate-level account: it creates the Firebase Auth account and sets the
role in one step, with no signup needed. Use "promote" instead when the person
already has an account — sign them up normally in the desktop or web app
first, then flip their role. Nobody can grant themselves this role from the
app; that's enforced by firestore.rules, by design.

Do NOT promote a college's own owner/admin account: a directorate_admin can
only open the Directorate, Reports and Transfers screens, so that account
would lose access to Books, Members and Issue/Return.
"""
import argparse
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import config  # noqa: E402

try:
    import firebase_admin
    from firebase_admin import credentials, auth, firestore
except ImportError:
    print("firebase-admin is not installed. Run: pip install firebase-admin")
    sys.exit(1)


def init_admin():
    cred_path = Path(__file__).resolve().parent.parent / config.FIREBASE_CRED_PATH
    if not cred_path.exists():
        print(f"Service account key not found at: {cred_path}")
        print("Download it from Firebase Console -> Project Settings -> "
              "Service Accounts -> Generate new private key, and save it there.")
        sys.exit(1)
    cred = credentials.Certificate(str(cred_path))
    firebase_admin.initialize_app(cred)
    return firestore.client()


def list_users(db):
    users = list(db.collection("users").stream())
    if not users:
        print("No users found in Firestore yet.")
        return
    print(f"{'EMAIL':<40} {'ROLE':<18} {'INSTITUTION':<20} UID")
    print("-" * 100)
    for doc in users:
        d = doc.to_dict()
        print(f"{d.get('email', ''):<40} {d.get('role', ''):<18} "
              f"{d.get('institutionId', ''):<20} {doc.id}")


def list_colleges(db):
    colleges = list(db.collection("directorate_index").stream())
    if not colleges:
        print("No colleges have published to /directorate_index yet.")
        print("A college shows up here as soon as it's created in the desktop "
              "app's onboarding screen, or after its first sync.")
        return
    print(f"{'COLLEGE ID':<22} {'NAME':<32} {'BOOKS':>6} {'MEMBERS':>8} {'LAST SYNC (epoch ms)'}")
    print("-" * 100)
    for doc in colleges:
        d = doc.to_dict()
        print(f"{doc.id:<22} {d.get('name', ''):<32} {d.get('booksCount', 0):>6} "
              f"{d.get('membersCount', 0):>8} {d.get('lastSyncAt', '')}")
    print(f"\n{len(colleges)} college(s) registered.")


def create_institution(db, college_id: str, name: str, owner_email: str):
    """Create a fresh institution and attach an owner, in one step.

    The app UIs do the same thing across three different onboarding screens,
    each of which has had its own bug (Android omitted ownerUid; Android and
    web overwrote an existing role with "staff"). Doing it here writes all four
    documents consistently, from one place, with the Admin SDK.
    """
    import time

    college_id = college_id.strip().upper()
    if not re.match(r"^[A-Z0-9\-]+$", college_id):
        print("College ID may contain only letters, numbers and hyphens.")
        sys.exit(1)

    if db.collection("institutions").document(college_id).get().exists:
        print(f"REFUSED: institution '{college_id}' already exists.")
        print("Pick a different id — creating over an existing college would")
        print("mix two colleges' data together.")
        sys.exit(1)

    try:
        owner = auth.get_user_by_email(owner_email)
    except auth.UserNotFoundError:
        print(f"No Firebase Auth account exists for {owner_email}.")
        sys.exit(1)

    now = int(time.time() * 1000)

    # 1. The tenant root. ownerUid is what isInstitutionOwner() matches on.
    db.collection("institutions").document(college_id).set({
        "name": name,
        "inviteCode": college_id,
        "ownerUid": owner.uid,
        "createdAt": now,
    })

    # 2. The owner's profile — role and institutionId are what every app and
    #    every security rule reads to decide access.
    db.collection("users").document(owner.uid).set({
        "uid": owner.uid,
        "email": owner_email,
        "role": "owner",
        "institutionId": college_id,
    }, merge=True)

    # 3. Directorate registry, zeroed until the first real sync publishes counts.
    db.collection("directorate_index").document(college_id).set({
        "institutionId": college_id,
        "name": name,
        "booksCount": 0, "ebooksCount": 0, "membersCount": 0,
        "activeLoans": 0, "overdueCount": 0, "reservationsCount": 0,
        "finesOutstanding": 0.0,
        "lastSyncAt": now,
        "lastSyncPlatform": "admin-cli",
        "schemaVersion": 2,
    }, merge=True)

    # 4. Legacy mirror, for older builds that still resolve invite codes here.
    db.collection("colleges").document(college_id).set({
        "collegeId": college_id, "collegeName": name, "name": name,
        "ownerUid": owner.uid, "created_at": now,
        "booksCount": 0, "membersCount": 0, "circulationCount": 0,
    }, merge=True)

    print(f"Created institution '{college_id}' ({name}).")
    print(f"Owner: {owner_email} (uid={owner.uid})")
    print()
    print("Next: sign in on each app with that account. It should go straight")
    print("to the dashboard — no Create/Join screen — with 0 books and 0 members.")
    print("Clear each device's local data FIRST or it will push old records up.")


# Root collections this project owns. Anything not listed here is left alone.
ROOT_COLLECTIONS = [
    "users",
    "institutions",
    "directorate_index",
    "directorate_approvals",
    "colleges",
    "directors",
]


def _delete_collection(db, col_ref, batch_size: int = 300) -> int:
    """Delete every document in a collection, recursing into subcollections.

    Firestore does NOT delete subcollections when a parent document is
    deleted — the children become orphans that still exist and still count
    against reads. Every college's books/members/loans live in subcollections
    under /institutions/{id}, so a wipe that skipped recursion would leave the
    bulk of the data behind while appearing to succeed.
    """
    deleted = 0
    while True:
        docs = list(col_ref.limit(batch_size).stream())
        if not docs:
            return deleted
        for d in docs:
            for sub in d.reference.collections():
                deleted += _delete_collection(db, sub, batch_size)
        batch = db.batch()
        for d in docs:
            batch.delete(d.reference)
        batch.commit()
        deleted += len(docs)


def wipe_all(db, include_auth: bool):
    """Delete every Firestore document this project owns, after confirmation."""
    project_id = firebase_admin.get_app().project_id

    print("This will PERMANENTLY delete, from Firestore project "
          f"'{project_id}':")
    total = 0
    for name in ROOT_COLLECTIONS:
        n = len(list(db.collection(name).list_documents()))
        total += n
        print(f"    {name:<24} {n} document(s)"
              + ("  (plus all books/members/loans beneath them)"
                 if name == "institutions" else ""))

    auth_accounts = []
    if include_auth:
        auth_accounts = list(auth.list_users().iterate_all())
        print(f"    {'Firebase Auth accounts':<24} {len(auth_accounts)} account(s)")
        print("\n  Every college and directorate login will be destroyed —")
        print("  they will have to sign up again from scratch.")
    else:
        print("\n  Firebase Auth accounts will be KEPT (pass --include-auth to")
        print("  delete those too). Sign-ins survive; their profiles do not.")

    if total == 0 and not auth_accounts:
        print("\nNothing to delete.")
        return

    print("\nThere is no undo. Export a backup first if you have not.")
    typed = input(f"\nType the project id '{project_id}' to confirm: ").strip()
    if typed != project_id:
        print("Confirmation did not match — nothing was deleted.")
        sys.exit(1)

    print()
    for name in ROOT_COLLECTIONS:
        n = _delete_collection(db, db.collection(name))
        print(f"  deleted {n} document(s) from {name}")

    if include_auth:
        for u in auth_accounts:
            try:
                auth.delete_user(u.uid)
            except Exception as e:
                print(f"  could not delete auth account {u.email}: {e}")
        print(f"  deleted {len(auth_accounts)} Firebase Auth account(s)")

    print("\nFirestore is empty.")
    print("\nNext:")
    print("  1. Recreate the directorate account:")
    print("       python scripts/manage_directorate.py create-directorate <email> <password>")
    print("  2. Clear each device's LOCAL data, or it will push old records back up:")
    print("       desktop  — move %USERPROFILE%\\GDCLibrary50\\gdc_library.db* aside")
    print("       android  — uninstall the app (not just reinstall)")
    print("  3. Colleges then install the apps and register themselves.")


def list_auth(db):
    """List Firebase AUTH accounts, which are separate from the Firestore
    `users` profiles. If /users is wiped, the sign-in accounts usually survive
    here — this shows what is left to rebuild profiles for."""
    accounts = list(auth.list_users().iterate_all())
    if not accounts:
        print("No Firebase Auth accounts exist.")
        return
    print(f"{'EMAIL':<40} {'DISABLED':<10} UID")
    print("-" * 100)
    for u in accounts:
        print(f"{(u.email or '(no email)'):<40} {str(u.disabled):<10} {u.uid}")
    print(f"\n{len(accounts)} auth account(s). Profiles live separately in /users.")


def repair_user(db, email: str, role: str, college_id: str):
    """Rebuild a Firestore profile for an existing Auth account.

    Deleting the /users collection does not delete the Auth account, but it
    does strip the role and institutionId every app reads — which presents as
    'sync silently does nothing' and PERMISSION_DENIED, because the rules
    resolve both from this document.
    """
    try:
        user = auth.get_user_by_email(email)
    except auth.UserNotFoundError:
        print(f"No Firebase Auth account exists for {email}.")
        print("Nothing to repair — create it with create-directorate, or sign up in the app.")
        sys.exit(1)

    if role == "directorate_admin" and college_id:
        print("A directorate account belongs to no college; ignoring the college id.")
        college_id = ""

    db.collection("users").document(user.uid).set(
        {
            "uid": user.uid,
            "email": email,
            "role": role,
            "institutionId": college_id,
        },
        merge=True,
    )
    print(f"Restored profile for {email}: role='{role}', institutionId='{college_id}'.")
    print("Sign out and sign in again on every app so each one re-reads it.")


def set_institution(db, email: str, college_id: str):
    """Re-attach an account to a college. Repairs an account whose
    institutionId was cleared — without it every app signed in as that
    account loses its college and stops syncing entirely."""
    try:
        user = auth.get_user_by_email(email)
    except auth.UserNotFoundError:
        print(f"No Firebase Auth account exists for {email}.")
        sys.exit(1)
    db.collection("users").document(user.uid).set(
        {"institutionId": college_id}, merge=True
    )
    print(f"{email} is now attached to institution '{college_id}'.")
    print("Sign out and sign in again on each app so it picks up the change.")


def set_role(db, email: str, role: str, force: bool = False):
    try:
        user = auth.get_user_by_email(email)
    except auth.UserNotFoundError:
        print(f"No Firebase Auth account exists for {email} yet.")
        print("Have them sign up in the app first, then re-run this.")
        sys.exit(1)

    doc_ref = db.collection("users").document(user.uid)
    doc = doc_ref.get()

    # Promoting a college's own account detaches it from that college, which
    # stops every app signed in as it from syncing. Never do that silently.
    if role == "directorate_admin" and doc.exists and not force:
        existing = doc.to_dict().get("institutionId", "")
        if existing:
            print(f"REFUSED: {email} currently belongs to college '{existing}'.")
            print("Promoting it would clear that and every app signed in as this")
            print("account would stop syncing. Create a separate directorate")
            print("account instead:")
            print(f"    python scripts/manage_directorate.py create-directorate <new-email> <password>")
            print("If you really mean to detach this account, re-run with --force.")
            sys.exit(1)

    payload = {"role": role}
    if role == "directorate_admin":
        # A directorate account belongs to no college. Clearing this also
        # repairs an account that college onboarding attached to one.
        payload["institutionId"] = ""
    if doc.exists:
        doc_ref.set(payload, merge=True)
    else:
        doc_ref.set({"uid": user.uid, "email": email, **payload})
    print(f"{email} (uid={user.uid}) is now role='{role}'.")
    if role == "directorate_admin":
        print("They can now sign in to the web app at /director, or open the "
              "Directorate Dashboard screen in the desktop app.")


def create_directorate(db, email: str, password: str):
    """Create the Firebase Auth account AND grant it directorate_admin.

    "promote" only works on an account that already exists, which means
    signing up through an app first — but a directorate account belongs to no
    college, so the normal signup flow pushes it into college onboarding it
    should never complete. Creating it here with the Admin SDK skips that.
    """
    try:
        user = auth.get_user_by_email(email)
        print(f"Auth account already exists for {email} (uid={user.uid}); setting the role only.")
    except auth.UserNotFoundError:
        if len(password) < 6:
            print("Firebase requires a password of at least 6 characters.")
            sys.exit(1)
        user = auth.create_user(email=email, password=password)
        print(f"Created Firebase Auth account for {email} (uid={user.uid}).")

    # merge=True so an existing account keeps whatever else is on its profile,
    # but institutionId is cleared explicitly: a directorate account belongs to
    # no college, and leaving one set would also grant it that college's data.
    db.collection("users").document(user.uid).set(
        {"uid": user.uid, "email": email, "role": "directorate_admin", "institutionId": ""},
        merge=True,
    )
    print(f"{email} is now role='directorate_admin'.")
    print("Sign in with it on the web app at /director, or in the desktop app "
          "(the Directorate Dashboard opens automatically).")


def set_password(email: str, password: str):
    """Reset an account's password. Useful when a directorate account was
    created with a password that was mistyped — Firebase reports a wrong
    password and an unknown email identically, so a failed sign-in cannot
    tell you which one happened."""
    if len(password) < 6:
        print("Firebase requires a password of at least 6 characters.")
        sys.exit(1)
    try:
        user = auth.get_user_by_email(email)
    except auth.UserNotFoundError:
        print(f"No Firebase Auth account exists for {email}.")
        sys.exit(1)
    auth.update_user(user.uid, password=password)
    print(f"Password updated for {email} (uid={user.uid}). You can sign in with it now.")


def main():
    parser = argparse.ArgumentParser(description="NEXLIB directorate admin CLI")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("list-users")
    sub.add_parser("list-colleges")
    sub.add_parser("list-auth")
    p_wipe = sub.add_parser("wipe-all", help="DELETE all Firestore data for this project")
    p_wipe.add_argument("--include-auth", action="store_true",
                        help="Also delete every Firebase Auth account")
    p_ci = sub.add_parser("create-institution", help="Create a fresh college and attach an owner")
    p_ci.add_argument("college_id")
    p_ci.add_argument("name")
    p_ci.add_argument("owner_email")
    p_rep = sub.add_parser("repair-user", help="Rebuild a Firestore profile for an existing Auth account")
    p_rep.add_argument("email")
    p_rep.add_argument("role", help="owner | college_admin | librarian | staff | directorate_admin")
    p_rep.add_argument("college_id", nargs="?", default="", help="Institution id (omit for directorate accounts)")
    p_create = sub.add_parser(
        "create-directorate",
        help="Create a new account AND grant it directorate_admin (no signup needed)",
    )
    p_create.add_argument("email")
    p_create.add_argument("password")
    p_pw = sub.add_parser("set-password", help="Reset an existing account's password")
    p_pw.add_argument("email")
    p_pw.add_argument("password")
    p_inst = sub.add_parser("set-institution", help="Attach an account to a college (repairs a cleared institutionId)")
    p_inst.add_argument("email")
    p_inst.add_argument("college_id")
    p_promote = sub.add_parser("promote", help="Grant directorate_admin to an existing user")
    p_promote.add_argument("email")
    p_promote.add_argument("--force", action="store_true",
                           help="Promote even if it detaches the account from its college")
    p_demote = sub.add_parser("demote", help="Revert a user to a plain college role")
    p_demote.add_argument("email")
    p_demote.add_argument("--role", default="staff", help="Role to set instead (default: staff)")

    args = parser.parse_args()
    db = init_admin()

    if args.command == "list-users":
        list_users(db)
    elif args.command == "list-colleges":
        list_colleges(db)
    elif args.command == "create-directorate":
        create_directorate(db, args.email, args.password)
    elif args.command == "set-password":
        set_password(args.email, args.password)
    elif args.command == "create-institution":
        create_institution(db, args.college_id, args.name, args.owner_email)
    elif args.command == "wipe-all":
        wipe_all(db, args.include_auth)
    elif args.command == "list-auth":
        list_auth(db)
    elif args.command == "repair-user":
        repair_user(db, args.email, args.role, args.college_id)
    elif args.command == "set-institution":
        set_institution(db, args.email, args.college_id)
    elif args.command == "promote":
        set_role(db, args.email, "directorate_admin", force=args.force)
    elif args.command == "demote":
        set_role(db, args.email, args.role)


if __name__ == "__main__":
    main()
