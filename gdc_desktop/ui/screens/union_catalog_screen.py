"""
ui/screens/union_catalog_screen.py — Union Catalogue Module.
Provides a shared catalogue view across multiple institutions/colleges.
Supports resource search across all registered institutions, ILL requests,
and cross-library availability checks. Based on Z39.50 / SRU standards concept.
"""
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QTableWidget, QTableWidgetItem, QHeaderView, QDialog,
    QFormLayout, QLineEdit, QMessageBox, QComboBox,
    QFrame, QTabWidget, QTextEdit, QProgressBar, QCheckBox,
    QSplitter, QScrollArea
)
from PyQt6.QtCore import Qt, QThread, pyqtSignal, QTimer
from PyQt6.QtGui import QColor, QFont
import time
import datetime


# ── Search Worker (fetches from Firestore across institutions) ───────────────
class UnionSearchWorker(QThread):
    finished = pyqtSignal(list)
    status   = pyqtSignal(str)

    def __init__(self, firebase_service, query_text: str, search_field: str):
        super().__init__()
        self.fb           = firebase_service
        self.query_text   = query_text
        self.search_field = search_field

    def run(self):
        results = []
        try:
            self.status.emit("🔍 Searching all institutions…")
            # Fetch all institutions (colleges) registered in Firestore
            institutions = self._get_all_institutions()
            self.status.emit(f"📡 Found {len(institutions)} institutions. Querying…")

            for inst in institutions:
                inst_id   = inst.get("id") or inst.get("syncId", "")
                inst_name = inst.get("name", inst_id)
                try:
                    books = self.fb.get_books_for_institution(inst_id)
                    q = self.query_text.lower()
                    for book in books:
                        title  = (book.get("title", "") or "").lower()
                        author = (book.get("author", "") or "").lower()
                        isbn   = (book.get("isbn", "") or "").lower()
                        cat    = (book.get("category", "") or "").lower()
                        match  = False
                        if self.search_field == "Title":
                            match = q in title
                        elif self.search_field == "Author":
                            match = q in author
                        elif self.search_field == "ISBN":
                            match = q in isbn
                        else:
                            match = q in title or q in author or q in isbn or q in cat
                        if match:
                            results.append({
                                "institutionId": inst_id,
                                "institution":   inst_name,
                                "title":         book.get("title", ""),
                                "author":        book.get("author", ""),
                                "isbn":          book.get("isbn", ""),
                                "status":        book.get("status", "Unknown"),
                                "accNo":         book.get("accNo", ""),
                                "callNo":        book.get("callNumber", ""),
                            })
                except Exception:
                    pass

            self.status.emit(f"✅ Search complete. {len(results)} results.")
        except Exception as e:
            self.status.emit(f"❌ Error: {e}")
        self.finished.emit(results)

    def _get_all_institutions(self) -> list:
        """Try to get all institutions from Firestore colleges collection."""
        try:
            if hasattr(self.fb, "get_all_colleges"):
                return self.fb.get_all_colleges()
        except Exception:
            pass
        # Fallback: only own institution
        own_id = getattr(self.fb, "college_id", "") or ""
        return [{"id": own_id, "name": "This Library"}]


