"""
ui/screens/visitor_log_screen.py — Gate Entry & Visitor Management.
Tracks physical footfall, guest entries, and real-time library occupancy.
"""
import time
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QLineEdit, QPushButton,
    QTableWidget, QTableWidgetItem, QHeaderView, QFrame, QMessageBox,
    QComboBox, QInputDialog
)
from PyQt6.QtCore import Qt

class VisitorLogScreen(QWidget):
    def __init__(self, db_helper, firebase_service=None):
        super().__init__()
        self.db = db_helper
        # Shared Firestore collection, so records raised here reach the
        # Android and web apps. These features were local-SQLite-only.
        self.fb = firebase_service
        self.ops = None
        if firebase_service is not None:
            from services.operations_service import OperationsService
            self.ops = OperationsService(firebase_service)
        self._build_ui()
        self.refresh()

    def _build_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(32, 24, 32, 24)
        layout.setSpacing(20)

        # Header
        hdr = QHBoxLayout()
        title_v = QVBoxLayout()
        title = QLabel("🛂  GATE ENTRY MONITOR")
        title.setStyleSheet("font-size: 24px; font-weight: 800; color: #10B981;")
        sub = QLabel("Log daily visitors and track real-time library occupancy.")
        title_v.addWidget(title); title_v.addWidget(sub)
        hdr.addLayout(title_v)
        hdr.addStretch()

        self.occ_lbl = QLabel("CURRENT OCCUPANCY: 0")
        self.occ_lbl.setStyleSheet("background: #064E3B; color: #10B981; padding: 10px 20px; border-radius: 8px; font-weight: 900;")
        hdr.addWidget(self.occ_lbl)
        layout.addLayout(hdr)

        # Quick Entry Bar
        entry_frame = QFrame()
        entry_frame.setObjectName("Card")
        e_lay = QHBoxLayout(entry_frame)
        e_lay.setContentsMargins(20, 15, 20, 15)

        self.entry_input = QLineEdit()
        self.entry_input.setPlaceholderText("Scan Member ID or Type Guest Name...")
        self.entry_input.returnPressed.connect(self._log_entry)
        e_lay.addWidget(self.entry_input, stretch=3)

        self.type_combo = QComboBox()
        self.type_combo.addItems(["Member", "Guest", "Staff"])
        e_lay.addWidget(self.type_combo, stretch=1)

        log_btn = QPushButton("ENTRY SIGN-IN")
        log_btn.setStyleSheet("background: #10B981; color: white; font-weight: bold; padding: 10px 20px;")
        log_btn.clicked.connect(self._log_entry)
        e_lay.addWidget(log_btn)

        layout.addWidget(entry_frame)

        # Log Table
        self.table = QTableWidget()
        self.table.setColumnCount(5)
        self.table.setHorizontalHeaderLabels(["Name/ID", "Type", "Entry Time", "Exit Time", "Action"])
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        self.table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table.setAlternatingRowColors(True)
        layout.addWidget(self.table)

    def _load_rows(self):
        """Today's visitors.

        Reads the shared Firestore collection when it is available, so an entry
        recorded on the Android app at the door appears here. Falls back to the
        legacy local table when offline or signed out, so the desk keeps working.
        """
        if self.ops is not None:
            rows = self.ops.todays_visitors()
            if rows:
                return rows

        today = time.strftime("%Y-%m-%d")
        try:
            with self.db._get_conn() as conn:
                legacy = conn.execute(
                    "SELECT * FROM visitor_log WHERE date_str = ? ORDER BY entry_time DESC",
                    (today,),
                ).fetchall()
        except Exception:
            return []
        # Present legacy rows in the shared camelCase shape so the rest of this
        # screen does not need to know which source it got them from.
        return [{
            "syncId": f"local:{r['id']}",
            "name": r["name"],
            "visitorType": r["visitor_type"],
            "entryTime": r["entry_time"],
            "exitTime": r["exit_time"],
        } for r in legacy]

    def refresh(self):
        rows = self._load_rows()

        self.table.setRowCount(len(rows))
        occupancy = 0
        for r, row in enumerate(rows):
            name = row.get("name") or ""
            v_type = row.get("visitorType") or ""
            entry_ms = row.get("entryTime") or 0
            exit_ms = row.get("exitTime")
            entry = time.strftime("%H:%M:%S", time.localtime(entry_ms / 1000)) if entry_ms else "—"
            exit_t = time.strftime("%H:%M:%S", time.localtime(exit_ms / 1000)) if exit_ms else "IN LIBRARY"

            if not exit_ms:
                occupancy += 1

            self.table.setItem(r, 0, QTableWidgetItem(name))
            self.table.setItem(r, 1, QTableWidgetItem(v_type))
            self.table.setItem(r, 2, QTableWidgetItem(entry))

            exit_item = QTableWidgetItem(exit_t)
            if exit_t == "IN LIBRARY":
                exit_item.setForeground(Qt.GlobalColor.green)
            self.table.setItem(r, 3, exit_item)

            if not exit_ms:
                out_btn = QPushButton("SIGN OUT")
                out_btn.setStyleSheet("background: #EF4444; color: white; border-radius: 4px; padding: 4px;")
                out_btn.clicked.connect(lambda _, rid=row.get("syncId"): self._log_exit(rid))
                self.table.setCellWidget(r, 4, out_btn)
            else:
                self.table.setItem(r, 4, QTableWidgetItem("Completed"))

        self.occ_lbl.setText(f"CURRENT OCCUPANCY: {occupancy}")

    def _log_entry(self):
        val = self.entry_input.text().strip()
        v_type = self.type_combo.currentText()
        if not val: return

        if self.ops is not None and self.ops.sign_in_visitor(val, v_type):
            self.entry_input.clear()
            self.refresh()
            QMessageBox.information(self, "Welcome", f"Entry logged for {val}.")
            return

        # Offline or signed out: keep the local register working. The entry
        # stays local and is picked up by the one-time migration later.
        now = int(time.time() * 1000)
        today = time.strftime("%Y-%m-%d")
        with self.db._get_conn() as conn:
            conn.execute(
                "INSERT INTO visitor_log (name, visitor_type, date_str, entry_time) VALUES (?, ?, ?, ?)",
                (val, v_type, today, now),
            )
            conn.commit()

        self.entry_input.clear()
        self.refresh()
        QMessageBox.information(
            self, "Welcome",
            f"Entry logged for {val}.\n\n(Saved on this device only — not connected.)",
        )

    def _log_exit(self, record_id):
        """Sign a visitor out.

        `record_id` is a Firestore syncId for shared rows, or "local:<rowid>"
        for a legacy row that predates the migration — the prefix is what tells
        the two apart.
        """
        if not record_id:
            return

        if isinstance(record_id, str) and record_id.startswith("local:"):
            local_id = record_id.split(":", 1)[1]
            now = int(time.time() * 1000)
            with self.db._get_conn() as conn:
                conn.execute("UPDATE visitor_log SET exit_time = ? WHERE id = ?", (now, local_id))
                conn.commit()
            self.refresh()
            return

        if self.ops is not None:
            self.ops.sign_out_visitor(record_id)
        self.refresh()
