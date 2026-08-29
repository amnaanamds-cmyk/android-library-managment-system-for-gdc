"""
ui/screens/biometric_screen.py — Biometric Verification Module.
Simulates fingerprint enrolment and verification for members.
Stores a biometric hash (fingerprint template mock) in the local DB.
In production, connect to a USB fingerprint reader SDK (e.g. ZKFinger, Mantra).
"""
import time
import hashlib
import random
import string
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QTableWidget, QTableWidgetItem, QHeaderView, QDialog,
    QFormLayout, QLineEdit, QMessageBox, QComboBox,
    QFrame, QTabWidget, QProgressBar, QCheckBox, QSpinBox
)
from PyQt6.QtCore import Qt, QThread, pyqtSignal, QTimer
from PyQt6.QtGui import QColor, QFont, QPixmap, QPainter, QBrush, QPen


# ── Fingerprint Scan Simulation Widget ──────────────────────────────────────
class FingerprintWidget(QFrame):
    """Visual fingerprint scanner simulation."""
    scan_complete = pyqtSignal(str)  # emits mock biometric hash

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setFixedSize(180, 200)
        self.setObjectName("fpWidget")
        self.setStyleSheet(
            "QFrame#fpWidget { background:#0F172A; border:2px solid #334155; border-radius:16px; }"
        )
        self._state = "idle"   # idle | scanning | done | error
        self._progress = 0
        self._hash = ""

        layout = QVBoxLayout(self)
        layout.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.setSpacing(8)

        self.fp_lbl = QLabel("👆")
        self.fp_lbl.setStyleSheet("font-size:52px;")
        self.fp_lbl.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(self.fp_lbl)

        self.status_lbl = QLabel("Place finger\non scanner")
        self.status_lbl.setStyleSheet("color:#64748B;font-size:11px;text-align:center;")
        self.status_lbl.setAlignment(Qt.AlignmentFlag.AlignCenter)
        layout.addWidget(self.status_lbl)

        self.bar = QProgressBar()
        self.bar.setRange(0, 100)
        self.bar.setValue(0)
        self.bar.setFixedHeight(6)
        self.bar.setVisible(False)
        layout.addWidget(self.bar)

        self._timer = QTimer()
        self._timer.timeout.connect(self._tick)

    def start_scan(self):
        self._state = "scanning"
        self._progress = 0
        self.fp_lbl.setText("🔄")
        self.status_lbl.setText("Scanning…")
        self.status_lbl.setStyleSheet("color:#3B82F6;font-size:11px;")
        self.bar.setVisible(True)
        self._timer.start(60)

    def _tick(self):
        self._progress += random.randint(3, 8)
        self.bar.setValue(min(self._progress, 100))
        if self._progress >= 100:
            self._timer.stop()
            self._state = "done"
            self.fp_lbl.setText("✅")
            self.status_lbl.setText("Scan complete!")
            self.status_lbl.setStyleSheet("color:#10B981;font-size:11px;")
            # Generate mock biometric hash
            raw = "".join(random.choices(string.ascii_letters + string.digits, k=64))
            self._hash = hashlib.sha256(raw.encode()).hexdigest()
            self.scan_complete.emit(self._hash)

    def reset(self):
        self._timer.stop()
        self._state = "idle"
        self._hash = ""
        self.fp_lbl.setText("👆")
        self.status_lbl.setText("Place finger\non scanner")
        self.status_lbl.setStyleSheet("color:#64748B;font-size:11px;")
        self.bar.setValue(0)
        self.bar.setVisible(False)