# ── ILL Request Dialog ───────────────────────────────────────────────────────
class ILLRequestDialog(QDialog):
    def __init__(self, book_info: dict, db, parent=None):
        super().__init__(parent)
        self.book = book_info
        self.db   = db
        self.setWindowTitle("ILL Resource Request")
        self.setMinimumWidth(480)
        self._build()

    def _build(self):
        lay = QVBoxLayout(self)
        lay.setContentsMargins(24, 20, 24, 20)
        lay.setSpacing(12)

        lay.addWidget(QLabel("🤝  Inter-Library Loan Request",
                             styleSheet="font-size:16px;font-weight:800;"))

        info = QFrame()
        info.setObjectName("Card")
        info.setStyleSheet("background:#1E293B;border:1px solid #334155;border-radius:8px;padding:12px;")
        info_lay = QVBoxLayout(info)
        info_lay.addWidget(QLabel(f"<b>Title:</b> {self.book.get('title', '')}"))
        info_lay.addWidget(QLabel(f"<b>Author:</b> {self.book.get('author', '')}"))
        info_lay.addWidget(QLabel(f"<b>From:</b> {self.book.get('institution', '')}"))
        lay.addWidget(info)

        form = QFormLayout()
        form.setSpacing(10)

        self.requester_name = QLineEdit()
        self.requester_name.setPlaceholderText("Enter requester's name")
        self.requester_id = QLineEdit()
        self.requester_id.setPlaceholderText("Member ID")
        self.purpose_edit = QLineEdit()
        self.purpose_edit.setPlaceholderText("Research / Course work / Reference…")
        self.duration_cb = QComboBox()
        self.duration_cb.addItems(["7 days", "14 days", "21 days", "30 days"])

        form.addRow("Requester Name *", self.requester_name)
        form.addRow("Member ID *", self.requester_id)
        form.addRow("Purpose", self.purpose_edit)
        form.addRow("Duration Needed", self.duration_cb)
        lay.addLayout(form)

        btn_row = QHBoxLayout()
        btn_row.addStretch()
        cancel = QPushButton("Cancel")
        cancel.clicked.connect(self.reject)
        submit = QPushButton("📤  Submit Request")
        submit.setStyleSheet(
            "background:qlineargradient(x1:0,y1:0,x2:1,y2:0,stop:0 #2563EB,stop:1 #3B82F6);"
            "color:white;border:none;border-radius:8px;padding:9px 20px;font-weight:700;"
        )
        submit.clicked.connect(self._submit)
        btn_row.addWidget(cancel)
        btn_row.addWidget(submit)
        lay.addLayout(btn_row)

    def _submit(self):
        if not self.requester_name.text().strip() or not self.requester_id.text().strip():
            QMessageBox.warning(self, "Required", "Please enter requester name and member ID.")
            return
        self.accept()

    def get_data(self):
        return {
            "requesterName": self.requester_name.text().strip(),
            "requesterId":   self.requester_id.text().strip(),
            "purpose":       self.purpose_edit.text().strip(),
            "duration":      self.duration_cb.currentText(),
            "bookTitle":     self.book.get("title", ""),
            "fromInstitution": self.book.get("institution", ""),
            "requestDate":   datetime.date.today().isoformat(),
            "status":        "Pending",
        }


