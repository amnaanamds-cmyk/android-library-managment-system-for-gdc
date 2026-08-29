"""
models/member.py — Member/Student data model matching Android/Firestore schema exactly.
PIN is stored as plain text, matching the Android SQLDelight implementation.
"""
import uuid
import time
from dataclasses import dataclass
from typing import Optional


@dataclass
class Member:
    syncId: str = ""
    id: int = 0
    memberId: str = ""
    name: str = ""
    email: str = ""
    phone: str = ""
    department: str = ""
    memberType: str = "Student"
    joinDate: str = ""
    expiryDate: str = ""
    booksIssued: int = 0
    fatherName: str = ""
    className: str = ""
    classNo: str = ""
    address: str = ""
    photoUri: Optional[str] = None
    designation: str = ""
    bps: str = ""
    pin: str = ""           # Stored as plain text — matches Android
    biometricHash: str = ""
    biometricEnrolDate: str = ""
    biometricLastVerified: str = ""
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
            "memberId": self.memberId,
            "name": self.name,
            "email": self.email,
            "phone": self.phone,
            "department": self.department,
            "memberType": self.memberType,
            "joinDate": self.joinDate,
            "expiryDate": self.expiryDate,
            "booksIssued": self.booksIssued,
            "fatherName": self.fatherName,
            "className": self.className,
            "classNo": self.classNo,
            "address": self.address,
            "photoUri": self.photoUri,
            "designation": self.designation,
            "bps": self.bps,
            "pin": self.pin,
            "biometricHash": self.biometricHash,
            "biometricEnrolDate": self.biometricEnrolDate,
            "biometricLastVerified": self.biometricLastVerified,
            "lastUpdated": self.lastUpdated,
            "deleted": self.deleted,
            "collegeId": "",
            "syncStatus": "synced",
        }

    @staticmethod
    def from_dict(d: dict) -> "Member":
        return Member(
            syncId=d.get("syncId") or "",
            memberId=d.get("memberId") or "",
            name=d.get("name") or "",
            email=d.get("email") or "",
            phone=d.get("phone") or "",
            department=d.get("department") or "",
            memberType=d.get("memberType") or "Student",
            joinDate=d.get("joinDate") or "",
            expiryDate=d.get("expiryDate") or "",
            booksIssued=int(d.get("booksIssued") or 0),
            fatherName=d.get("fatherName") or "",
            className=d.get("className") or "",
            classNo=d.get("classNo") or "",
            address=d.get("address") or "",
            photoUri=d.get("photoUri"),
            designation=d.get("designation") or "",
            bps=d.get("bps") or "",
            pin=d.get("pin") or "",
            biometricHash=d.get("biometricHash") or "",
            biometricEnrolDate=d.get("biometricEnrolDate") or "",
            biometricLastVerified=d.get("biometricLastVerified") or "",
            lastUpdated=int(d.get("lastUpdated") or 0),
            deleted=bool(d.get("deleted") or False),
        )

