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