# ── Enrol Dialog ─────────────────────────────────────────────────────────────
class EnrolDialog(QDialog):
    def __init__(self, members, parent=None):
        super().__init__(parent)
        self.members = members
        self._hash = ""
        self.setWindowTitle("Biometric Enrolment")
        self.setMinimumWidth(440)
        self._build()

    def _build(self):
        lay = QVBoxLayout(self)
        lay.setContentsMargins(24, 20, 24, 20)
        lay.setSpacing(14)

        lay.addWidget(QLabel("🔐  Biometric Enrolment",
                             styleSheet="font-size:18px;font-weight:800;"))
        lay.addWidget(QLabel("Enrol a member's fingerprint to prevent library card misuse.",
                             styleSheet="color:#94A3B8;font-size:12px;"))

        form = QFormLayout()
        self.member_cb = QComboBox()
        self.member_cb.setEditable(True)
        self.member_cb.addItems([f"{m.memberId} — {m.name}" for m in self.members])
        form.addRow("Member *", self.member_cb)
        lay.addLayout(form)

        # Fingerprint widget
        fp_row = QHBoxLayout()
        fp_row.addStretch()
        self.fp_widget = FingerprintWidget()
        self.fp_widget.scan_complete.connect(self._on_scan)
        fp_row.addWidget(self.fp_widget)
        fp_row.addStretch()
        lay.addLayout(fp_row)

        scan_btn = QPushButton("📲  Simulate Fingerprint Scan")
        scan_btn.setStyleSheet(
            "background:#1E40AF;color:white;border:none;border-radius:8px;padding:10px;font-weight:700;"
        )
        scan_btn.clicked.connect(self.fp_widget.start_scan)
        lay.addWidget(scan_btn)

        self.result_lbl = QLabel("")
        self.result_lbl.setStyleSheet("color:#10B981;font-size:11px;")
        lay.addWidget(self.result_lbl)

        btn_row = QHBoxLayout()
        btn_row.addStretch()
        cancel = QPushButton("Cancel")
        cancel.clicked.connect(self.reject)
        self.save_btn = QPushButton("💾  Save Biometric")
        self.save_btn.setEnabled(False)
        self.save_btn.setStyleSheet(
            "background:#059669;color:white;border:none;border-radius:8px;padding:9px 20px;font-weight:700;"
        )
        self.save_btn.clicked.connect(self.accept)
        btn_row.addWidget(cancel)
        btn_row.addWidget(self.save_btn)
        lay.addLayout(btn_row)

    def _on_scan(self, bio_hash: str):
        self._hash = bio_hash
        self.result_lbl.setText(f"✅ Template: {bio_hash[:16]}…")
        self.save_btn.setEnabled(True)

    def get_data(self):
        idx = self.member_cb.currentIndex()
        member = self.members[idx] if 0 <= idx < len(self.members) else None
        return member, self._hash


# ── Verify Dialog ─────────────────────────────────────────────────────────────
class VerifyDialog(QDialog):
    def __init__(self, member, parent=None):
        super().__init__(parent)
        self.member = member
        self._verified = False
        self.setWindowTitle(f"Verify — {member.name if member else 'Member'}")
        self.setMinimumWidth(360)
        self._build()

    def _build(self):
        lay = QVBoxLayout(self)
        lay.setContentsMargins(24, 20, 24, 20)
        lay.setSpacing(14)

        lay.addWidget(QLabel("🔍  Biometric Verification",
                             styleSheet="font-size:18px;font-weight:800;"))
        name = self.member.name if self.member else "Unknown"
        lay.addWidget(QLabel(f"Verifying: <b>{name}</b>",
                             styleSheet="font-size:13px;"))

        fp_row = QHBoxLayout()
        fp_row.addStretch()
        self.fp_widget = FingerprintWidget()
        self.fp_widget.scan_complete.connect(self._on_scan)
        fp_row.addWidget(self.fp_widget)
        fp_row.addStretch()
        lay.addLayout(fp_row)

        scan_btn = QPushButton("📲  Scan Fingerprint")
        scan_btn.setStyleSheet(
            "background:#1E40AF;color:white;border:none;border-radius:8px;padding:10px;font-weight:700;"
        )
        scan_btn.clicked.connect(self.fp_widget.start_scan)
        lay.addWidget(scan_btn)

        self.result_lbl = QLabel("")
        self.result_lbl.setAlignment(Qt.AlignmentFlag.AlignCenter)
        self.result_lbl.setStyleSheet("font-size:13px;font-weight:700;")
        lay.addWidget(self.result_lbl)

        close_btn = QPushButton("Close")
        close_btn.clicked.connect(self.accept)
        lay.addWidget(close_btn)

    def _on_scan(self, bio_hash: str):
        stored = getattr(self.member, "biometricHash", "") or ""
        if stored and stored == bio_hash:
            self._verified = True
            self.result_lbl.setText("✅  VERIFIED — Identity Confirmed")
            self.result_lbl.setStyleSheet("color:#10B981;font-size:14px;font-weight:800;")
        elif not stored:
            # No biometric enrolled — always allow (warn)
            self._verified = True
            self.result_lbl.setText("⚠️  No biometric enrolled — Proceed manually")
            self.result_lbl.setStyleSheet("color:#F59E0B;font-size:13px;font-weight:700;")
        else:
            # Mismatch — simulate match for demo (real scanner would compare templates)
            # In production: compare using SDK's 1:1 match function
            match_score = random.randint(70, 99)
            if match_score >= 75:
                self._verified = True
                self.result_lbl.setText(f"✅  MATCH ({match_score}%) — Access Granted")
                self.result_lbl.setStyleSheet("color:#10B981;font-size:14px;font-weight:800;")
            else:
                self._verified = False
                self.result_lbl.setText(f"❌  MISMATCH ({match_score}%) — Access Denied")
                self.result_lbl.setStyleSheet("color:#EF4444;font-size:14px;font-weight:800;")


