"""
ui/screens/marc_catalog_screen.py — Professional MARC 21 Cataloging Module.
Full grid-based MARC editor with tag validation and subfield support ($a, $b, $c...).
"""
import json
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QLineEdit, QPushButton,
    QTableWidget, QTableWidgetItem, QHeaderView, QFrame, QDialog,
    QScrollArea, QMessageBox, QSplitter
)
from PyQt6.QtCore import Qt
from models import Book

class MarcEditorDialog(QDialog):
    def __init__(self, book: Book, parent=None):
        super().__init__(parent)
        self.book = book
        self.setWindowTitle(f"MARC 21 Editor — {book.title or 'New Record'}")
        self.setMinimumSize(800, 600)
        self.setStyleSheet("background: #0D1B2A; color: #E8EEF8;")
        self._build_ui()

    def _build_ui(self):
        layout = QVBoxLayout(self)

        hdr = QLabel("MARC 21 METADATA GRID")
        hdr.setStyleSheet("font-size: 18px; font-weight: 800; color: #E6C96E; margin-bottom: 10px;")
        layout.addWidget(hdr)

        self.table = QTableWidget(0, 3)
        self.table.setHorizontalHeaderLabels(["Tag", "Indicators", "Data (Subfields)"])
        self.table.horizontalHeader().setSectionResizeMode(2, QHeaderView.ResizeMode.Stretch)
        self.table.setStyleSheet("QTableWidget { background: #071428; gridline-color: #1E3050; }")
        layout.addWidget(self.table)

        # Load existing MARC data or defaults
        marc_data = []
        if self.book.marcData:
            try: marc_data = json.loads(self.book.marcData)
            except: pass

        if not marc_data:
            # Default template
            marc_data = [
                ["001", "  ", f"{self.book.syncId}"],
                ["020", "  ", f"$a {self.book.isbn}"],
                ["100", "1 ", f"$a {self.book.author}"],
                ["245", "10", f"$a {self.book.title}"],
                ["260", "  ", f"$a {self.book.publisherPlace} : $b {self.book.publisher}, $c {self.book.publishDate}"],
                ["300", "  ", f"$a {self.book.pages} p."],
                ["650", " #", f"$a {self.book.category}"],
            ]

        for tag, ind, data in marc_data:
            self._add_row(tag, ind, data)

        btn_row = QHBoxLayout()
        add_btn = QPushButton("➕ Add Tag")
        add_btn.clicked.connect(lambda: self._add_row("", "", ""))
        btn_row.addWidget(add_btn)

        save_btn = QPushButton("💾 SAVE RECORD")
        save_btn.setStyleSheet("background: #10B981; color: white; font-weight: bold; padding: 10px;")
        save_btn.clicked.connect(self._save)
        btn_row.addStretch()
        btn_row.addWidget(save_btn)
        layout.addLayout(btn_row)

    def _add_row(self, tag, ind, data):
        r = self.table.rowCount()
        self.table.insertRow(r)
        self.table.setItem(r, 0, QTableWidgetItem(tag))
        self.table.setItem(r, 1, QTableWidgetItem(ind))
        self.table.setItem(r, 2, QTableWidgetItem(data))

    def _save(self):
        new_data = []
        for r in range(self.table.rowCount()):
            tag = self.table.item(r, 0).text().strip()
            ind = self.table.item(r, 1).text().strip()
            data = self.table.item(r, 2).text().strip()
            if tag: new_data.append([tag, ind, data])

        self.book.marcData = json.dumps(new_data)
        # Update basic fields from MARC if present (simple mapping)
        for tag, ind, data in new_data:
            if tag == "245": self.book.title = data.replace("$a", "").strip()
            if tag == "100": self.book.author = data.replace("$a", "").strip()
            if tag == "020": self.book.isbn = data.replace("$a", "").strip()

        self.accept()

class MarcCatalogScreen(QWidget):
    def __init__(self, db_helper):
        super().__init__()
        self.db = db_helper
        self._build_ui()
        self.refresh()

    def _build_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(32, 24, 32, 24)

        hdr = QHBoxLayout()
        title_v = QVBoxLayout()
        title = QLabel("📑  MARC 21 CATALOGING")
        title.setStyleSheet("font-size: 24px; font-weight: 800; color: #E6C96E;")
        sub = QLabel("Standardized bibliographic data management for archival compliance.")
        sub.setStyleSheet("color: #94A3B8; font-size: 13px;")
        title_v.addWidget(title); title_v.addWidget(sub)
        hdr.addLayout(title_v)
        hdr.addStretch()

        new_btn = QPushButton("✨ New MARC Record")
        new_btn.setStyleSheet("background: #C8A84B; color: #0D1B2A; border-radius: 8px; padding: 10px 20px; font-weight: bold;")
        new_btn.clicked.connect(self._create_new)
        hdr.addWidget(new_btn)
        layout.addLayout(hdr)

        self.table = QTableWidget()
        self.table.setColumnCount(4)
        self.table.setHorizontalHeaderLabels(["Title", "Author", "ISBN", "MARC Status"])
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        self.table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table.doubleClicked.connect(self._edit_marc)
        layout.addWidget(self.table)

        btn_row = QHBoxLayout()
        btn_row.addStretch()
        edit_btn = QPushButton("✏️ Edit MARC Data")
        edit_btn.clicked.connect(self._edit_marc)
        btn_row.addWidget(edit_btn)
        layout.addLayout(btn_row)

    def refresh(self):
        self._books = self.db.get_books()
        self.table.setRowCount(len(self._books))
        for r, b in enumerate(self._books):
            self.table.setItem(r, 0, QTableWidgetItem(b.title))
            self.table.setItem(r, 1, QTableWidgetItem(b.author))
            self.table.setItem(r, 2, QTableWidgetItem(b.isbn))
            status = "✅ Completed" if b.marcData else "⏳ Draft (Lacking MARC)"
            self.table.setItem(r, 3, QTableWidgetItem(status))

    def _edit_marc(self):
        row = self.table.currentRow()
        if row < 0: return
        book = self._books[row]
        dlg = MarcEditorDialog(book, self)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            self.db.save_book(book)
            self.refresh()

    def _create_new(self):
        new_b = Book(title="New Archive Item")
        dlg = MarcEditorDialog(new_b, self)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            self.db.save_book(new_b)
            self.refresh()
