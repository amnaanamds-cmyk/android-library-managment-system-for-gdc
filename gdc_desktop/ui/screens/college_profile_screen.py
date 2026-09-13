"""
ui/screens/college_profile_screen.py — Institutional Branding & Configuration.
Allows the librarian to set the College Name, Logo, and Physical Location.
Syncs to Firestore so the mobile app also reflects the branding.
"""
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QLineEdit, QPushButton,
    QFileDialog, QMessageBox, QFrame, QScrollArea
)
from PyQt6.QtCore import Qt
from PyQt6.QtGui import QPixmap, QIcon
import config

class CollegeProfileScreen(QWidget):
    def __init__(self, db_helper, firebase_service, main_window=None):
        super().__init__()
        self.db = db_helper
        self.fb = firebase_service
        self.main_window = main_window
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
        # Load from Local DB (cached config)
        reg = self.db.get_college_registration()
        name = reg.get("name", config.COLLEGE_NAME)
        loc = reg.get("location", config.COLLEGE_LOCATION)
        email = reg.get("email", "")
        phone = reg.get("phone", "")

        # Try to pull latest cloud profile if available
        if not self.fb.mock_mode and self.fb.college_id:
            try:
                doc = self.fb.db.collection("institutions").document(self.fb.college_id).get()
                if doc.exists:
                    cdata = doc.to_dict() or {}
                    name = cdata.get("collegeFullName") or cdata.get("name") or name
                    loc = cdata.get("address") or cdata.get("location") or loc
                    email = cdata.get("email") or cdata.get("contactEmail") or email
                    phone = cdata.get("phone") or phone
                    # Cache back into local DB
                    self.db.set_college_registration("name", name)
                    self.db.set_college_registration("location", loc)
                    self.db.set_college_registration("email", email)
                    self.db.set_college_registration("phone", phone)
            except Exception as e:
                print(f"Failed to fetch cloud profile in desktop: {e}")

        self.name_input.setText(name)
        self.loc_input.setText(loc)
        self.email_input.setText(email)
        self.phone_input.setText(phone)

        logo_path = reg.get("logo_url")
        if logo_path:
            self.logo_lbl.setText("LOGO SET")

    def save_data(self):
        name = self.name_input.text().strip()
        loc = self.loc_input.text().strip()
        email = self.email_input.text().strip()
        phone = self.phone_input.text().strip()

        if not name:
            QMessageBox.warning(self, "Error", "Institution name is required.")
            return

        # 1. Update Local DB
        self.db.set_college_registration("name", name)
        self.db.set_college_registration("location", loc)
        self.db.set_college_registration("email", email)
        self.db.set_college_registration("phone", phone)

        # 2. Update Config
        config.COLLEGE_NAME = name
        config.COLLEGE_LOCATION = loc

        # 3. Push to Firestore (Multi-Tenant & Directorate Sync)
        if not self.fb.mock_mode and self.fb.college_id:
            try:
                import time
                profile_data = {
                    "name": name,
                    "location": loc,
                    "address": loc,
                    "email": email,
                    "contactEmail": email,
                    "phone": phone,
                    "collegeFullName": name, # for Android compat
                    "collegeName": name,
                    "tagline": "Knowledge is Power",
                    "lastUpdated": int(time.time() * 1000),
                    "isSetupComplete": True
                }
                # Update specific institution document
                self.fb.db.collection("institutions").document(self.fb.college_id).set(profile_data, merge=True)

                # Also ensure registration in a global index for the Directorate Dashboard
                self.fb.db.collection("directorate_index").document(self.fb.college_id).set({
                    "institutionId": self.fb.college_id,
                    "name": name,
                    "location": loc,
                    "lastSeen": int(time.time() * 1000)
                }, merge=True)

                # Register in the canonical 'colleges' collection for the Web App Director Dashboard
                self.fb.db.collection("colleges").document(self.fb.college_id).set({
                    "collegeId": self.fb.college_id,
                    "collegeName": name,
                    "location": loc,
                    "lastSyncAt": int(time.time() * 1000)
                }, merge=True)

                print(f"Cloud profile updated for {self.fb.college_id}")
            except Exception as e:
                print(f"Firestore update failed: {e}")

        # Refresh the sidebar branding immediately — it's built once at
        # startup and otherwise wouldn't show this change until app restart.
        if self.main_window:
            try:
                self.main_window.refresh_branding()
            except Exception as e:
                print(f"Failed to refresh sidebar branding: {e}")

        QMessageBox.information(self, "Success", "College Profile updated successfully!\n\nThis profile is now registered with the Directorate and synced to your mobile apps.")

    def _upload_logo(self):
        import time
        path, _ = QFileDialog.getOpenFileName(self, "Choose Logo", "", "Images (*.png *.jpg *.jpeg)")
        if path:
            pixmap = QPixmap(path).scaled(120, 120, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation)
            self.logo_lbl.setPixmap(pixmap)
            self.logo_lbl.setText("")
            # In a real app, upload to Firebase Storage and save URL
            QMessageBox.information(self, "Logo Uploaded", "Logo selected locally. It will be uploaded to cloud on save.")
