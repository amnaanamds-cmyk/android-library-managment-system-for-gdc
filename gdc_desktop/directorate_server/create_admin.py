"""
directorate_server/create_admin.py — create the first directorate admin
account interactively.

Replaces a startup-time auto-seed of director@gdc.edu / director123, a
real, publicly-documented password that every deployment carried until
reconfigured. Creating the first account is now something a person does
once, deliberately, typing their own password — never something that
happens silently because the users table was empty.

Run from this directory:  python create_admin.py
"""
import getpass
import sys

import database
import auth


def main():
    database.init_db()

    email = input("Admin email: ").strip()
    if not email or "@" not in email:
        print("That doesn't look like an email address. Aborting.")
        sys.exit(1)

    with database.get_db() as conn:
        existing = conn.execute(
            "SELECT email FROM directorate_users WHERE email = ?", (email,)
        ).fetchone()
        if existing:
            print(f"An account already exists for {email}. Use the API to change its password instead.")
            sys.exit(1)

    name = input("Full name: ").strip() or email

    password = getpass.getpass("Password (hidden): ")
    confirm = getpass.getpass("Confirm password: ")
    if password != confirm:
        print("Passwords did not match. Aborting.")
        sys.exit(1)
    if len(password) < 10:
        print("Use at least 10 characters — this account can read and manage every college's data.")
        sys.exit(1)

    p_hash = auth.get_password_hash(password)
    with database.get_db() as conn:
        conn.execute(
            "INSERT INTO directorate_users (email, password_hash, name, role) VALUES (?, ?, ?, ?)",
            (email, p_hash, name, "directorate_admin"),
        )
        conn.commit()

    print(f"\nCreated directorate_admin account for {email}. You can sign in now.")


if __name__ == "__main__":
    main()
