"""
ui/screens/sync_network_screen.py — College sync network.

Desktop equivalent of the web app's Sync page. A college publishes an
8-character sync code, and pairing with a partner's code records a
bi-directional link in `syncedWith` on both institution documents.

That link is what the union catalogue and inter-library loan features read to
decide which other colleges this one may see. Without a desktop equivalent, a
librarian who only ever uses the Windows app could not join the network at all.

Pairing exchanges nothing but the link itself: each college's catalogue,
members and circulation stay in their own tenant, and the security rules keep
them there.
"""
import random
import string
import time

from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QTableWidget, QTableWidgetItem, QHeaderView, QLineEdit,
    QMessageBox, QApplication,
)
from PyQt6.QtCore import Qt, QThread, pyqtSignal

# Ambiguous characters (0/O, 1/I) are excluded: these codes get read aloud
# over the phone between colleges.
CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
CODE_LENGTH = 8


def generate_sync_code() -> str:
    return "".join(random.choice(CODE_ALPHABET) for _ in range(CODE_LENGTH))


class _NetworkWorker(QThread):
    """Firestore work off the UI thread, so the window never freezes."""
    finished = pyqtSignal(object)

    def __init__(self, fn):
        super().__init__()
        self._fn = fn

    def run(self):
        try:
            self.finished.emit(self._fn())
        except Exception as e:
            self.finished.emit({"error": str(e)})


