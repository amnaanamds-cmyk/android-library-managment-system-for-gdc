import os
import time
from collections import defaultdict, deque

import bcrypt
from jose import jwt, JWTError
from datetime import datetime, timedelta
from fastapi import HTTPException, Security, Depends
from fastapi.security import APIKeyHeader, OAuth2PasswordBearer

import database

# No hardcoded fallback. A guessable, publicly-committed secret lets anyone
# who has read this source forge a valid directorate-admin token for every
# endpoint in this server — which is exactly what the fallback used to be
# ("super-secret-directorate-key-for-gdc-management-system-2026", visible
# to anyone with repo access, used by default on every deployment that
# never set JWT_SECRET — which .env.example never even mentioned). Refusing
# to start without a real secret is the fix; a convenient default here is
# not a convenience, it is the vulnerability.
SECRET_KEY = os.getenv("JWT_SECRET")
if not SECRET_KEY:
    raise RuntimeError(
        "JWT_SECRET is not set. This server refuses to start with a default or missing "
        "secret, because that secret is what every directorate-admin token is signed with — "
        "a guessable one lets anyone forge admin access. Set a long, random JWT_SECRET in "
        "your environment (e.g. `python -c \"import secrets; print(secrets.token_hex(32))\"`) "
        "before starting this server."
    )

ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_MINUTES = 1440  # 24 hours

api_key_header = APIKeyHeader(name="X-College-API-Key", auto_error=False)
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/auth/login", auto_error=False)

# requirements.txt listed passlib[bcrypt], but verify_password/
# get_password_hash implemented unsalted-per-iteration SHA-256 by hand
# instead and never used it — brute-forceable at speed, where bcrypt's
# whole purpose is a tunable, deliberately slow work factor. Using the
# bcrypt package directly rather than through passlib's CryptContext:
# passlib has been unmaintained since 2020 and its bcrypt backend detection
# breaks outright against current bcrypt releases (it probes
# bcrypt.__about__.__version__, removed in bcrypt 4.1+) — confirmed by
# actually running this against a fresh `pip install`, which is exactly
# what a real deployment does. One fewer unmaintained dependency to break
# again later.
_BCRYPT_MAX_BYTES = 72  # bcrypt's own hard input limit


def verify_password(plain_password: str, hashed_password: str) -> bool:
    try:
        return bcrypt.checkpw(
            plain_password.encode("utf-8")[:_BCRYPT_MAX_BYTES],
            hashed_password.encode("utf-8"),
        )
    except (ValueError, TypeError):
        return False


def get_password_hash(password: str) -> str:
    return bcrypt.hashpw(
        password.encode("utf-8")[:_BCRYPT_MAX_BYTES], bcrypt.gensalt()
    ).decode("utf-8")


def create_access_token(data: dict):
    to_encode = data.copy()
    if "role" not in to_encode:
        raise ValueError("create_access_token requires a role claim — see get_current_user for why.")
    expire = datetime.utcnow() + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    to_encode.update({"exp": expire})
    return jwt.encode(to_encode, SECRET_KEY, algorithm=ALGORITHM)


def verify_api_key(college_id: str, api_key: str) -> bool:
    if not college_id or not api_key:
        return False
    with database.get_db() as conn:
        row = conn.execute("SELECT api_key FROM colleges WHERE id = ?", (college_id,)).fetchone()
        return bool(row and row["api_key"] == api_key)


def get_current_user(token: str = Depends(oauth2_scheme)):
    if not token:
        raise HTTPException(status_code=401, detail="Not authenticated")
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        email: str = payload.get("sub")
        role = payload.get("role")
        # A token with no role claim used to be treated as directorate_admin
        # — the HIGHEST privilege, on the theory that an absent claim is a
        # safe default. It is the opposite: any token missing this claim
        # (forged, truncated, or from a future endpoint that forgets to set
        # it) silently became full admin. Absence must deny, never grant.
        if email is None or role is None:
            raise HTTPException(status_code=401, detail="Invalid token")
        return {"email": email, "role": role}
    except JWTError:
        raise HTTPException(status_code=401, detail="Invalid token")


def require_college_api_key(college_id: str, x_college_api_key: str = Security(api_key_header)) -> str:
    """FastAPI dependency: proves the caller controls `college_id` specifically
    — not just that they hold *some* valid key. Mirrors the check /api/sync
    already did correctly; the transfer endpoints below had none at all."""
    if not x_college_api_key or not verify_api_key(college_id, x_college_api_key):
        raise HTTPException(status_code=403, detail="Invalid College ID or API Key")
    return college_id


# ── Minimal login rate limiting ─────────────────────────────────────────
#
# /api/auth/login had no throttling at all — brute-forceable with no
# lockout. No new dependency pulled in for this; an in-memory sliding
# window is enough for a service this size, and is intentionally simple
# enough to read and trust rather than reaching for a library.
_LOGIN_WINDOW_SECONDS = 300
_LOGIN_MAX_ATTEMPTS = 8
_login_attempts: dict[str, deque] = defaultdict(deque)


def check_login_rate_limit(key: str):
    """Raises 429 if `key` (e.g. client IP) has failed login too many times
    recently. Call check_login_rate_limit() before verifying credentials,
    and record_login_failure()/record_login_success() after."""
    now = time.monotonic()
    attempts = _login_attempts[key]
    while attempts and now - attempts[0] > _LOGIN_WINDOW_SECONDS:
        attempts.popleft()
    if len(attempts) >= _LOGIN_MAX_ATTEMPTS:
        raise HTTPException(status_code=429, detail="Too many login attempts. Try again in a few minutes.")


def record_login_failure(key: str):
    _login_attempts[key].append(time.monotonic())


def record_login_success(key: str):
    _login_attempts.pop(key, None)
