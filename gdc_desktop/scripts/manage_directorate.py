"""
scripts/manage_directorate.py — one-off admin CLI for the directorate rollout.

Run this once from a machine that has serviceAccountKey.json (the same file
gdc_desktop uses). It talks to Firebase directly via the Admin SDK, so it
bypasses Firestore Security Rules the same way the desktop app does — only
run it with a service account key you trust.

Usage (from the gdc_desktop/ directory):

    python scripts/manage_directorate.py list-users
    python scripts/manage_directorate.py list-colleges
    python scripts/manage_directorate.py promote <email>
    python scripts/manage_directorate.py demote <email>

"promote" is how you create your first (and any additional) directorate-level
account: sign the person up normally in the desktop or web app first (so
Firebase Auth + their users/{uid} doc exist), then run this to flip their
role to "directorate_admin". Nobody can grant themselves this role from the
app — that's enforced by firestore.rules, by design.
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


def set_role(db, email: str, role: str):
    try:
        user = auth.get_user_by_email(email)
    except auth.UserNotFoundError:
        print(f"No Firebase Auth account exists for {email} yet.")
        print("Have them sign up in the app first, then re-run this.")
        sys.exit(1)

    doc_ref = db.collection("users").document(user.uid)
    doc = doc_ref.get()
    if doc.exists:
        doc_ref.set({"role": role}, merge=True)
    else:
        doc_ref.set({"uid": user.uid, "email": email, "role": role})
    print(f"{email} (uid={user.uid}) is now role='{role}'.")
    if role == "directorate_admin":
        print("They can now sign in to the web app at /director, or open the "
              "Directorate Dashboard screen in the desktop app.")


def main():
    parser = argparse.ArgumentParser(description="NEXLIB directorate admin CLI")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("list-users")
    sub.add_parser("list-colleges")
    p_promote = sub.add_parser("promote", help="Grant directorate_admin to an existing user")
    p_promote.add_argument("email")
    p_demote = sub.add_parser("demote", help="Revert a user to a plain college role")
    p_demote.add_argument("email")
    p_demote.add_argument("--role", default="staff", help="Role to set instead (default: staff)")

    args = parser.parse_args()
    db = init_admin()

    if args.command == "list-users":
        list_users(db)
    elif args.command == "list-colleges":
        list_colleges(db)
    elif args.command == "promote":
        set_role(db, args.email, "directorate_admin")
    elif args.command == "demote":
        set_role(db, args.email, args.role)


if __name__ == "__main__":
    main()
