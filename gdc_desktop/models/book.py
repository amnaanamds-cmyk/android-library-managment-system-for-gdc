"""
models/book.py — Book data model matching Android/Firestore schema exactly.
"""
import uuid
import time
from dataclasses import dataclass, field
from typing import Optional


@dataclass
class Book:
    syncId: str = ""
    id: int = 0
    isbn: str = ""
    accNo: str = ""
    title: str = ""
    author: str = ""
    publisher: str = ""
    publisherPlace: str = ""
    publishDate: str = ""
    edition: str = ""
    pages: int = 0
    procurement: str = ""
    volume: str = ""
    price: float = 0.0
    status: str = "Available"
    isDigital: bool = False
    digitalUrl: Optional[str] = None
    category: str = "Uncategorized"
    marcData: Optional[str] = None  # JSON string of MARC tags
    callNumber: str = ""  # DDC/LC call number for spine labels
    authorCutter: str = ""  # Cutter number (author code)
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
            "isbn": self.isbn,
            "accNo": self.accNo,
            "title": self.title,
            "author": self.author,
            "publisher": self.publisher,
            "publisherPlace": self.publisherPlace,
            "publishDate": self.publishDate,
            "edition": self.edition,
            "pages": self.pages,
            "procurement": self.procurement,
            "volume": self.volume,
            "price": self.price,
            "status": self.status,
            "isDigital": self.isDigital,
            "digitalUrl": self.digitalUrl,
            "category": self.category,
            "marcData": self.marcData,
            "callNumber": self.callNumber,
            "authorCutter": self.authorCutter,
            "lastUpdated": self.lastUpdated,
            "deleted": self.deleted,
            "collegeId": "",
            "syncStatus": "synced",
        }

    @staticmethod
    def from_dict(d: dict) -> "Book":
        return Book(
            syncId=d.get("syncId") or "",
            isbn=d.get("isbn") or "",
            accNo=d.get("accNo") or "",
            title=d.get("title") or "",
            author=d.get("author") or "",
            publisher=d.get("publisher") or "",
            publisherPlace=d.get("publisherPlace") or "",
            publishDate=d.get("publishDate") or "",
            edition=d.get("edition") or "",
            pages=int(d.get("pages") or 0),
            procurement=d.get("procurement") or "",
            volume=d.get("volume") or "",
            price=float(d.get("price") or 0.0),
            status=d.get("status") or "Available",
            isDigital=bool(d.get("isDigital") or False),
            digitalUrl=d.get("digitalUrl"),
            category=d.get("category") or "Uncategorized",
            marcData=d.get("marcData"),
            callNumber=d.get("callNumber") or "",
            authorCutter=d.get("authorCutter") or "",
            lastUpdated=int(d.get("lastUpdated") or 0),
            deleted=bool(d.get("deleted") or False),
        )
