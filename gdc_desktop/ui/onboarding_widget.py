from PyQt6.QtWidgets import QWidget, QVBoxLayout, QLabel, QLineEdit, QPushButton, QMessageBox
import uuid
import random
import time
import config
import re
from ui.widgets.qr_display_widget import QRDisplayWidget

class OnboardingWidget(QWidget):
    def __init__(self, auth_service, on_complete, parent=None):
        super().__init__(parent)
        self.auth = auth_service
        self.on_complete = on_complete
        self._build_ui()

    def _build_ui(self):
        lay = QVBoxLayout(self)
        
        lbl = QLabel("Welcome! You don't belong to any institution yet.")
        lbl.setStyleSheet("color: #E6C96E; font-weight: bold; font-size: 16px;")
        lay.addWidget(lbl)
        
        # Create Institution
        create_lbl = QLabel("Create New Institution")
        create_lbl.setStyleSheet("font-size: 14px; font-weight: bold; color: #A0B4CC; margin-top: 10px;")
        lay.addWidget(create_lbl)
        self.college_name_input = QLineEdit()
        self.college_name_input.setPlaceholderText("Enter College Name (e.g. GDC Mardan)...")
        lay.addWidget(self.college_name_input)
        self.college_id_create_input = QLineEdit()
        self.college_id_create_input.setPlaceholderText("Enter College Unique ID (e.g. GDC-MARDAN-01)...")
        lay.addWidget(self.college_id_create_input)
        create_btn = QPushButton("Create Institution")
        create_btn.setObjectName("loginBtn")
        create_btn.clicked.connect(self._create_institution)
        lay.addWidget(create_btn)
        
        # Join Institution
        join_lbl = QLabel("Or Join Existing Institution")
        join_lbl.setStyleSheet("font-size: 14px; font-weight: bold; color: #A0B4CC; margin-top: 20px;")
        lay.addWidget(join_lbl)
        self.college_id_join_input = QLineEdit()
        self.college_id_join_input.setPlaceholderText("Enter College Unique ID to join...")
        lay.addWidget(self.college_id_join_input)
        join_btn = QPushButton("Join Institution")
        join_btn.setObjectName("loginBtn")
        join_btn.clicked.connect(self._join_institution)
        lay.addWidget(join_btn)

    def _create_institution(self):
        cname = self.college_name_input.text().strip()
        cid = self.college_id_create_input.text().strip().upper()
        
        if not cname:
            QMessageBox.warning(self, "Error", "Please enter a valid college name.")
            return
        if not cid:
            QMessageBox.warning(self, "Error", "Please enter a College Unique ID.")
            return
        if not re.match(r"^[A-Z0-9\-]+$", cid):
            QMessageBox.warning(self, "Error", "College Unique ID can only contain letters, numbers, and hyphens.")
            return

        if self.auth.fb.db:
            try:
                # Check if this ID already exists
                existing = self.auth.fb.db.collection("institutions").document(cid).get()
                if existing.exists:
                    QMessageBox.warning(self, "Error", "This College Unique ID is already in use. Please choose another or join it.")
                    return
                
                # Create institution document. ownerUid is what the Firestore
                # security rules use to recognise this account as the owner —
                # the Android and Web clients are rule-enforced even though this
                # desktop app (firebase-admin) is not, so it has to be written
                # here too or those clients lose privileged access.
                uid = self.auth.current_user.uid
                self.auth.fb.db.collection("institutions").document(cid).set({
                    "name": cname,
                    "inviteCode": cid, # Keep this field for backward compatibility if needed, but it's identical to ID
                    "ownerUid": uid,
                    "createdAt": int(time.time() * 1000)
                })
                # Write canonical users/{uid} document (institutionId is the key)
                self.auth.fb.db.collection("users").document(uid).set({
                    "email": self.auth.current_user.email,
                    "institutionId": cid,   # canonical field
                    "role": "owner"
                }, merge=True)

                # Register in the directorate registry so the college is visible
                # to the directorate portal immediately, rather than only after
                # someone happens to open the college profile screen.
                try:
                    from services.registry_service import RegistryService
                    RegistryService(None, self.auth.fb).register_institution(cid, cname, uid)
                except Exception as e:
                    print(f"Directorate registration skipped: {e}")

                # Update local session objects
                self.auth.current_user.institutionId = cid
                self.auth.current_user.role = "owner"
                self.auth.fb.college_id = cid
                config.COLLEGE_ID = cid
                config.COLLEGE_NAME = cname

                # Show QR
                try:
                    qr_dlg = QRDisplayWidget(self.auth.fb, self.auth, self)
                    qr_dlg.exec()
                except Exception as e:
                    print("Error showing QR:", e)

                QMessageBox.information(
                    self, "Success",
                    f"Institution created!\nYour College Unique ID is: {cid}\nUse this ID or the QR code on other devices to join."
                )
                self.on_complete()
            except Exception as e:
                QMessageBox.warning(self, "Error", f"Failed to create institution: {e}")
        else:
            QMessageBox.warning(self, "Offline Mode",
                                "Cannot create institution in offline/mock mode.")

    def _join_institution(self):
        cid = self.college_id_join_input.text().strip().upper()
        if not cid:
            QMessageBox.warning(self, "Error", "Please enter a College Unique ID.")
            return

        if self.auth.fb.db:
            try:
                doc = self.auth.fb.db.collection("institutions").document(cid).get()
                if not doc.exists:
                    QMessageBox.warning(self, "Error", "Invalid College Unique ID. Institution not found.")
                    return

                cname = doc.to_dict().get("name", "Unknown College")

                # Write canonical users/{uid} document
                uid = self.auth.current_user.uid
                self.auth.fb.db.collection("users").document(uid).set({
                    "email": self.auth.current_user.email,
                    "institutionId": cid,   # canonical field
                    "role": "staff"
                }, merge=True)

                # Update local session objects
                self.auth.current_user.institutionId = cid
                self.auth.current_user.role = "staff"
                self.auth.fb.college_id = cid
                config.COLLEGE_ID = cid
                config.COLLEGE_NAME = cname

                QMessageBox.information(self, "Success", f"Joined {cname} successfully!")
                self.on_complete()
            except Exception as e:
                QMessageBox.warning(self, "Error", f"Failed to join institution: {e}")
        else:
            QMessageBox.warning(self, "Offline Mode",
                                "Cannot join institution in offline/mock mode.")
