"""
models/issue_record.py — IssuedBook record matching Android/Firestore schema.
"""
import uuid
import time
from dataclasses import dataclass
from typing import Optional


@dataclass
class IssueRecord:
    syncId: str = ""
    id: int = 0
    bookId: int = 0
    bookTitle: str = ""
    bookIsbn: str = ""
    memberId: int = 0
    memberName: str = ""
    memberMemberId: str = ""
    issueDate: str = ""
    dueDate: str = ""
    returnDate: Optional[str] = None
    fine: float = 0.0
    status: str = "Issued"   # "Issued" or "Returned"
    # Epoch millis, set explicitly at issue time by issue_return_screen.py.
    # Deliberately NOT auto-filled in __post_init__ like syncId/lastUpdated:
    # this field did not exist before it was added here, so every record
    # already in a college's database has 0 for it, and __post_init__ has no
    # way to tell "a genuinely new record" apart from "an old one just being
    # reloaded from SQLite" — auto-filling either case with time.time() would
    # fabricate a time of day for historical issues that never had one
    # recorded, which is exactly what this field replaces (see
    # ui/screens/heatmap_screen.py's fix). 0 means "no time recorded";
    # readers must treat it as missing data, never as midnight.
    issueTimestamp: int = 0
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
            "bookIsbn": self.bookIsbn,
            "memberId": self.memberId,
            "memberName": self.memberName,
            "memberMemberId": self.memberMemberId,
            "issueDate": self.issueDate,
            "dueDate": self.dueDate,
            "returnDate": self.returnDate,
            "fine": self.fine,
            "status": self.status,
            "issueTimestamp": self.issueTimestamp,
            "lastUpdated": self.lastUpdated,
            "deleted": self.deleted,
            "collegeId": "",
            "syncStatus": "synced",
        }

    @staticmethod
    def from_dict(d: dict) -> "IssueRecord":
        return IssueRecord(
            syncId=d.get("syncId") or "",
            bookId=int(d.get("bookId") or 0),
            bookTitle=d.get("bookTitle") or "",
            bookIsbn=d.get("bookIsbn") or "",
            memberId=int(d.get("memberId") or 0),
            memberName=d.get("memberName") or "",
            memberMemberId=d.get("memberMemberId") or "",
            issueDate=d.get("issueDate") or "",
            dueDate=d.get("dueDate") or "",
            returnDate=d.get("returnDate"),
            fine=float(d.get("fine") or 0.0),
            status=d.get("status") or "Issued",
            issueTimestamp=int(d.get("issueTimestamp") or 0),
            lastUpdated=int(d.get("lastUpdated") or 0),
            deleted=bool(d.get("deleted") or False),
        )
