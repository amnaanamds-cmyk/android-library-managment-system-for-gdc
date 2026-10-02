"""
ui/screens/college_profile_screen.py — Institutional Branding & Configuration.
Allows the librarian to set the College Name, Logo, and Physical Location.
Syncs to Firestore so the mobile app also reflects the branding.
"""
import os
import time
import uuid
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QLineEdit, QPushButton,
    QFileDialog, QMessageBox, QFrame, QScrollArea
)
from PyQt6.QtCore import Qt, QThread, pyqtSignal
from PyQt6.QtGui import QPixmap, QIcon
import config
from services.storage_service import StorageService


class LoadProfileWorker(QThread):
    """The one live Firestore GET this screen makes. Used to run directly
    inside __init__ -> load_data(), so opening this screen on a slow or
    flaky rural connection blocked the whole app until the request timed
    out. Now it only ever carries data back; the screen is already showing
    the cached local profile by the time this finishes."""
    finished = pyqtSignal(dict)

    def __init__(self, fb):
        super().__init__()
        self.fb = fb

    def run(self):
        result = {}
        if not self.fb.mock_mode and self.fb.college_id:
            try:
                doc = self.fb.db.collection("institutions").document(self.fb.college_id).get()
                if doc.exists:
                    result = doc.to_dict() or {}
            except Exception as e:
                print(f"Failed to fetch cloud profile in desktop: {e}")
        self.finished.emit(result)


class SaveProfileWorker(QThread):
    """The three Firestore .set() calls save_data() used to make one after
    another on the UI thread on every click of Save."""
    finished = pyqtSignal(bool, str)

    def __init__(self, fb, profile_data):
        super().__init__()
        self.fb = fb
        self.profile_data = profile_data

    def run(self):
        try:
            cid = self.fb.college_id
            self.fb.db.collection("institutions").document(cid).set(self.profile_data, merge=True)

            self.fb.db.collection("directorate_index").document(cid).set({
                "institutionId": cid,
                "name": self.profile_data["name"],
                "location": self.profile_data["location"],
                "lastSeen": int(time.time() * 1000),
            }, merge=True)

            self.fb.db.collection("colleges").document(cid).set({
                "collegeId": cid,
                "collegeName": self.profile_data["name"],
                "location": self.profile_data["location"],
                "lastSyncAt": int(time.time() * 1000),
            }, merge=True)

            self.finished.emit(True, "")
        except Exception as e:
            self.finished.emit(False, str(e))


class LogoUploadWorker(QThread):
    finished = pyqtSignal(bool, str)

    def __init__(self, storage: StorageService, local_path: str, dest_filename: str):
        super().__init__()
        self.storage = storage
        self.local_path = local_path
        self.dest_filename = dest_filename

    def run(self):
        ok, result = self.storage.upload(self.local_path, f"branding/{self.dest_filename}")
        self.finished.emit(ok, result)


