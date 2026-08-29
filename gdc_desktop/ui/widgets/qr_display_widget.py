from PyQt6.QtWidgets import QDialog, QVBoxLayout, QLabel, QPushButton
from PyQt6.QtCore import Qt
from PyQt6 import QtGui
import time

class QRDisplayWidget(QDialog):
    def __init__(self, fb_service, auth_service, parent=None):
        super().__init__(parent)
        self.fb = fb_service
        self.auth = auth_service
        self.setWindowTitle("Link Mobile Device")
        self.setFixedSize(400, 500)
        self.setStyleSheet("background: white; color: black;")
        self._build_ui()

    def _build_ui(self):
        try:
            import qrcode
            from io import BytesIO

            inst_id = self.fb.college_id or "gdc11"
            role = self.auth.role or "admin"
            # Format: PREFIX|INST_ID|ROLE|TIMESTAMP
            qr_data = f"NEXLIB_LINK|{inst_id}|{role}|{int(time.time())}"

            # Generate QR
            qr = qrcode.QRCode(version=1, box_size=10, border=5)
            qr.add_data(qr_data)
            qr.make(fit=True)
            img = qr.make_image(fill_color="black", back_color="white")

            # Convert to QPixmap
            buffer = BytesIO()
            img.save(buffer, format="PNG")
            qimg = QtGui.QImage.fromData(buffer.getvalue())
            pixmap = QtGui.QPixmap.fromImage(qimg)

            vlay = QVBoxLayout(self)
            vlay.setContentsMargins(30, 30, 30, 30)

            title = QLabel("WhatsApp-Style Device Link")
            title.setStyleSheet("font-size: 18px; font-weight: bold; color: #1E3A8A;")
            title.setAlignment(Qt.AlignmentFlag.AlignCenter)
            vlay.addWidget(title)

            desc = QLabel(f"College ID: {inst_id}\n\nOpen NEXLIB on your phone and scan this code to login instantly.")
            desc.setWordWrap(True)
            desc.setAlignment(Qt.AlignmentFlag.AlignCenter)
            vlay.addWidget(desc)

            qr_lbl = QLabel()
            qr_lbl.setPixmap(pixmap.scaled(300, 300, Qt.AspectRatioMode.KeepAspectRatio))
            qr_lbl.setAlignment(Qt.AlignmentFlag.AlignCenter)
            vlay.addWidget(qr_lbl)

            close_btn = QPushButton("Close")
            close_btn.clicked.connect(self.close)
            vlay.addWidget(close_btn)

        except Exception as e:
            import logging
            logging.error(f"Could not generate QR: {e}")
            vlay = QVBoxLayout(self)
            err_lbl = QLabel("Failed to load QR generator (qrcode package missing).")
            vlay.addWidget(err_lbl)
            close_btn = QPushButton("Close")
            close_btn.clicked.connect(self.close)
            vlay.addWidget(close_btn)
