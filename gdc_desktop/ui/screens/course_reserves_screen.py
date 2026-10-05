"""
ui/screens/course_reserves_screen.py — Course Reserves (Koha-style).

A book stays in normal circulation, but while it is listed here for a
course it loans for a much shorter period than the institution's normal
borrowDurationDays — e.g. 1-3 days instead of 14 — the same idea as a
"reading room only" or "overnight" loan a physical reserve shelf uses.
Any student can still check it out (NEXLIB has no course-enrollment data
to restrict against); IssueDialog in issue_return_screen.py looks the book
up here and uses the shorter period automatically, with a visible label
so the librarian can see why.

Backed by /institutions/{collegeId}/course_reserves through
OperationsService — desktop-only for now; see operations_service.py's
Course Reserves section for why that is a safe, additive gap rather than
a cross-platform disagreement.
"""
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QTableWidget, QTableWidgetItem, QHeaderView, QLineEdit,
    QComboBox, QMessageBox, QDialog, QFormLayout, QDialogButtonBox,
    QSpinBox, QDoubleSpinBox,
)
from PyQt6.QtCore import Qt, QThread, pyqtSignal
from PyQt6.QtGui import QColor

from services.operations_service import OperationsService, COURSE_RESERVE_STATUSES

STATUS_COLORS = {
    "Active": "#10B981",
    "Ended": "#6B8CAE",
}


class _LoadWorker(QThread):
    finished = pyqtSignal(object)

    def __init__(self, ops):
        super().__init__()
        self.ops = ops

    def run(self):
        try:
            self.finished.emit(self.ops.course_reserves())
        except Exception as e:
            print(f"Course reserves load failed: {e}")
            self.finished.emit([])


