"""
ui/screens/wishlist_screen.py — Purchase wishlist / book requests.

Desktop equivalent of the Android Wishlist screen. Members ask for titles the
library does not hold; the acquisitions librarian works the list by demand and
promotes an approved title straight into a purchase order.

Backed by /institutions/{collegeId}/wishlist through OperationsService, so a
request raised on a student's phone appears here and vice versa.
"""
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QTableWidget, QTableWidgetItem, QHeaderView, QLineEdit,
    QComboBox, QMessageBox, QDialog, QFormLayout, QDialogButtonBox,
)
from PyQt6.QtCore import Qt, QThread, pyqtSignal
from PyQt6.QtGui import QColor

from services.operations_service import OperationsService, WISHLIST_STATUSES

STATUS_COLORS = {
    "Requested": "#F59E0B",
    "UnderReview": "#3B82F6",
    "Approved": "#10B981",
    "Ordered": "#8B5CF6",
    "Declined": "#EF4444",
}


class _LoadWorker(QThread):
    finished = pyqtSignal(object)

    def __init__(self, ops):
        super().__init__()
        self.ops = ops

    def run(self):
        try:
            self.finished.emit(self.ops.wishlist())
        except Exception as e:
            print(f"Wishlist load failed: {e}")
            self.finished.emit([])


