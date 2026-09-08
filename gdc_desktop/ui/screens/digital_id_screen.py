import json
from PyQt6.QtWidgets import (QWidget, QVBoxLayout, QHBoxLayout, QPushButton,
                             QLabel, QListWidget, QLineEdit, QFileDialog, QMessageBox, QFrame)
from PyQt6.QtCore import Qt
from PyQt6.QtGui import QPixmap, QFont, QPainter
from PyQt6.QtPrintSupport import QPrinter, QPrintDialog
import config

try:
    import qrcode
    from PIL import ImageQt
    QRCODE_AVAILABLE = True
except ImportError:
    QRCODE_AVAILABLE = False


class DigitalIdScreen(QWidget):
    def __init__(self, db_helper):
        super().__init__()
        self.db = db_helper
        self._members_map = {}
        self.init_ui()

    def init_ui(self):
        self.setStyleSheet("""
            QWidget {
                background-color: #0F172A;
                color: #F1F5F9;
                font-family: 'Inter', 'Segoe UI', sans-serif;
            }
            QLineEdit {
                background: #1E293B;
                border: 1.5px solid #334155;
                border-radius: 8px;
                padding: 10px;
                color: white;
            }
            QLineEdit:focus {
                border: 1.5px solid #3B82F6;
            }
            QListWidget {
                background: #1E293B;
                border: 1px solid #334155;
                border-radius: 10px;
                padding: 5px;
            }
            QListWidget::item {
                padding: 8px;
                border-radius: 6px;
            }
            QListWidget::item:selected {
                background: #2563EB;
                color: #FFFFFF;
                font-weight: bold;
            }
            QPushButton {
                background: #2563EB;
                border: none;
                border-radius: 8px;
                padding: 10px 20px;
                color: #ffffff;
                font-weight: bold;
            }
            QPushButton:hover {
                background: #3B82F6;
            }
            QFrame#CardFrame {
                background: qlineargradient(x1:0, y1:0, x2:1, y2:1, stop:0 #0F1E3D, stop:0.5 #1A3A6E, stop:1 #0F1E3D);
                border: 2px solid #C8A84B;
                border-radius: 18px;
            }
        """)
        
        main_layout = QHBoxLayout(self)
        main_layout.setContentsMargins(20, 20, 20, 20)
        main_layout.setSpacing(20)
        
        left_panel = QVBoxLayout()
        left_panel.setContentsMargins(0, 0, 10, 0)
        
        left_header = QLabel("👥  Select Member")
        left_header.setFont(QFont("Segoe UI", 14, QFont.Weight.Bold))
        left_panel.addWidget(left_header)

        self.search_input = QLineEdit()
        self.search_input.setPlaceholderText("Search members by ID or name...")
        self.search_input.textChanged.connect(self.filter_members)
        left_panel.addWidget(self.search_input)
        
        self.member_list = QListWidget()
        self.member_list.itemSelectionChanged.connect(self.on_member_selected)
        left_panel.addWidget(self.member_list)
        
        main_layout.addLayout(left_panel, 1)
        
        right_panel = QVBoxLayout()
        right_panel.setAlignment(Qt.AlignmentFlag.AlignCenter)
        
        title_label = QLabel("🪪  DIGITAL ID CARD PREVIEW")
        title_label.setFont(QFont("Segoe UI", 18, QFont.Weight.Bold))
        title_label.setStyleSheet("color: #C8A84B; letter-spacing: 1px;")
        title_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        right_panel.addWidget(title_label)
        
        self.card_widget = QFrame()
        self.card_widget.setObjectName("CardFrame")
        self.card_widget.setFixedSize(420, 270)
        
        card_layout = QVBoxLayout(self.card_widget)
        card_layout.setContentsMargins(20, 15, 20, 15)
        
        # 1. Header Row
        header_layout = QHBoxLayout()
        logo_label = QLabel("📚")
        logo_label.setFont(QFont("Segoe UI", 24))
        
        title_box = QVBoxLayout()
        library_title = QLabel("GDC LIBRARY50")
        library_title.setFont(QFont("Segoe UI", 14, QFont.Weight.ExtraBold))
        library_title.setStyleSheet("color: #C8A84B;")
        
        inst_sub = QLabel(getattr(config, "COLLEGE_NAME", "") or "Government Degree College")
        inst_sub.setFont(QFont("Segoe UI", 9))
        inst_sub.setStyleSheet("color: #94A3B8;")
        title_box.addWidget(library_title)
        title_box.addWidget(inst_sub)
        
        header_layout.addWidget(logo_label)
        header_layout.addLayout(title_box)
        header_layout.addStretch()
        
        self.status_badge = QLabel("ACTIVE")
        self.status_badge.setStyleSheet("background-color: #059669; color: white; padding: 4px 8px; border-radius: 6px; font-weight: bold; font-size: 11px;")
        header_layout.addWidget(self.status_badge)
        
        card_layout.addLayout(header_layout)
        
        # Divider
        divider = QFrame()
        divider.setFixedHeight(1)
        divider.setStyleSheet("background-color: rgba(200, 168, 75, 0.4);")
        card_layout.addWidget(divider)

        # 2. Body Row (Info + QR Code)
        body_layout = QHBoxLayout()
        info_layout = QVBoxLayout()
        
        self.name_label = QLabel("Select a member...")
        self.name_label.setFont(QFont("Segoe UI", 13, QFont.Weight.Bold))
        self.name_label.setStyleSheet("color: white;")
        
        self.id_label = QLabel("MEMBER ID: —")
        self.id_label.setFont(QFont("Segoe UI", 10))
        self.id_label.setStyleSheet("color: #94A3B8;")

        self.dept_label = QLabel("DEPARTMENT: —")
        self.dept_label.setFont(QFont("Segoe UI", 10))
        self.dept_label.setStyleSheet("color: #94A3B8;")

        self.expiry_label = QLabel("VALID TILL: —")
        self.expiry_label.setFont(QFont("Segoe UI", 10))
        self.expiry_label.setStyleSheet("color: #94A3B8;")
        
        info_layout.addWidget(self.name_label)
        info_layout.addWidget(self.id_label)
        info_layout.addWidget(self.dept_label)
        info_layout.addWidget(self.expiry_label)
        info_layout.addStretch()
        
        body_layout.addLayout(info_layout)
        body_layout.addStretch()
        
        self.qr_label = QLabel()
        self.qr_label.setFixedSize(110, 110)
        self.qr_label.setStyleSheet("border: 2px solid #C8A84B; background: white; border-radius: 8px; padding: 4px;")
        self.qr_label.setAlignment(Qt.AlignmentFlag.AlignCenter)
        body_layout.addWidget(self.qr_label)
        
        card_layout.addLayout(body_layout)
        
        right_panel.addWidget(self.card_widget, alignment=Qt.AlignmentFlag.AlignCenter)
        
        if not QRCODE_AVAILABLE:
            warning_label = QLabel("⚠️ 'qrcode' or 'Pillow' library missing. Run: pip install qrcode pillow")
            warning_label.setStyleSheet("color: #EF4444; font-size: 11px;")
            right_panel.addWidget(warning_label, alignment=Qt.AlignmentFlag.AlignCenter)
        
        btn_layout = QHBoxLayout()
        btn_layout.setSpacing(15)
        
        save_btn = QPushButton("💾  Save Card PNG")
        save_btn.clicked.connect(self.save_as_png)
        
        print_btn = QPushButton("🖨️  Print ID Card")
        print_btn.clicked.connect(self.print_card)
        
        btn_layout.addWidget(save_btn)
        btn_layout.addWidget(print_btn)
        
        right_panel.addLayout(btn_layout)
        
        main_layout.addLayout(right_panel, 2)
        
        self.load_members()

    def refresh(self):
        self.load_members()

    def load_members(self):
        self.member_list.clear()
        self._members_map = {}
        try:
            members = self.db.get_members()
            for m in members:
                display_text = f"{m.memberId} - {m.name}"
                self.member_list.addItem(display_text)
                self._members_map[m.memberId] = m
            if not members:
                self.member_list.addItem("No members available")
        except Exception as e:
            print(f"Error loading members in Digital ID Screen: {e}")

    def filter_members(self, text):
        for i in range(self.member_list.count()):
            item = self.member_list.item(i)
            item.setHidden(text.lower() not in item.text().lower())

    def on_member_selected(self):
        items = self.member_list.selectedItems()
        if not items:
            return
            
        item_text = items[0].text()
        if " - " not in item_text:
            return

        member_id = item_text.split(" - ")[0]
        member = self._members_map.get(member_id)
        if not member:
            return

        self.name_label.setText(member.name)
        self.id_label.setText(f"MEMBER ID: {member.memberId}")
        self.dept_label.setText(f"DEPT: {member.department or 'General'}")
        self.expiry_label.setText(f"VALID TILL: {member.expiryDate or 'Permanent'}")
        
        status = "ACTIVE" if not member.deleted else "INACTIVE"
        if status == "ACTIVE":
            self.status_badge.setStyleSheet("background-color: #059669; color: white; padding: 4px 8px; border-radius: 6px; font-weight: bold; font-size: 11px;")
            self.status_badge.setText("ACTIVE")
        else:
            self.status_badge.setStyleSheet("background-color: #DC2626; color: white; padding: 4px 8px; border-radius: 6px; font-weight: bold; font-size: 11px;")
            self.status_badge.setText("INACTIVE")
            
        self.generate_qr(member)

    def generate_qr(self, member):
        if not QRCODE_AVAILABLE:
            return
            
        qr_payload = json.dumps({
            "id": member.memberId,
            "name": member.name,
            "dept": member.department or "",
            "type": member.memberType or "STUDENT",
            "exp": member.expiryDate or ""
        })
        
        qr = qrcode.QRCode(
            version=1,
            error_correction=qrcode.constants.ERROR_CORRECT_L,
            box_size=10,
            border=1,
        )
        qr.add_data(qr_payload)
        qr.make(fit=True)
        
        img = qr.make_image(fill_color="black", back_color="white")
        qimage = ImageQt.ImageQt(img)
        pixmap = QPixmap.fromImage(qimage).scaled(100, 100, Qt.AspectRatioMode.KeepAspectRatio, Qt.TransformationMode.SmoothTransformation)
        self.qr_label.setPixmap(pixmap)

    def save_as_png(self):
        if self.member_list.currentRow() < 0:
            QMessageBox.warning(self, "Select Member", "Please select a member first.")
            return

        file_path, _ = QFileDialog.getSaveFileName(self, "Save Digital ID Card", "", "PNG Images (*.png)")
        if file_path:
            pixmap = self.card_widget.grab()
            pixmap.save(file_path, "PNG")
            QMessageBox.information(self, "Success", f"Digital ID Card saved successfully:\n{file_path}")

    def print_card(self):
        if self.member_list.currentRow() < 0:
            QMessageBox.warning(self, "Select Member", "Please select a member first.")
            return

        printer = QPrinter(QPrinter.PrinterMode.HighResolution)
        dialog = QPrintDialog(printer, self)
        if dialog.exec() == QPrintDialog.DialogCode.Accepted:
            pixmap = self.card_widget.grab()
            painter = QPainter(printer)
            
            rect = painter.viewport()
            size = pixmap.size()
            size.scale(rect.size(), Qt.AspectRatioMode.KeepAspectRatio)
            painter.setViewport(rect.x(), rect.y(), size.width(), size.height())
            painter.setWindow(pixmap.rect())
            
            painter.drawPixmap(0, 0, pixmap)
            painter.end()