class SyncNetworkScreen(QWidget):
    def __init__(self, firebase_service):
        super().__init__()
        self.fb = firebase_service
        self._my_code = ""
        self._partners = []
        self._workers = []
        self.init_ui()
        self.refresh()

    # ── UI ───────────────────────────────────────────────────────────────────

    def init_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(28, 24, 28, 24)
        layout.setSpacing(14)

        hdr = QHBoxLayout()
        title = QLabel("🔗  College Sync Network")
        title.setStyleSheet(
            "font-size:22px;font-weight:800;color:#E8EEF8;font-family:'Segoe UI';"
        )
        hdr.addWidget(title)
        hdr.addStretch()
        self.btn_refresh = QPushButton("🔄 Refresh")
        self.btn_refresh.setStyleSheet(self._button_style("#1E5FD4", "#2872F0"))
        self.btn_refresh.clicked.connect(self.refresh)
        hdr.addWidget(self.btn_refresh)
        layout.addLayout(hdr)

        subtitle = QLabel(
            "Pair with partner colleges so their catalogues appear in the "
            "union catalogue and inter-library loans. Each college's own "
            "records stay private."
        )
        subtitle.setStyleSheet("color:#A0B4CC;font-size:13px;")
        subtitle.setWordWrap(True)
        layout.addWidget(subtitle)

        # ── Our own sync code ──
        code_box = QHBoxLayout()
        code_label = QLabel("Your sync code:")
        code_label.setStyleSheet("color:#A0B4CC;font-size:13px;font-weight:700;")
        code_box.addWidget(code_label)

        self.code_value = QLabel("…")
        self.code_value.setStyleSheet(
            "color:#E6C96E;font-size:22px;font-weight:800;"
            "letter-spacing:6px;font-family:'Consolas';"
        )
        self.code_value.setTextInteractionFlags(Qt.TextInteractionFlag.TextSelectableByMouse)
        code_box.addWidget(self.code_value)

        self.btn_copy = QPushButton("📋 Copy")
        self.btn_copy.setStyleSheet(self._button_style("#0F766E", "#14B8A6"))
        self.btn_copy.clicked.connect(self._copy_code)
        code_box.addWidget(self.btn_copy)
        code_box.addStretch()
        layout.addLayout(code_box)

        # ── Pair with a partner ──
        pair_box = QHBoxLayout()
        self.partner_input = QLineEdit()
        self.partner_input.setPlaceholderText("Enter a partner college's 8-character sync code…")
        self.partner_input.setMaxLength(CODE_LENGTH)
        self.partner_input.setStyleSheet(
            "background:#0D1B2A;color:#E8EEF8;border:1px solid #1E3050;"
            "border-radius:8px;padding:9px 12px;font-size:14px;letter-spacing:3px;"
        )
        self.partner_input.returnPressed.connect(self._connect_partner)
        pair_box.addWidget(self.partner_input, stretch=1)

        self.btn_connect = QPushButton("🔗 Connect")
        self.btn_connect.setStyleSheet(self._button_style("#1E5FD4", "#2872F0"))
        self.btn_connect.clicked.connect(self._connect_partner)
        pair_box.addWidget(self.btn_connect)
        layout.addLayout(pair_box)

        # ── Connected colleges ──
        self.table = QTableWidget(0, 4)
        self.table.setHorizontalHeaderLabels(
            ["College", "Institution ID", "Sync Code", "Action"]
        )
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        self.table.setStyleSheet(
            "QTableWidget{background:#0D1B2A;color:#E8EEF8;border:1px solid #1E3050;}"
            "QHeaderView::section{background:#1E3050;color:#E8EEF8;padding:6px;}"
        )
        layout.addWidget(self.table)

        self.status = QLabel("")
        self.status.setStyleSheet("color:#A0B4CC;font-size:12px;")
        layout.addWidget(self.status)

    @staticmethod
    def _button_style(c1: str, c2: str) -> str:
        return (
            f"background:qlineargradient(x1:0,y1:0,x2:1,y2:0,stop:0 {c1},stop:1 {c2});"
            "color:white;border:none;border-radius:8px;padding:9px 18px;"
            "font-size:13px;font-weight:700;"
        )

    # ── Data ─────────────────────────────────────────────────────────────────

    def _inst_ref(self):
        if self.fb.mock_mode or not self.fb.db or not self.fb.college_id:
            return None
        return self.fb.db.collection("institutions").document(self.fb.college_id)

    def _run(self, fn, on_done):
        worker = _NetworkWorker(fn)
        worker.finished.connect(on_done)
        # Keep a reference: a QThread that goes out of scope is destroyed while
        # still running and takes the app down with it.
        self._workers.append(worker)
        worker.finished.connect(lambda _: self._workers.remove(worker)
                                if worker in self._workers else None)
        worker.start()

    def refresh(self):
        ref = self._inst_ref()
        if ref is None:
            self.status.setText("Offline, or not signed in to an institution.")
            self.code_value.setText("—")
            return

        self.status.setText("Loading network…")

        def work():
            snap = ref.get()
            data = snap.to_dict() if snap.exists else {}

            # Mint a sync code on first use, so a college never has to think
            # about generating one.
            code = data.get("syncCode") or ""
            if not code:
                code = generate_sync_code()
                ref.set({"syncCode": code}, merge=True)

            partners = []
            for pid in data.get("syncedWith") or []:
                try:
                    psnap = self.fb.db.collection("institutions").document(pid).get()
                    if psnap.exists:
                        pdata = psnap.to_dict() or {}
                        partners.append({
                            "id": pid,
                            "name": pdata.get("name") or pid,
                            "code": pdata.get("syncCode") or pdata.get("inviteCode") or "—",
                        })
                except Exception:
                    # A partner we can no longer read is still worth listing,
                    # so the librarian can see and remove a stale link.
                    partners.append({"id": pid, "name": pid, "code": "—"})
            return {"code": code, "partners": partners}

        self._run(work, self._on_loaded)

    def _on_loaded(self, result):
        if not isinstance(result, dict) or "error" in result:
            self.status.setText(f"Could not load the network: {result.get('error', 'unknown error')}")
            return
        self._my_code = result["code"]
        self._partners = result["partners"]
        self.code_value.setText(self._my_code)
        self._populate_table()
        self.status.setText(
            f"{len(self._partners)} connected college(s)."
            if self._partners else "Not connected to any college yet."
        )

    def _populate_table(self):
        self.table.setRowCount(0)
        for i, p in enumerate(self._partners):
            self.table.insertRow(i)
            self.table.setItem(i, 0, QTableWidgetItem(p["name"]))
            self.table.setItem(i, 1, QTableWidgetItem(p["id"]))
            self.table.setItem(i, 2, QTableWidgetItem(p["code"]))
            btn = QPushButton("Disconnect")
            btn.setStyleSheet(
                "background:#7F1D1D;color:#FCA5A5;border:none;border-radius:6px;"
                "padding:4px 10px;font-size:12px;font-weight:700;"
            )
            btn.clicked.connect(lambda _, pid=p["id"], name=p["name"]: self._disconnect(pid, name))
            self.table.setCellWidget(i, 3, btn)

    # ── Actions ──────────────────────────────────────────────────────────────

    def _copy_code(self):
        if not self._my_code:
            return
        QApplication.clipboard().setText(self._my_code)
        self.status.setText("Sync code copied to the clipboard.")

    def _connect_partner(self):
        code = self.partner_input.text().strip().upper()
        if not code:
            return
        if code == self._my_code:
            QMessageBox.warning(self, "Error", "That is this college's own code.")
            return
        ref = self._inst_ref()
        if ref is None:
            QMessageBox.warning(self, "Offline", "Connect to the internet and sign in first.")
            return

        my_id = self.fb.college_id
        self.status.setText("Connecting…")

        def work():
            root = self.fb.db.collection("institutions")
            # Look up by syncCode, falling back to inviteCode for colleges that
            # have not opened this screen yet and so have no syncCode.
            matches = list(root.where("syncCode", "==", code).stream())
            if not matches:
                matches = list(root.where("inviteCode", "==", code).stream())
            if not matches:
                return {"error": "No college found with that code."}

            partner = matches[0]
            if partner.id == my_id:
                return {"error": "That is this college's own code."}

            pdata = partner.to_dict() or {}

            # Link both directions, so either side can see the other.
            from firebase_admin import firestore as _fs
            root.document(my_id).set(
                {"syncedWith": _fs.ArrayUnion([partner.id])}, merge=True
            )
            root.document(partner.id).set(
                {"syncedWith": _fs.ArrayUnion([my_id])}, merge=True
            )
            return {"name": pdata.get("name") or partner.id}

        def done(result):
            if not isinstance(result, dict) or "error" in result:
                QMessageBox.warning(
                    self, "Could not connect",
                    result.get("error", "Unknown error") if isinstance(result, dict) else "Unknown error",
                )
                self.status.setText("")
                return
            self.partner_input.clear()
            QMessageBox.information(
                self, "Connected", f"Now connected to {result['name']}."
            )
            self.refresh()

        self._run(work, done)

    def _disconnect(self, partner_id: str, partner_name: str):
        confirm = QMessageBox.question(
            self, "Disconnect",
            f"Disconnect from {partner_name}?\n\n"
            "Their catalogue will no longer appear in your union catalogue. "
            "No records are deleted on either side.",
        )
        if confirm != QMessageBox.StandardButton.Yes:
            return

        my_id = self.fb.college_id

        def work():
            from firebase_admin import firestore as _fs
            root = self.fb.db.collection("institutions")
            root.document(my_id).set(
                {"syncedWith": _fs.ArrayRemove([partner_id])}, merge=True
            )
            root.document(partner_id).set(
                {"syncedWith": _fs.ArrayRemove([my_id])}, merge=True
            )
            return {"ok": True}

        self._run(work, lambda _: self.refresh())