# ── Main Screen ──────────────────────────────────────────────────────────────
class BiometricScreen(QWidget):

    COLS = ["Member ID", "Name", "Role", "Biometric", "Enrolled Date", "Last Verified"]

    def __init__(self, db_helper):
        super().__init__()
        self.db = db_helper
        self._members = []
        self._displayed = []
        self._build_ui()
        self.refresh()

    def _build_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(28, 24, 28, 24)
        layout.setSpacing(14)

        # ── Header ─────────────────────────────────────────
        hdr = QHBoxLayout()
        title_col = QVBoxLayout()
        title = QLabel("🔐  Biometric Verification")
        title.setStyleSheet("font-size:22px;font-weight:800;font-family:'Segoe UI';")
        sub = QLabel("Enrol member fingerprints to prevent library card misuse. Verify identity at issue/return.")
        sub.setStyleSheet("color:#94A3B8;font-size:12px;")
        title_col.addWidget(title)
        title_col.addWidget(sub)
        hdr.addLayout(title_col)
        hdr.addStretch()

        enrol_btn = QPushButton("➕  Enrol Fingerprint")
        enrol_btn.setStyleSheet(
            "background:qlineargradient(x1:0,y1:0,x2:1,y2:0,stop:0 #059669,stop:1 #10B981);"
            "color:white;border:none;border-radius:8px;padding:9px 18px;font-weight:700;"
        )
        enrol_btn.clicked.connect(self._enrol)
        hdr.addWidget(enrol_btn)

        verify_btn = QPushButton("🔍  Verify Selected")
        verify_btn.setStyleSheet(
            "background:qlineargradient(x1:0,y1:0,x2:1,y2:0,stop:0 #2563EB,stop:1 #3B82F6);"
            "color:white;border:none;border-radius:8px;padding:9px 18px;font-weight:700;"
        )
        verify_btn.clicked.connect(self._verify)
        hdr.addWidget(verify_btn)

        delete_btn = QPushButton("🗑️  Remove Biometric")
        delete_btn.setStyleSheet(
            "background:rgba(239,68,68,0.15);color:#EF4444;"
            "border:1px solid rgba(239,68,68,0.4);border-radius:8px;padding:9px 18px;font-weight:700;"
        )
        delete_btn.clicked.connect(self._remove_biometric)
        hdr.addWidget(delete_btn)

        layout.addLayout(hdr)

        # ── Stats strip ────────────────────────────────────
        stats_frame = QFrame()
        stats_frame.setObjectName("Card")
        stats_lay = QHBoxLayout(stats_frame)
        stats_lay.setContentsMargins(16, 12, 16, 12)

        self.stat_total = self._make_stat("Total Members", "0", "#3B82F6")
        self.stat_enrolled = self._make_stat("Enrolled", "0", "#10B981")
        self.stat_pending  = self._make_stat("Not Enrolled", "0", "#F59E0B")
        self.stat_rate     = self._make_stat("Enrolment Rate", "0%", "#8B5CF6")

        for s in [self.stat_total, self.stat_enrolled, self.stat_pending, self.stat_rate]:
            stats_lay.addWidget(s)
            if s != self.stat_rate:
                div = QFrame()
                div.setFixedWidth(1)
                div.setStyleSheet("background:#334155;")
                stats_lay.addWidget(div)
        layout.addWidget(stats_frame)

        # ── Search ─────────────────────────────────────────
        search_row = QHBoxLayout()
        self.search_bar = QLineEdit()
        self.search_bar.setPlaceholderText("🔍  Search members…")
        self.search_bar.textChanged.connect(self._filter)
        search_row.addWidget(self.search_bar)

        self.filter_cb = QComboBox()
        self.filter_cb.addItems(["All Members", "Enrolled", "Not Enrolled"])
        self.filter_cb.currentTextChanged.connect(lambda _: self._filter())
        search_row.addWidget(self.filter_cb)
        layout.addLayout(search_row)

        # ── Table ──────────────────────────────────────────
        self.table = QTableWidget()
        self.table.setColumnCount(len(self.COLS))
        self.table.setHorizontalHeaderLabels(self.COLS)
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        self.table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.table.setAlternatingRowColors(True)
        self.table.doubleClicked.connect(self._verify)
        layout.addWidget(self.table)

        # ── Info box ───────────────────────────────────────
        info = QLabel(
            "ℹ️  <b>Hardware Note:</b> For production use, connect a USB fingerprint reader "
            "(ZKFinger SDK, Mantra MFS100, or similar). The scanner simulation above generates "
            "a unique SHA-256 template per scan for testing purposes."
        )
        info.setWordWrap(True)
        info.setStyleSheet("color:#64748B;font-size:11px;border:1px solid #334155;border-radius:6px;padding:8px;")
        layout.addWidget(info)

    def _make_stat(self, label: str, value: str, color: str) -> QFrame:
        f = QFrame()
        lay = QVBoxLayout(f)
        lay.setContentsMargins(12, 8, 12, 8)
        lay.setSpacing(2)
        v = QLabel(value)
        v.setStyleSheet(f"font-size:24px;font-weight:800;color:{color};")
        v.setAlignment(Qt.AlignmentFlag.AlignCenter)
        l = QLabel(label)
        l.setStyleSheet("font-size:11px;color:#64748B;")
        l.setAlignment(Qt.AlignmentFlag.AlignCenter)
        lay.addWidget(v)
        lay.addWidget(l)
        f._val_lbl = v
        return f

    def refresh(self):
        try:
            self._members = self.db.get_members()
        except Exception:
            self._members = []
        self._filter()

    def _filter(self):
        q = self.search_bar.text().lower()
        fc = self.filter_cb.currentText()
        filtered = [m for m in self._members if q in (
            (m.name or "") + (m.memberId or "") + (getattr(m, "role", "") or "")
        ).lower()]
        if fc == "Enrolled":
            filtered = [m for m in filtered if getattr(m, "biometricHash", "")]
        elif fc == "Not Enrolled":
            filtered = [m for m in filtered if not getattr(m, "biometricHash", "")]
        self._displayed = filtered
        self._populate(filtered)

    def _populate(self, members):
        total = len(self._members)
        enrolled = sum(1 for m in self._members if getattr(m, "biometricHash", ""))
        self.stat_total._val_lbl.setText(str(total))
        self.stat_enrolled._val_lbl.setText(str(enrolled))
        self.stat_pending._val_lbl.setText(str(total - enrolled))
        rate = f"{int(enrolled / total * 100)}%" if total else "0%"
        self.stat_rate._val_lbl.setText(rate)

        self.table.setRowCount(len(members))
        for row, m in enumerate(members):
            has_bio = bool(getattr(m, "biometricHash", ""))
            enrol_date = getattr(m, "biometricEnrolDate", "") or "—"
            last_ver   = getattr(m, "biometricLastVerified", "") or "—"
            for col, val in enumerate([
                m.memberId or "",
                m.name or "",
                getattr(m, "role", "") or "Student",
                "🔐 Enrolled" if has_bio else "⏳ Not Enrolled",
                enrol_date,
                last_ver,
            ]):
                item = QTableWidgetItem(val)
                if col == 3:
                    item.setForeground(QColor("#10B981" if has_bio else "#F59E0B"))
                self.table.setItem(row, col, item)

    def _enrol(self):
        if not self._members:
            QMessageBox.warning(self, "No Members", "No members found in database.")
            return
        dlg = EnrolDialog(self._members, self)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            member, bio_hash = dlg.get_data()
            if not member or not bio_hash:
                return
            member.biometricHash      = bio_hash
            member.biometricEnrolDate = time.strftime("%Y-%m-%d")
            member.lastUpdated        = int(time.time() * 1000)
            try:
                self.db.save_member(member)
                QMessageBox.information(
                    self, "Enrolled",
                    f"✅ Biometric enrolled for:\n{member.name} ({member.memberId})"
                )
                self.refresh()
            except Exception as e:
                QMessageBox.warning(self, "Error", str(e))

    def _verify(self):
        row = self.table.currentRow()
        if row < 0 or row >= len(self._displayed):
            QMessageBox.information(self, "Select", "Select a member to verify.")
            return
        member = self._displayed[row]
        dlg = VerifyDialog(member, self)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            if dlg._verified:
                member.biometricLastVerified = time.strftime("%Y-%m-%d %H:%M")
                member.lastUpdated = int(time.time() * 1000)
                try:
                    self.db.save_member(member)
                    self.refresh()
                except Exception:
                    pass

    def _remove_biometric(self):
        row = self.table.currentRow()
        if row < 0 or row >= len(self._displayed):
            QMessageBox.information(self, "Select", "Select a member.")
            return
        member = self._displayed[row]
        if not getattr(member, "biometricHash", ""):
            QMessageBox.information(self, "Not Enrolled", "This member has no biometric enrolled.")
            return
        reply = QMessageBox.question(
            self, "Remove Biometric",
            f"Remove biometric data for {member.name}?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
        )
        if reply == QMessageBox.StandardButton.Yes:
            member.biometricHash = ""
            member.biometricEnrolDate = ""
            member.lastUpdated = int(time.time() * 1000)
            try:
                self.db.save_member(member)
                self.refresh()
            except Exception as e:
                QMessageBox.warning(self, "Error", str(e))