class CollegeProfileScreen(QWidget):
    def __init__(self, db_helper, firebase_service, main_window=None):
        super().__init__()
        self.db = db_helper
        self.fb = firebase_service
        self.storage = StorageService(firebase_service)
        self.main_window = main_window
        self._load_worker = None
        self._save_worker = None
        self._logo_worker = None
        self._pending_logo_path = None
        self._build_ui()
        self.load_data()

    def _build_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(40, 40, 40, 40)
        layout.setSpacing(24)

        # Header
        hdr = QLabel("🏛️  COLLEGE PROFILE & BRANDING")
        hdr.setStyleSheet("font-size: 26px; font-weight: 900; color: #3B82F6;")
        layout.addWidget(hdr)

        # Main Card
        card = QFrame()
        card.setStyleSheet("background: #1E293B; border-radius: 16px; border: 1px solid #334155;")
        card_lay = QVBoxLayout(card)
        card_lay.setContentsMargins(30, 30, 30, 30)
        card_lay.setSpacing(20)

        # Logo Area
        logo_lay = QHBoxLayout()
        self.logo_lbl = QLabel()
        self.logo_lbl.setFixedSize(120, 120)
        self.logo_lbl.setStyleSheet("background: #0F172A; border-radius: 60px; border: 2px dashed #334155;")
        self.logo_lbl.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.logo_lbl.setText("NO LOGO")
        logo_lay.addWidget(self.logo_lbl)

        logo_btns = QVBoxLayout()
        up_btn = QPushButton("📤 Upload Logo")
        up_btn.setStyleSheet("background: #2563EB; color: white; padding: 8px 16px;")
        up_btn.clicked.connect(self._upload_logo)
        logo_btns.addWidget(up_btn)

        rem_btn = QPushButton("🗑️ Remove")
        rem_btn.setStyleSheet("background: transparent; color: #EF4444; border: 1px solid #EF4444; padding: 8px 16px;")
        logo_btns.addStretch()
        logo_lay.addLayout(logo_btns)
        logo_lay.addStretch()
        card_lay.addLayout(logo_lay)

        # Form
        self.name_input = self._create_field("College / Institution Name", "e.g. Government Degree College")
        self.loc_input = self._create_field("Physical Location", "e.g. Peshawar, Pakistan")
        self.email_input = self._create_field("Official Library Email", "library@college.edu")
        self.phone_input = self._create_field("Contact Number", "+92 300 1234567")

        card_lay.addWidget(QLabel("INSTITUTION NAME"))
        card_lay.addWidget(self.name_input)
        card_lay.addWidget(QLabel("LOCATION"))
        card_lay.addWidget(self.loc_input)
        card_lay.addWidget(QLabel("CONTACT EMAIL"))
        card_lay.addWidget(self.email_input)
        card_lay.addWidget(QLabel("PHONE NUMBER"))
        card_lay.addWidget(self.phone_input)

        save_btn = QPushButton("💾  SAVE INSTITUTION PROFILE")
        save_btn.setStyleSheet("background: #10B981; color: white; font-weight: 900; padding: 15px; font-size: 14px; margin-top: 20px;")
        save_btn.clicked.connect(self.save_data)
        card_lay.addWidget(save_btn)

        layout.addWidget(card)
        layout.addStretch()

    def _create_field(self, label, placeholder):
        f = QLineEdit()
        f.setPlaceholderText(placeholder)
        f.setStyleSheet("background: #0F172A; border: 1px solid #334155; padding: 12px; color: white; border-radius: 8px;")
        return f

    def load_data(self):
        # Local cache first — instant, no network, so the screen never
        # opens blank while waiting on Firestore.
        reg = self.db.get_college_registration()
        self._name = reg.get("name", config.COLLEGE_NAME)
        self._loc = reg.get("location", config.COLLEGE_LOCATION)
        self._email = reg.get("email", "")
        self._phone = reg.get("phone", "")
        self._apply_fields()

        logo_path = reg.get("logo_url")
        if logo_path:
            self.logo_lbl.setText("LOGO SET")

        # Then refresh from the cloud in the background — this used to be
        # a synchronous Firestore GET right here, blocking the screen from
        # even opening until it returned (or timed out, on a bad rural
        # connection).
        self._load_worker = LoadProfileWorker(self.fb)
        self._load_worker.finished.connect(self._on_cloud_profile_loaded)
        self._load_worker.start()

    def _apply_fields(self):
        self.name_input.setText(self._name)
        self.loc_input.setText(self._loc)
        self.email_input.setText(self._email)
        self.phone_input.setText(self._phone)

    def _on_cloud_profile_loaded(self, cdata: dict):
        if not cdata:
            return
        self._name = cdata.get("collegeFullName") or cdata.get("name") or self._name
        self._loc = cdata.get("address") or cdata.get("location") or self._loc
        self._email = cdata.get("email") or cdata.get("contactEmail") or self._email
        self._phone = cdata.get("phone") or self._phone
        self._apply_fields()

        self.db.set_college_registration("name", self._name)
        self.db.set_college_registration("location", self._loc)
        self.db.set_college_registration("email", self._email)
        self.db.set_college_registration("phone", self._phone)

    def save_data(self):
        name = self.name_input.text().strip()
        loc = self.loc_input.text().strip()
        email = self.email_input.text().strip()
        phone = self.phone_input.text().strip()

        if not name:
            QMessageBox.warning(self, "Error", "Institution name is required.")
            return

        # 1. Update Local DB — instant, no network, always succeeds.
        self.db.set_college_registration("name", name)
        self.db.set_college_registration("location", loc)
        self.db.set_college_registration("email", email)
        self.db.set_college_registration("phone", phone)

        # 2. Update Config
        config.COLLEGE_NAME = name
        config.COLLEGE_LOCATION = loc

        # Branding and the "saved" confirmation reflect the local save,
        # which already succeeded — they don't wait on the network.
        if self.main_window:
            try:
                self.main_window.refresh_branding()
            except Exception as e:
                print(f"Failed to refresh sidebar branding: {e}")
        QMessageBox.information(self, "Success", "College Profile updated successfully!\n\nSyncing to the Directorate and your mobile apps now.")

        # 3. Push to Firestore in the background — this used to be three
        # sequential .set() calls on the UI thread on every click of Save.
        if not self.fb.mock_mode and self.fb.college_id:
            logo_url = self.db.get_college_registration().get("logo_url", "")
            profile_data = {
                "name": name,
                "location": loc,
                "address": loc,
                "email": email,
                "contactEmail": email,
                "phone": phone,
                "collegeFullName": name,  # for Android compat
                "collegeName": name,
                "tagline": "Knowledge is Power",
                "lastUpdated": int(time.time() * 1000),
                "isSetupComplete": True,
            }
            if logo_url:
                profile_data["logoUrl"] = logo_url
            self._save_worker = SaveProfileWorker(self.fb, profile_data)
            self._save_worker.finished.connect(self._on_cloud_profile_saved)
            self._save_worker.start()

    def _on_cloud_profile_saved(self, ok: bool, error: str):
        if ok:
            print(f"Cloud profile updated for {self.fb.college_id}")
        else:
            print(f"Firestore update failed: {error}")

    def _upload_logo(self):
        path, _ = QFileDialog.getOpenFileName(self, "Choose Logo", "", "Images (*.png *.jpg *.jpeg)")
        if not path:
            return

        pixmap = QPixmap(path).scaled(120, 120, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation)
        self.logo_lbl.setPixmap(pixmap)
        self.logo_lbl.setText("")

        if self.fb.mock_mode or not self.fb.college_id:
            QMessageBox.information(self, "Logo Set Locally",
                                     "Showing this logo on this device only — not connected to the cloud right now.")
            return

        ext = os.path.splitext(path)[1] or ".png"
        dest_filename = f"logo{ext}"  # one logo per college — overwrite, don't accumulate
        self._logo_worker = LogoUploadWorker(self.storage, path, dest_filename)
        self._logo_worker.finished.connect(self._on_logo_uploaded)
        self._logo_worker.start()

    def _on_logo_uploaded(self, ok: bool, result: str):
        if ok:
            self._pending_logo_path = result
            self.db.set_college_registration("logo_url", result)
            QMessageBox.information(self, "Logo Uploaded", "Logo uploaded — it will sync to the Directorate and your mobile apps.")
        else:
            QMessageBox.warning(self, "Upload Failed", f"The logo was not uploaded: {result}")
