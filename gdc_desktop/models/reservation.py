"""
models/reservation.py — Reservation data model matching Android/Firestore schema.
"""
import uuid
import time
from dataclasses import dataclass
from typing import Optional


@dataclass
class Reservation:
    syncId: str = ""
    id: int = 0
    bookId: int = 0
    bookTitle: str = ""
    memberId: int = 0
    memberName: str = ""
    reservedDate: str = ""
    status: str = "Pending"   # "Pending" or "Fulfilled"
    notifiedDate: Optional[str] = None
    lastUpdated: int = 0
    deleted: bool = False

    def __post_init__(self):
        if not self.syncId:
            self.syncId = str(uuid.uuid4())
        if not self.lastUpdated:
            self.lastUpdated = int(time.time() * 1000)

    def to_dict(self) -> dict:
        return {
            "syncId": self.syncId,
            "bookId": self.bookId,
            "bookTitle": self.bookTitle,
            "memberId": self.memberId,
            "memberName": self.memberName,
            "reservedDate": self.reservedDate,
            "status": self.status,
            "notifiedDate": self.notifiedDate,
            "lastUpdated": self.lastUpdated,
            "deleted": self.deleted,
            "collegeId": "",
            "syncStatus": "synced",
        }

    @staticmethod
    def from_dict(d: dict) -> "Reservation":
        return Reservation(
            syncId=d.get("syncId") or "",
            bookId=int(d.get("bookId") or 0),
            bookTitle=d.get("bookTitle") or "",
            memberId=int(d.get("memberId") or 0),
            memberName=d.get("memberName") or "",
            reservedDate=d.get("reservedDate") or "",
            status=d.get("status") or "Pending",
            notifiedDate=d.get("notifiedDate"),
            lastUpdated=int(d.get("lastUpdated") or 0),
            deleted=bool(d.get("deleted") or False),
        )


@dataclass
class User:
    uid: str = ""
    email: str = ""
    name: str = ""
    role: str = "admin"   # "admin", "librarian", "owner", "staff", "director"
    # institutionId is the canonical Firestore field (SYNC_ARCHITECTURE.md).
    # collegeId is kept as an alias for backward compatibility with older code.
    institutionId: str = ""

    @property
    def collegeId(self) -> str:
        """Backward-compat alias for institutionId."""
        return self.institutionId

    @collegeId.setter
    def collegeId(self, value: str):
        self.institutionId = value

    @staticmethod
    def from_dict(d: dict) -> "User":
        # Prefer institutionId (canonical), fall back to collegeId (legacy)
        inst_id = d.get("institutionId") or d.get("collegeId", "")
        return User(
            uid=d.get("uid", ""),
            email=d.get("email", ""),
            name=d.get("name", ""),
            role=d.get("role", "admin"),
            institutionId=inst_id,
        )