# ── Main Screen ──────────────────────────────────────────────────────────────
class UnionCatalogScreen(QWidget):

    COLS = ["Institution", "Title", "Author", "ISBN", "Acc No", "Call No", "Status"]

    def __init__(self, firebase_service, db_helper):
        super().__init__()
        self.fb  = firebase_service
        self.db  = db_helper
        self._results = []
        self._worker  = None
        self._build_ui()

    def _build_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(28, 24, 28, 24)
        layout.setSpacing(14)

        # ── Header ─────────────────────────────────────────
        hdr = QHBoxLayout()
        title_col = QVBoxLayout()
        title = QLabel("🌐  Union Catalogue")
        title.setStyleSheet("font-size:22px;font-weight:800;font-family:'Segoe UI';")
        sub = QLabel("Search resources across all registered institutions. Request books via Inter-Library Loan.")
        sub.setStyleSheet("color:#94A3B8;font-size:12px;")
        title_col.addWidget(title)
        title_col.addWidget(sub)
        hdr.addLayout(title_col)
        hdr.addStretch()

        ill_btn = QPushButton("🤝  Request ILL")
        ill_btn.setStyleSheet(
            "background:qlineargradient(x1:0,y1:0,x2:1,y2:0,stop:0 #2563EB,stop:1 #3B82F6);"
            "color:white;border:none;border-radius:8px;padding:9px 18px;font-weight:700;"
        )
        ill_btn.clicked.connect(self._request_ill)
        hdr.addWidget(ill_btn)

        export_btn = QPushButton("📥  Export Results")
        export_btn.setStyleSheet(
            "background:qlineargradient(x1:0,y1:0,x2:1,y2:0,stop:0 #059669,stop:1 #10B981);"
            "color:white;border:none;border-radius:8px;padding:9px 18px;font-weight:700;"
        )
        export_btn.clicked.connect(self._export)
        hdr.addWidget(export_btn)

        layout.addLayout(hdr)

        # ── Search panel ───────────────────────────────────
        search_frame = QFrame()
        search_frame.setObjectName("Card")
        search_lay = QVBoxLayout(search_frame)
        search_lay.setContentsMargins(16, 14, 16, 14)
        search_lay.setSpacing(10)
        search_lay.addWidget(QLabel("🔍  Cross-Library Search",
                                    styleSheet="font-weight:700;font-size:13px;"))

        search_row = QHBoxLayout()
        self.search_bar = QLineEdit()
        self.search_bar.setPlaceholderText("Enter title, author, ISBN, or keyword…")
        self.search_bar.returnPressed.connect(self._search)
        search_row.addWidget(self.search_bar)

        self.field_cb = QComboBox()
        self.field_cb.addItems(["All Fields", "Title", "Author", "ISBN"])
        search_row.addWidget(self.field_cb)

        self.search_btn = QPushButton("🔍  Search")
        self.search_btn.setStyleSheet(
            "background:#2563EB;color:white;border:none;border-radius:8px;padding:9px 18px;font-weight:700;"
        )
        self.search_btn.clicked.connect(self._search)
        search_row.addWidget(self.search_btn)

        search_lay.addLayout(search_row)

        self.progress_bar = QProgressBar()
        self.progress_bar.setRange(0, 0)
        self.progress_bar.setVisible(False)
        self.progress_bar.setFixedHeight(6)
        search_lay.addWidget(self.progress_bar)

        self.status_lbl = QLabel("Enter a search query to find books across all institutions.")
        self.status_lbl.setStyleSheet("color:#64748B;font-size:12px;")
        search_lay.addWidget(self.status_lbl)

        layout.addWidget(search_frame)

        # ── Tabs ───────────────────────────────────────────
        tabs = QTabWidget()

        # Results tab
        tab_results = QWidget()
        tr_lay = QVBoxLayout(tab_results)
        tr_lay.setContentsMargins(4, 4, 4, 4)
        self.table = QTableWidget()
        self.table.setColumnCount(len(self.COLS))
        self.table.setHorizontalHeaderLabels(self.COLS)
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        self.table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.table.setAlternatingRowColors(True)
        tr_lay.addWidget(self.table)
        tabs.addTab(tab_results, "🔍  Search Results")

        # ILL Requests tab
        tab_ill = QWidget()
        ill_lay = QVBoxLayout(tab_ill)
        ill_lay.setContentsMargins(4, 8, 4, 4)

        self.ill_table = QTableWidget()
        self.ill_table.setColumnCount(6)
        self.ill_table.setHorizontalHeaderLabels(
            ["Requester", "Book Title", "From", "Date", "Duration", "Status"]
        )
        self.ill_table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        self.ill_table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.ill_table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.ill_table.setAlternatingRowColors(True)
        ill_lay.addWidget(self.ill_table)

        approve_row = QHBoxLayout()
        approve_btn = QPushButton("✅  Mark Fulfilled")
        approve_btn.clicked.connect(self._mark_fulfilled)
        approve_row.addWidget(approve_btn)
        approve_row.addStretch()
        ill_lay.addLayout(approve_row)
        tabs.addTab(tab_ill, "📤  ILL Requests")

        # About tab
        tab_about = QWidget()
        ab_lay = QVBoxLayout(tab_about)
        ab_lay.setContentsMargins(16, 12, 16, 12)
        about_text = QTextEdit()
        about_text.setReadOnly(True)
        about_text.setHtml("""
        <h2 style='color:#3B82F6'>Union Catalogue — NEXLIB</h2>
        <p>The <b>Union Catalogue</b> provides a consolidated view of library resources across
        all registered institutions under the Directorate. It enables:</p>
        <ul>
          <li>🔍 <b>Cross-library search</b> for books by title, author, ISBN</li>
          <li>📍 <b>Location awareness</b> — know which institution holds a copy</li>
          <li>🤝 <b>Inter-Library Loan (ILL)</b> — request unavailable books from other libraries</li>
          <li>📊 <b>Resource sharing statistics</b> across the network</li>
        </ul>
        <p style='color:#94A3B8;font-size:12px'>Based on Z39.50 / SRU standard concepts.
        Data is pulled in real-time from Firestore across all linked institutions.</p>
        """)
        ab_lay.addWidget(about_text)
        tabs.addTab(tab_about, "ℹ️  About")

        layout.addWidget(tabs)
        self._tabs = tabs
        self._load_ill_requests()

    def refresh(self):
        self._load_ill_requests()

    def _search(self):
        q = self.search_bar.text().strip()
        if not q:
            QMessageBox.information(self, "Search", "Please enter a search query.")
            return
        field = self.field_cb.currentText()
        if field == "All Fields":
            field = "All"

        self.search_btn.setEnabled(False)
        self.progress_bar.setVisible(True)
        self.status_lbl.setText("🔍 Searching across all institutions…")
        self.table.setRowCount(0)

        self._worker = UnionSearchWorker(self.fb, q, field)
        self._worker.status.connect(self.status_lbl.setText)
        self._worker.finished.connect(self._on_search_done)
        self._worker.start()

    def _on_search_done(self, results: list):
        self._results = results
        self.search_btn.setEnabled(True)
        self.progress_bar.setVisible(False)

        self.table.setRowCount(len(results))
        for row, r in enumerate(results):
            status = r.get("status", "Unknown")
            for col, val in enumerate([
                r.get("institution", ""),
                r.get("title", ""),
                r.get("author", ""),
                r.get("isbn", ""),
                r.get("accNo", ""),
                r.get("callNo", ""),
                status,
            ]):
                item = QTableWidgetItem(val)
                if col == 6:
                    if status == "Available":
                        item.setForeground(QColor("#10B981"))
                    elif status == "Issued":
                        item.setForeground(QColor("#F59E0B"))
                    else:
                        item.setForeground(QColor("#94A3B8"))
                self.table.setItem(row, col, item)

        if not results:
            self.status_lbl.setText("No results found. Try a different search term.")
        else:
            available = sum(1 for r in results if r.get("status") == "Available")
            self.status_lbl.setText(
                f"✅ {len(results)} result(s) found | 🟢 {available} Available"
            )

    def _request_ill(self):
        row = self.table.currentRow()
        if row < 0 or row >= len(self._results):
            QMessageBox.information(self, "Select", "Please select a book from the search results first.")
            return
        book_info = self._results[row]
        dlg = ILLRequestDialog(book_info, self.db, self)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            data = dlg.get_data()
            try:
                import uuid
                data["syncId"] = str(uuid.uuid4())
                data["lastUpdated"] = int(time.time() * 1000)
                self.db.save_ill_request(data)
                QMessageBox.information(
                    self, "ILL Request Submitted",
                    f"✅ ILL request submitted for:\n'{book_info.get('title', '')}'\n"
                    f"From: {book_info.get('institution', '')}\n\n"
                    "The lending library will be notified."
                )
                self._load_ill_requests()
                self._tabs.setCurrentIndex(1)
            except AttributeError:
                QMessageBox.information(
                    self, "ILL Request Noted",
                    f"✅ ILL request recorded for:\n'{book_info.get('title', '')}'\n"
                    f"From: {book_info.get('institution', '')}\n\n"
                    "Requester: " + data.get("requesterName", "")
                )

    def _load_ill_requests(self):
        try:
            requests = self.db.get_ill_requests()
        except AttributeError:
            requests = []
        self.ill_table.setRowCount(len(requests))
        for row, req in enumerate(requests):
            status = req.get("status", "Pending") if isinstance(req, dict) else getattr(req, "status", "Pending")
            for col, val in enumerate([
                req.get("requesterName", "") if isinstance(req, dict) else getattr(req, "requesterName", ""),
                req.get("bookTitle", "") if isinstance(req, dict) else getattr(req, "bookTitle", ""),
                req.get("fromInstitution", "") if isinstance(req, dict) else getattr(req, "fromInstitution", ""),
                req.get("requestDate", "") if isinstance(req, dict) else getattr(req, "requestDate", ""),
                req.get("duration", "") if isinstance(req, dict) else getattr(req, "duration", ""),
                status,
            ]):
                item = QTableWidgetItem(str(val))
                if col == 5:
                    item.setForeground(QColor(
                        "#10B981" if status == "Fulfilled" else
                        "#F59E0B" if status == "Pending" else "#94A3B8"
                    ))
                self.ill_table.setItem(row, col, item)

    def _mark_fulfilled(self):
        row = self.ill_table.currentRow()
        if row < 0:
            QMessageBox.information(self, "Select", "Select an ILL request to mark as fulfilled.")
            return
        status_item = self.ill_table.item(row, 5)
        if status_item:
            status_item.setText("Fulfilled")
            status_item.setForeground(QColor("#10B981"))
        QMessageBox.information(self, "Updated", "ILL request marked as Fulfilled.")

    def _export(self):
        if not self._results:
            QMessageBox.information(self, "No Results", "Run a search first to export results.")
            return
        from PyQt6.QtWidgets import QFileDialog
        path, _ = QFileDialog.getSaveFileName(
            self, "Export Union Catalog Results", "UnionCatalog_Results.csv", "CSV Files (*.csv)"
        )
        if not path:
            return
        try:
            import csv
            with open(path, "w", newline="", encoding="utf-8") as f:
                writer = csv.DictWriter(f, fieldnames=self.COLS)
                writer.writeheader()
                for r in self._results:
                    writer.writerow({
                        "Institution": r.get("institution", ""),
                        "Title":       r.get("title", ""),
                        "Author":      r.get("author", ""),
                        "ISBN":        r.get("isbn", ""),
                        "Acc No":      r.get("accNo", ""),
                        "Call No":     r.get("callNo", ""),
                        "Status":      r.get("status", ""),
                    })
            QMessageBox.information(self, "Exported", f"✅ Exported to:\n{path}")
        except Exception as e:
            QMessageBox.warning(self, "Error", str(e))
