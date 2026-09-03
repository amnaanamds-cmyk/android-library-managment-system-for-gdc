"""
services/auth_service.py
Firebase Authentication using REST API (firebase-admin doesn't support client auth).
Roles are stored in Firestore users collection.
"Remember Me" uses Windows Credential Manager via keyring.
Offline Persistent Login: caches full user profile in local SQLite so the app
can start without internet after the first successful login.
"""
import json
import time
import logging
import requests
import keyring
from typing import Optional, Tuple

import config
from models.reservation import User

logger = logging.getLogger(__name__)

KEYRING_SERVICE = config.KEYRING_SERVICE_NAME
KEYRING_USER = "refresh_token"
KEYRING_INST_USER = "institution_id"  # persists institutionId across restarts
SIGN_IN_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key={}"
REFRESH_URL = "https://securetoken.googleapis.com/v1/token?key={}"


class AuthService:
    def __init__(self, firebase_service, db_helper=None):
        self.fb = firebase_service
        self._db_helper = db_helper  # set later by main.py after init
        self._current_user: Optional[User] = None
        self._id_token: Optional[str] = None
        self._token_expiry: float = 0

    def set_db_helper(self, db_helper):
        """Inject the DatabaseHelper reference (called from main.py)."""
        self._db_helper = db_helper

    @property
    def current_user(self) -> Optional[User]:
        return self._current_user

    @property
    def is_logged_in(self) -> bool:
        return self._current_user is not None

    @property
    def is_director(self) -> bool:
        return self._current_user is not None and self._current_user.role == "director"

    @property
    def is_admin_or_librarian(self) -> bool:
        return self._current_user is not None and self._current_user.role in ("admin", "librarian")

    @property
    def role(self) -> str:
        return self._current_user.role if self._current_user else ""

    @property
    def is_directorate_admin(self) -> bool:
        return self._current_user is not None and self._current_user.role in ("directorate_admin",)

    @property 
    def is_college_admin(self) -> bool:
        """College Admin = old 'director' or 'admin' roles."""
        return self._current_user is not None and self._current_user.role in (
            "college_admin", "director", "admin"
        )

    @property
    def is_librarian(self) -> bool:
        return self._current_user is not None and self._current_user.role in (
            "librarian", "admin", "college_admin", "director"
        )

    def can_manage_books(self) -> bool:
        return self.role in ("admin", "college_admin", "director", "librarian")

    def can_manage_members(self) -> bool:
        return self.role in ("admin", "college_admin", "director", "librarian")

    def can_access_settings(self) -> bool:
        return self.role in ("admin", "college_admin", "director")

    def can_view_reports(self) -> bool:
        return self.role in ("admin", "college_admin", "director", "directorate_admin")

    def can_view_directorate_dashboard(self) -> bool:
        return self.role in ("director", "college_admin", "directorate_admin")

    # ── Cache user profile to SQLite ─────────────────────────────────────────
    def _cache_session_locally(self):
        """Persist the current user profile into SQLite for offline restarts."""
        if self._db_helper and self._current_user:
            try:
                user = self._current_user
                self._db_helper.save_cached_session(
                    uid=user.uid,
                    email=user.email,
                    name=user.name,
                    role=user.role,
                    institution_id=user.institutionId,
                )
                logger.info("Cached user session locally for offline access.")
            except Exception as e:
                logger.warning(f"Failed to cache session locally: {e}")

    def _apply_institution(self, inst_id: str):
        """Inject the resolved institutionId into firebase_service and config."""
        if inst_id:
            self.fb.college_id = inst_id
            import config as _cfg
            _cfg.COLLEGE_ID = inst_id

    # ── Sign In ───────────────────────────────────────────────────────────────
    def sign_in(self, email: str, password: str,
                remember_me: bool = False) -> Tuple[bool, str]:
        """Sign in with email/password. Returns (success, error_msg)."""
        # Real Firebase login only — local bypasses removed to prevent
        # wrong institutionId being injected into the sync engine.
        if self.fb.mock_mode or not config.FIREBASE_WEB_API_KEY or config.FIREBASE_WEB_API_KEY == "your-firebase-web-api-key":
            return False, "Firebase not configured. Check your .env file and serviceAccountKey.json."

        try:
            resp = requests.post(
                SIGN_IN_URL.format(config.FIREBASE_WEB_API_KEY),
                json={"email": email, "password": password, "returnSecureToken": True},
                timeout=10,
            )
            data = resp.json()
            if "error" in data:
                return False, data["error"].get("message", "Login failed")

            self._id_token = data["idToken"]
            self._token_expiry = time.time() + int(data.get("expiresIn", 3600))
            uid = data["localId"]
            refresh_token = data.get("refreshToken", "")

            # Fetch role + institutionId from Firestore users collection
            if self.fb.db:
                user_doc = self.fb.db.collection("users").document(uid).get()
                if user_doc.exists:
                    user_data = user_doc.to_dict()
                    user_data["uid"] = uid
                    self._current_user = User.from_dict(user_data)
                else:
                    self._current_user = User(uid=uid, email=email, role="admin")
            else:
                self._current_user = User(uid=uid, email=email, role="admin")

            # Seed firebase_service with the resolved institutionId
            self._apply_institution(self._current_user.institutionId)

            # Always store refresh token securely for session restore
            if refresh_token:
                keyring.set_password(KEYRING_SERVICE, KEYRING_USER, refresh_token)
                keyring.set_password(KEYRING_SERVICE, KEYRING_INST_USER,
                                     self._current_user.institutionId)

            # Always cache the user profile locally for offline persistent login
            self._cache_session_locally()

            # If user explicitly unchecked "remember me", clear the keyring
            # (but keep the SQLite cache — they still get offline access)
            if not remember_me:
                try:
                    keyring.delete_password(KEYRING_SERVICE, KEYRING_USER)
                    keyring.delete_password(KEYRING_SERVICE, KEYRING_INST_USER)
                except Exception:
                    pass

            return True, ""

        except requests.exceptions.ConnectionError:
            return False, "No internet connection. Check your network."
        except Exception as e:
            return False, str(e)

    # ── Offline Session Restore (SQLite — no internet needed) ─────────────────
    def try_restore_offline_session(self) -> bool:
        """Try to restore the session from the local SQLite cache.
        This requires ZERO internet access — the user profile was cached
        during the last successful login."""
        if not self._db_helper:
            return False
        try:
            cached = self._db_helper.load_cached_session()
            if not cached:
                return False

            # Reconstruct the User from cached data
            self._current_user = User(
                uid=cached["uid"],
                email=cached["email"],
                name=cached.get("name", ""),
                role=cached["role"],
                institutionId=cached["institutionId"],
            )

            # Inject institutionId into firebase_service and config
            self._apply_institution(self._current_user.institutionId)

            logger.info(
                f"Restored offline session for {self._current_user.email} "
                f"(role={self._current_user.role}, "
                f"institution={self._current_user.institutionId})"
            )
            return True

        except Exception as e:
            logger.warning(f"Failed to restore offline session: {e}")
            return False

    # ── Remember Me Restore (online — uses refresh token) ─────────────────────
    def try_restore_session(self) -> bool:
        """Try to restore session from saved refresh token (Windows Credential Manager).
        This REQUIRES internet to exchange the refresh token for a new ID token.
        Falls back to offline SQLite cache if the network is unavailable."""

        # Step 1: Try fully-offline restore from SQLite cache FIRST
        if self.try_restore_offline_session():
            # Schedule a background token refresh (non-blocking) if possible
            self._try_background_refresh()
            return True

        # Step 2: Fall back to online refresh-token exchange
        try:
            refresh_token = keyring.get_password(KEYRING_SERVICE, KEYRING_USER)
            if not refresh_token or not config.FIREBASE_WEB_API_KEY:
                return False

            resp = requests.post(
                REFRESH_URL.format(config.FIREBASE_WEB_API_KEY),
                data={"grant_type": "refresh_token", "refresh_token": refresh_token},
                timeout=10,
            )
            data = resp.json()
            if "error" in data or "id_token" not in data:
                return False

            self._id_token = data["id_token"]
            self._token_expiry = time.time() + int(data.get("expires_in", 3600))
            uid = data.get("user_id", "")

            if self.fb.db:
                user_doc = self.fb.db.collection("users").document(uid).get()
                if user_doc.exists:
                    user_data = user_doc.to_dict()
                    user_data["uid"] = uid
                    self._current_user = User.from_dict(user_data)

                    # Restore institutionId into firebase_service
                    self._apply_institution(self._current_user.institutionId)

                    # Update the local cache with fresh data from Firestore
                    self._cache_session_locally()
                    return True
            else:
                # In mock mode, if we reach here we can't really restore a "real" session role
                # but we shouldn't crash.
                return False
            return False
        except requests.exceptions.ConnectionError:
            # Network unavailable — offline restore already tried above
            logger.info("No internet for token refresh; offline session already attempted.")
            return False
        except Exception:
            return False

    # ── Background Refresh (non-blocking) ────────────────────────────────────
    def _try_background_refresh(self):
        """Attempt to silently refresh the Firebase token and update the cached
        profile in the background. This runs after an offline session restore
        so the user is already logged in and the UI is responsive."""
        try:
            refresh_token = keyring.get_password(KEYRING_SERVICE, KEYRING_USER)
            if not refresh_token or not config.FIREBASE_WEB_API_KEY:
                return

            resp = requests.post(
                REFRESH_URL.format(config.FIREBASE_WEB_API_KEY),
                data={"grant_type": "refresh_token", "refresh_token": refresh_token},
                timeout=5,
            )
            data = resp.json()
            if "error" in data or "id_token" not in data:
                return

            self._id_token = data["id_token"]
            self._token_expiry = time.time() + int(data.get("expires_in", 3600))
            uid = data.get("user_id", "")

            # Re-fetch user profile from Firestore to pick up any role changes
            if self.fb.db:
                user_doc = self.fb.db.collection("users").document(uid).get()
                if user_doc.exists:
                    user_data = user_doc.to_dict()
                    user_data["uid"] = uid
                    self._current_user = User.from_dict(user_data)
                    self._apply_institution(self._current_user.institutionId)
                    self._cache_session_locally()
                    logger.info("Background token refresh + profile update succeeded.")

        except Exception as e:
            # Silently ignore — user is already logged in from the cache
            logger.debug(f"Background refresh failed (non-critical): {e}")

    # ── Sign Out ──────────────────────────────────────────────────────────────
    def sign_out(self):
        self._current_user = None
        self._id_token = None
        self._token_expiry = 0

        # Clear the local SQLite session cache
        if self._db_helper:
            try:
                self._db_helper.clear_cached_session()
                logger.info("Cleared cached offline session.")
            except Exception as e:
                logger.warning(f"Failed to clear cached session: {e}")

        # Clear persisted institutionId so next login starts fresh
        try:
            keyring.delete_password(KEYRING_SERVICE, KEYRING_INST_USER)
        except Exception:
            pass

    # ── Clear Saved Credentials ───────────────────────────────────────────────
    def clear_saved_credentials(self):
        try:
            keyring.delete_password(KEYRING_SERVICE, KEYRING_USER)
        except Exception:
            pass
        # Also clear the local session cache
        if self._db_helper:
            try:
                self._db_helper.clear_cached_session()
            except Exception:
                pass
