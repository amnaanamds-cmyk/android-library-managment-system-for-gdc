"""
services/storage_service.py — real file storage for digital resources and
institutional logos, via Firebase Cloud Storage.

Before this existed, "uploading" a digital book or a college logo did
neither: digital_library_screen.py's upload handler took a local file
picker result and saved the PATH ON THAT ONE PC as if it were a shareable
URL — the literal code comment called it "Simple simulation: just create a
book record". A book "uploaded" on one desktop was invisible, broken, on
every other device, every other college, and the directorate. This module
is what actually puts the bytes somewhere every authorized device can
reach them.

Capacity, honestly: Firebase's free Spark plan gives 5 GB of Storage,
province-wide, shared by every college on this deployment combined — not
per college. MAX_UPLOAD_MB below is a conservative per-file ceiling chosen
against that: at 10 MB/file, the province-wide bucket holds roughly 500
files total before someone has to delete something or the project moves to
a paid plan. There is no running total enforced here (that would need a
Storage read on every upload attempt, adding cost to prevent cost) — this
is a sanity cap on any one file, not a guarantee the bucket has room.

Security model: files are stored PRIVATE under institutions/{college_id}/...,
never made public. A public URL would let anyone with the link read a
tenant's file regardless of which college they belong to — exactly the
boundary this project's security rules otherwise enforce for every other
piece of tenant data. The desktop app already holds Admin SDK privileges
(see SYNC_ARCHITECTURE.md / the research paper's note on its asymmetric
trust boundary), so it generates short-lived signed URLs on demand instead
of relying on Storage Security Rules the way a future Android/web reader
of these same files eventually will.
"""
from datetime import timedelta
from typing import Optional, Tuple

from firebase_admin import storage

MAX_UPLOAD_MB = 10
MAX_UPLOAD_BYTES = MAX_UPLOAD_MB * 1024 * 1024


class StorageService:
    def __init__(self, firebase_service):
        self.fb = firebase_service

    def _bucket(self):
        if self.fb.mock_mode or not self.fb.college_id:
            return None
        try:
            return storage.bucket()
        except Exception as e:
            print(f"Could not reach Firebase Storage: {e}")
            return None

    def upload(self, local_path: str, dest_subpath: str) -> Tuple[bool, str]:
        """Upload local_path to institutions/{college_id}/{dest_subpath}.

        Returns (True, storage_path) on success — storage_path is what
        should be saved as digitalUrl/logo_url, NOT a public URL, since the
        file is private. Returns (False, error_message) on failure,
        including the "not connected" case, which is the common one for an
        offline-first desktop app on a rural connection.
        """
        bucket = self._bucket()
        if bucket is None:
            return False, "Not connected to the cloud right now (offline, or not signed in). Try again once connected."

        storage_path = f"institutions/{self.fb.college_id}/{dest_subpath}"
        try:
            blob = bucket.blob(storage_path)
            blob.upload_from_filename(local_path)
            return True, storage_path
        except Exception as e:
            return False, str(e)

    def signed_url(self, storage_path: str, expires_minutes: int = 60) -> Optional[str]:
        """A short-lived, authenticated download link for a private file.
        None if the bucket isn't reachable or the file doesn't exist."""
        bucket = self._bucket()
        if bucket is None:
            return None
        try:
            blob = bucket.blob(storage_path)
            if not blob.exists():
                return None
            return blob.generate_signed_url(expiration=timedelta(minutes=expires_minutes))
        except Exception as e:
            print(f"Could not generate a download link: {e}")
            return None

    def delete(self, storage_path: str) -> bool:
        bucket = self._bucket()
        if bucket is None:
            return False
        try:
            blob = bucket.blob(storage_path)
            if blob.exists():
                blob.delete()
            return True
        except Exception as e:
            print(f"Could not delete {storage_path}: {e}")
            return False


def looks_like_storage_path(value: str) -> bool:
    """True for institutions/{id}/... paths this module wrote. False for a
    plain http(s) URL or a bare local filesystem path — the three shapes
    digitalUrl/logo_url can hold, including from before this file existed."""
    return bool(value) and value.startswith("institutions/") and not value.startswith("http")