class AddReserveDialog(QDialog):
    def __init__(self, books, default_loan_days: int, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Add Course Reserve")
        self.setMinimumWidth(440)
        # Only books not already deleted are offered — a reserve references
        # a specific copy, so there is nothing to shorten the loan period of
        # once that copy is gone.
        self._books = books
        self._build(default_loan_days)

    def _build(self, default_loan_days: int):
        form = QFormLayout(self)
        form.setSpacing(10)

        self.course_code = QLineEdit()
        self.course_code.setPlaceholderText("e.g. CS-301")
        self.course_name = QLineEdit()
        self.course_name.setPlaceholderText("e.g. Database Systems")
        self.instructor = QLineEdit()
        self.term = QLineEdit()
        self.term.setPlaceholderText("e.g. Fall 2026")

        self.book_cb = QComboBox()
        self.book_cb.addItems([f"[{b.accNo}]  {b.title}" for b in self._books])
        self.book_cb.setEditable(True)

        self.loan_days = QSpinBox()
        self.loan_days.setRange(1, 30)
        self.loan_days.setValue(min(default_loan_days, 3) if default_loan_days else 3)

        self.fine_rate = QDoubleSpinBox()
        self.fine_rate.setRange(0, 1000)
        self.fine_rate.setDecimals(2)
        self.fine_rate.setSpecialValueText("Use normal fine rate")

        form.addRow("Course Code *", self.course_code)
        form.addRow("Course Name *", self.course_name)
        form.addRow("Instructor", self.instructor)
        form.addRow("Term", self.term)
        form.addRow("Book *", self.book_cb)
        form.addRow("Reserve Loan Days *", self.loan_days)
        form.addRow("Reserve Fine Rate/day", self.fine_rate)

        buttons = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        buttons.accepted.connect(self.accept)
        buttons.rejected.connect(self.reject)
        form.addRow(buttons)

    def get_data(self):
        idx = self.book_cb.currentIndex()
        book = self._books[idx] if 0 <= idx < len(self._books) else None
        return {
            "course_code": self.course_code.text().strip(),
            "course_name": self.course_name.text().strip(),
            "instructor": self.instructor.text().strip(),
            "term": self.term.text().strip(),
            "book": book,
            "loan_days": self.loan_days.value(),
            "fine_rate": self.fine_rate.value(),
        }


class CourseReservesScreen(QWidget):
    def __init__(self, firebase_service, db_helper, auth_service=None):
        super().__init__()
        self.fb = firebase_service
        self.db = db_helper
        self.auth = auth_service
        self.ops = OperationsService(firebase_service)
        self._items = []
        self._worker = None
        self._build_ui()
        self.refresh()

    def _build_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(28, 24, 28, 24)
        layout.setSpacing(14)

        hdr = QHBoxLayout()
        title = QLabel("📚  Course Reserves")
        title.setStyleSheet("font-size:22px;font-weight:800;color:#E8EEF8;font-family:'Segoe UI';")
        hdr.addWidget(title)
        hdr.addStretch()

        self.btn_add = QPushButton("➕ Add Reserve")
        self.btn_add.setStyleSheet(self._btn("#1E5FD4", "#2872F0"))
        self.btn_add.clicked.connect(self._add_reserve)
        hdr.addWidget(self.btn_add)

        self.btn_refresh = QPushButton("🔄 Refresh")
        self.btn_refresh.setStyleSheet(self._btn("#0F766E", "#14B8A6"))
        self.btn_refresh.clicked.connect(self.refresh)
        hdr.addWidget(self.btn_refresh)
        layout.addLayout(hdr)

        sub = QLabel(
            "Books listed here loan for a short, course-specific period instead of the "
            "normal loan period — the book still shows Available and anyone may check it "
            "out, but Issue Book will default to the shorter due date automatically."
        )
        sub.setStyleSheet("color:#A0B4CC;font-size:13px;")
        sub.setWordWrap(True)
        layout.addWidget(sub)

        filter_row = QHBoxLayout()
        self.search = QLineEdit()
        self.search.setPlaceholderText("Search course, instructor, or book…")
        self.search.setStyleSheet(
            "background:#0D1B2A;color:#E8EEF8;border:1px solid #1E3050;"
            "border-radius:8px;padding:8px 12px;font-size:13px;"
        )
        self.search.textChanged.connect(self._populate)
        filter_row.addWidget(self.search, stretch=1)

        self.status_filter = QComboBox()
        self.status_filter.addItems(["All"] + COURSE_RESERVE_STATUSES)
        self.status_filter.setStyleSheet(
            "background:#0D1B2A;color:#E8EEF8;border:1px solid #1E3050;"
            "border-radius:8px;padding:8px 12px;font-size:13px;"
        )
        self.status_filter.currentTextChanged.connect(self._populate)
        filter_row.addWidget(self.status_filter)
        layout.addLayout(filter_row)

        self.table = QTableWidget(0, 7)
        self.table.setHorizontalHeaderLabels(
            ["Course", "Instructor", "Term", "Book", "Loan Days", "Status", "Action"]
        )
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        self.table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
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
        self.status_lbl.setText(f"{len(self._items)} course reserve(s).")

    def _populate(self):
        query = self.search.text().strip().lower()
        wanted_status = self.status_filter.currentText()

        rows = self._items
        if wanted_status != "All":
            rows = [r for r in rows if (r.get("status") or "") == wanted_status]
        if query:
            rows = [
                r for r in rows
                if query in (r.get("courseCode") or "").lower()
                or query in (r.get("courseName") or "").lower()
                or query in (r.get("instructor") or "").lower()
                or query in (r.get("bookTitle") or "").lower()
            ]

        self.table.setRowCount(0)
        for i, item in enumerate(rows):
            self.table.insertRow(i)
            course = f"{item.get('courseCode') or ''}  {item.get('courseName') or ''}".strip()
            self.table.setItem(i, 0, QTableWidgetItem(course or "—"))
            self.table.setItem(i, 1, QTableWidgetItem(item.get("instructor") or "—"))
            self.table.setItem(i, 2, QTableWidgetItem(item.get("term") or "—"))
            self.table.setItem(i, 3, QTableWidgetItem(item.get("bookTitle") or "—"))
            self.table.setItem(i, 4, QTableWidgetItem(str(item.get("loanDays") or "—")))

            status = item.get("status") or "Active"
            status_cell = QTableWidgetItem(status)
            status_cell.setForeground(QColor(STATUS_COLORS.get(status, "#A0B4CC")))
            self.table.setItem(i, 5, status_cell)

            if status == "Active":
                end_btn = QPushButton("End Reserve")
                end_btn.setStyleSheet(
                    "background:#1E3050;color:#F59E0B;border:none;border-radius:6px;"
                    "padding:3px 10px;font-size:12px;"
                )
                end_btn.clicked.connect(lambda _, rec=item: self._end_reserve(rec))
                self.table.setCellWidget(i, 6, end_btn)
            else:
                self.table.setCellWidget(i, 6, None)

    # ── Actions ──────────────────────────────────────────────────────────────

    def _add_reserve(self):
        try:
            books = [b for b in self.db.get_books() if not b.deleted]
        except Exception:
            books = []
        if not books:
            QMessageBox.information(self, "No Books", "There are no books to put on reserve yet.")
            return

        default_days = 3
        try:
            default_days = self.fb.get_library_settings().borrowDurationDays
        except Exception:
            pass

        dlg = AddReserveDialog(books, default_days, self)
        if dlg.exec() != QDialog.DialogCode.Accepted:
            return
        data = dlg.get_data()
        if not data["course_code"] or not data["course_name"] or not data["book"]:
            QMessageBox.warning(self, "Required", "Course code, course name, and book are required.")
            return

        book = data["book"]
        sync_id = self.ops.add_course_reserve(
            course_code=data["course_code"], course_name=data["course_name"],
            instructor=data["instructor"], term=data["term"],
            book_id=book.id, book_title=book.title, book_isbn=book.isbn,
            loan_days=data["loan_days"], fine_rate=data["fine_rate"],
        )
        if sync_id:
            self.refresh()
        else:
            QMessageBox.warning(self, "Error", "Could not save the course reserve.")

    def _end_reserve(self, record: dict):
        reply = QMessageBox.question(
            self, "End Reserve",
            f"End the course reserve for \"{record.get('bookTitle')}\"? "
            "It will go back to the normal loan period.",
        )
        if reply != QMessageBox.StandardButton.Yes:
            return
        if self.ops.end_course_reserve(record.get("syncId")):
            self.refresh()
        else:
            QMessageBox.warning(self, "Error", "Could not end the reserve.")
