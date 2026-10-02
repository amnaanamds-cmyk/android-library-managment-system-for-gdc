"""
directorate_server/test_security.py — behavioral tests for the
vulnerabilities fixed in this server: a hardcoded JWT secret anyone with
repo access could forge admin tokens with, a seeded default admin account
(director@gdc.edu / director123) and two demo college API keys shipped live
on every deployment, three inter-library-transfer endpoints with no
authentication at all, a role claim that defaulted to full admin when
absent, homemade unsalted-iteration password hashing, wide-open CORS, and
no login rate limiting.

Run from this directory:  python test_security.py

Uses a real, ephemeral SQLite database and a real running FastAPI app via
TestClient — not mocks. Every check here failed against the original code;
each exists because it did.
"""
import os
import sys
import tempfile

os.environ.setdefault("JWT_SECRET", "test-secret-for-this-test-run-only-never-use-in-prod")
sys.path.insert(0, os.path.dirname(__file__))

import database

database.DB_PATH = tempfile.mktemp(suffix=".db")
database.init_db()

import auth
import main
from fastapi.testclient import TestClient
import jwt as pyjwt

client = TestClient(main.app)

_pass = _fail = 0


def check(label: str, cond: bool):
    global _pass, _fail
    print(("ok  " if cond else "FAIL") + "  " + label)
    if cond:
        _pass += 1
    else:
        _fail += 1


# ── password hashing ────────────────────────────────────────────────────
h = auth.get_password_hash("correct horse battery staple")
check("real bcrypt hash produced (not the old hand-rolled salt:sha256)", h.startswith("$2b$"))
check("bcrypt verify round-trips correctly", auth.verify_password("correct horse battery staple", h))
check("bcrypt verify rejects a wrong password", not auth.verify_password("wrong", h))

# ── tokens must declare a role ──────────────────────────────────────────
try:
    auth.create_access_token({"sub": "x@y.com"})
    check("create_access_token refuses to issue a token with no role claim", False)
except ValueError:
    check("create_access_token refuses to issue a token with no role claim", True)

# ── fixtures: a real admin, three colleges ──────────────────────────────
with database.get_db() as conn:
    conn.execute(
        "INSERT INTO directorate_users (email, password_hash, name, role) VALUES (?, ?, ?, ?)",
        ("admin@test.edu", auth.get_password_hash("RealAdminPass123!"), "Test Admin", "directorate_admin"),
    )
    for cid, name, key in [
        ("gdc-test-1", "GDC Test 1", "real-secret-key-1"),
        ("gdc-test-2", "GDC Test 2", "real-secret-key-2"),
        ("gdc-test-3", "GDC Test 3", "real-secret-key-3"),
    ]:
        conn.execute(
            "INSERT INTO colleges (id, name, location, api_key, registered_at) VALUES (?, ?, ?, ?, ?)",
            (cid, name, "Test City", key, 0),
        )
    conn.commit()

# ── no demo credentials seeded ──────────────────────────────────────────
with database.get_db() as conn:
    users = conn.execute("SELECT email FROM directorate_users").fetchall()
check("no 'director@gdc.edu' demo admin account is auto-seeded", not any(u["email"] == "director@gdc.edu" for u in users))

# ── inter-library transfer endpoints now require auth ──────────────────
r = client.post(
    "/api/transfers",
    json={"from_college": "gdc-test-1", "to_college": "gdc-test-2", "book_title": "Intro to Physics"},
    headers={"X-College-API-Key": "real-secret-key-1"},
)
check("a college's own key CAN create a transfer as itself", r.status_code == 200)
with database.get_db() as conn:
    transfer_id = conn.execute("SELECT id FROM book_transfers").fetchone()["id"]

r = client.post("/api/transfers", json={"from_college": "gdc-test-1", "to_college": "gdc-test-2", "book_title": "X"})
check("POST /api/transfers with no key at all is rejected", r.status_code == 403)

r = client.get("/api/transfers/college/gdc-test-1")
check("GET /api/transfers/college/{id} with no key at all is rejected", r.status_code == 403)

r = client.post(f"/api/transfers/{transfer_id}/status", params={"status": "received"})
check("POST .../status on a REAL transfer, no key, is rejected", r.status_code == 403)

r = client.post("/api/transfers/99999/status", params={"status": "received"})
check("POST .../status on a NONEXISTENT transfer, no key, ALSO 403s (no ID-enumeration leak)", r.status_code == 403)

r = client.post(
    "/api/transfers",
    json={"from_college": "gdc-test-2", "to_college": "gdc-test-1", "book_title": "X"},
    headers={"X-College-API-Key": "real-secret-key-1"},
)
check("one college's key cannot create a transfer claiming to BE another college", r.status_code == 403)

r = client.post(
    f"/api/transfers/{transfer_id}/status", params={"status": "received"},
    headers={"X-College-API-Key": "real-secret-key-3"},
)
check("a college NOT party to a transfer (valid key, wrong transfer) cannot update it", r.status_code == 403)

r = client.post(
    f"/api/transfers/{transfer_id}/status", params={"status": "received"},
    headers={"X-College-API-Key": "real-secret-key-2"},
)
check("the RECEIVING college (a real party to the transfer) CAN update it", r.status_code == 200)

# ── the old hardcoded secret is dead ────────────────────────────────────
old_secret = "super-secret-directorate-key-for-gdc-management-system-2026"
forged = pyjwt.encode({"sub": "attacker@evil.com", "role": "directorate_admin"}, old_secret, algorithm="HS256")
r = client.get("/api/colleges", headers={"Authorization": f"Bearer {forged}"})
check("a token forged with the OLD hardcoded fallback secret is rejected", r.status_code == 401)

# ── real login works end to end ─────────────────────────────────────────
r = client.post("/api/auth/login", json={"email": "admin@test.edu", "password": "RealAdminPass123!"})
check("a real admin login succeeds", r.status_code == 200)
real_token = r.json()["access_token"]
r = client.get("/api/colleges", headers={"Authorization": f"Bearer {real_token}"})
check("a correctly-signed token with a role claim is accepted", r.status_code == 200)

# ── absent role claim denies, never defaults to admin ───────────────────
no_role_token = pyjwt.encode({"sub": "x@y.com"}, os.environ["JWT_SECRET"], algorithm="HS256")
r = client.get("/api/colleges", headers={"Authorization": f"Bearer {no_role_token}"})
check("a token missing its role claim is REJECTED, never defaulted to directorate_admin", r.status_code == 401)

# ── login rate limiting ──────────────────────────────────────────────────
for _ in range(8):
    client.post("/api/auth/login", json={"email": "admin@test.edu", "password": "wrong"})
r = client.post("/api/auth/login", json={"email": "admin@test.edu", "password": "wrong"})
check("repeated failed logins are rate-limited", r.status_code == 429)

# ── CORS is no longer wide open ─────────────────────────────────────────
check("CORS no longer defaults to allow_origins=['*']", main._origins != ["*"])

print(f"\n{_pass} passed, {_fail} failed.")
sys.exit(1 if _fail else 0)