class WishlistScreen(QWidget):
    def __init__(self, firebase_service, auth_service=None):
        super().__init__()
        self.fb = firebase_service
        self.auth = auth_service
        self.ops = OperationsService(firebase_service)
        self._items = []
        self._worker = None
        self.init_ui()
        self.refresh()

    # ── UI ───────────────────────────────────────────────────────────────────

    def init_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(28, 24, 28, 24)
        layout.setSpacing(14)

        hdr = QHBoxLayout()
        title = QLabel("⭐  Purchase Wishlist")
        title.setStyleSheet(
            "font-size:22px;font-weight:800;color:#E8EEF8;font-family:'Segoe UI';"
        )
        hdr.addWidget(title)
        hdr.addStretch()

        self.btn_add = QPushButton("➕ Add Request")
        self.btn_add.setStyleSheet(self._btn("#1E5FD4", "#2872F0"))
        self.btn_add.clicked.connect(self._add_item)
        hdr.addWidget(self.btn_add)

        self.btn_refresh = QPushButton("🔄 Refresh")
        self.btn_refresh.setStyleSheet(self._btn("#0F766E", "#14B8A6"))
        self.btn_refresh.clicked.connect(self.refresh)
        hdr.addWidget(self.btn_refresh)
        layout.addLayout(hdr)

        sub = QLabel(
            "Titles members have asked the library to acquire, most requested "
            "first. Approving a title creates a purchase order in Acquisitions."
        )
        sub.setStyleSheet("color:#A0B4CC;font-size:13px;")
        sub.setWordWrap(True)
        layout.addWidget(sub)

        filter_row = QHBoxLayout()
        self.search = QLineEdit()
        self.search.setPlaceholderText("Search title or author…")
        self.search.setStyleSheet(
            "background:#0D1B2A;color:#E8EEF8;border:1px solid #1E3050;"
            "border-radius:8px;padding:8px 12px;font-size:13px;"
        )
        self.search.textChanged.connect(self._populate)
        filter_row.addWidget(self.search, stretch=1)

        self.status_filter = QComboBox()
        self.status_filter.addItems(["All"] + WISHLIST_STATUSES)
        self.status_filter.setStyleSheet(
            "background:#0D1B2A;color:#E8EEF8;border:1px solid #1E3050;"
            "border-radius:8px;padding:8px 12px;font-size:13px;"
        )
        self.status_filter.currentTextChanged.connect(self._populate)
        filter_row.addWidget(self.status_filter)
        layout.addLayout(filter_row)

        self.table = QTableWidget(0, 6)
        self.table.setHorizontalHeaderLabels(
            ["Title", "Author", "Requested by", "Votes", "Status", "Action"]
        )
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        self.table.setStyleSheet(
            "QTableWidget{background:#0D1B2A;color:#E8EEF8;border:1px solid #1E3050;}"
            "QHeaderView::section{background:#1E3050;color:#E8EEF8;padding:6px;}"
        )
        layout.addWidget(self.table)

        self.status_lbl = QLabel("")
        self.status_lbl.setStyleSheet("color:#A0B4CC;font-size:12px;")
        layout.addWidget(self.status_lbl)

    @staticmethod
    def _btn(c1: str, c2: str) -> str:
        return (
            f"background:qlineargradient(x1:0,y1:0,x2:1,y2:0,stop:0 {c1},stop:1 {c2});"
            "color:white;border:none;border-radius:8px;padding:9px 18px;"
            "font-size:13px;font-weight:700;"
        )

    # ── Data ─────────────────────────────────────────────────────────────────

    def refresh(self):
        if self.fb.mock_mode or not self.fb.college_id:
            self.status_lbl.setText("Offline, or not signed in to an institution.")
            return
        self.status_lbl.setText("Loading…")
        self._worker = _LoadWorker(self.ops)
        self._worker.finished.connect(self._on_loaded)
        self._worker.start()

    def _on_loaded(self, items):
        self._items = items or []
        self._populate()
        self.status_lbl.setText(f"{len(self._items)} requested title(s).")

    def _populate(self):
        query = self.search.text().strip().lower()
        wanted_status = self.status_filter.currentText()

        rows = self._items
        if wanted_status != "All":
            rows = [r for r in rows if (r.get("status") or "") == wanted_status]
        if query:
            rows = [
                r for r in rows
                if query in (r.get("title") or "").lower()
                or query in (r.get("author") or "").lower()
            ]

        self.table.setRowCount(0)
        for i, item in enumerate(rows):
            self.table.insertRow(i)
            self.table.setItem(i, 0, QTableWidgetItem(item.get("title") or "—"))
            self.table.setItem(i, 1, QTableWidgetItem(item.get("author") or "—"))
            self.table.setItem(i, 2, QTableWidgetItem(item.get("requestedByName") or "—"))

            votes = QTableWidgetItem(str(item.get("votes") or 1))
            votes.setTextAlignment(Qt.AlignmentFlag.AlignCenter)
            self.table.setItem(i, 3, votes)

            status = item.get("status") or "Requested"
            status_cell = QTableWidgetItem(status)
            status_cell.setForeground(QColor(STATUS_COLORS.get(status, "#A0B4CC")))
            self.table.setItem(i, 4, status_cell)

            combo = QComboBox()
            combo.addItems(WISHLIST_STATUSES)
            combo.setCurrentText(status)
            combo.setStyleSheet(
                "background:#1E3050;color:#E8EEF8;border:none;"
                "border-radius:6px;padding:3px 8px;font-size:12px;"
            )
            combo.currentTextChanged.connect(
                lambda new_status, rec=item: self._change_status(rec, new_status)
            )
            self.table.setCellWidget(i, 5, combo)

    # ── Actions ──────────────────────────────────────────────────────────────

    def _change_status(self, record: dict, new_status: str):
        if (record.get("status") or "") == new_status:
            return
        if not self.ops.set_wishlist_status(record.get("syncId"), new_status):
            QMessageBox.warning(self, "Error", "Could not update the request.")
            return
        record["status"] = new_status

        # Approving a title is the point at which it becomes an acquisition, so
        # offer to raise the purchase order rather than making the librarian
        # retype it in another screen.
        if new_status == "Approved":
            ask = QMessageBox.question(
                self, "Raise purchase order?",
                f"Create a purchase order for \"{record.get('title')}\"?",
            )
            if ask == QMessageBox.StandardButton.Yes:
                self._raise_po_for(record)
        self.refresh()

    def _raise_po_for(self, record: dict):
        dialog = QDialog(self)
        dialog.setWindowTitle("New purchase order")
        form = QFormLayout(dialog)

        vendor = QLineEdit()
        qty = QLineEdit("1")
        price = QLineEdit()
        form.addRow("Vendor:", vendor)
        form.addRow("Quantity:", qty)
        form.addRow("Unit price:", price)

        buttons = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        buttons.accepted.connect(dialog.accept)
        buttons.rejected.connect(dialog.reject)
        form.addRow(buttons)

        if dialog.exec() != QDialog.DialogCode.Accepted:
            return
        if not vendor.text().strip():
            QMessageBox.warning(self, "Error", "A vendor is required.")
            return

        try:
            quantity = int(qty.text().strip() or "1")
            unit_price = float(price.text().strip() or "0")
        except ValueError:
            QMessageBox.warning(self, "Error", "Quantity and price must be numbers.")
            return

        po_id = self.ops.raise_purchase_order(
            vendor=vendor.text().strip(),
            book_title=record.get("title") or "",
            quantity=quantity,
            unit_price=unit_price,
            isbn=record.get("isbn") or "",
            notes=f"Raised from wishlist request ({record.get('votes') or 1} votes).",
        )
        if po_id:
            self.ops.set_wishlist_status(record.get("syncId"), "Ordered")
            QMessageBox.information(self, "Ordered", "Purchase order created.")
        else:
            QMessageBox.warning(self, "Error", "Could not create the purchase order.")

    def _add_item(self):
        dialog = QDialog(self)
        dialog.setWindowTitle("Add a wishlist request")
        form = QFormLayout(dialog)

        title = QLineEdit()
        author = QLineEdit()
        isbn = QLineEdit()
        member = QLineEdit()
        reason = QLineEdit()
        form.addRow("Title:", title)
        form.addRow("Author:", author)
        form.addRow("ISBN:", isbn)
        form.addRow("Requested by:", member)
        form.addRow("Reason:", reason)

        buttons = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        buttons.accepted.connect(dialog.accept)
        buttons.rejected.connect(dialog.reject)
        form.addRow(buttons)

        if dialog.exec() != QDialog.DialogCode.Accepted:
            return
        if not title.text().strip():
            QMessageBox.warning(self, "Error", "A title is required.")
            return

        # add_wishlist_item upvotes an existing entry with the same title rather
        # than creating a duplicate, so demand stays visible in one row.
        if self.ops.add_wishlist_item(
            title=title.text().strip(),
            author=author.text().strip(),
            isbn=isbn.text().strip(),
            member_name=member.text().strip(),
            reason=reason.text().strip(),
        ):
            self.refresh()
        else:
            QMessageBox.warning(self, "Error", "Could not save the request.")
