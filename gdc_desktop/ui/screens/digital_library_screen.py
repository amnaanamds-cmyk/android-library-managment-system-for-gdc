"""
ui/screens/digital_library_screen.py — Dedicated Digital Repository & E-Book Reader.
Supports PDF viewing, digital resource management, and rich categorization.
"""
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QLineEdit, QPushButton,
    QTableWidget, QTableWidgetItem, QHeaderView, QFrame, QFileDialog,
    QMessageBox, QComboBox, QSplitter, QScrollArea, QProgressBar
)
from PyQt6.QtCore import Qt, QUrl
from PyQt6.QtGui import QColor, QDesktopServices
from models import Book

class DigitalLibraryScreen(QWidget):
    def __init__(self, db_helper):
        super().__init__()
        self.db = db_helper
        self._books = []
        self._build_ui()
        self.refresh()

    def _build_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(32, 24, 32, 24)
        layout.setSpacing(20)

        # Header
        hdr = QHBoxLayout()
        title_v = QVBoxLayout()
        title = QLabel("🌐  DIGITAL REPOSITORY")
        title.setStyleSheet("font-size: 24px; font-weight: 800; color: #3B82F6;")
        sub = QLabel("Access institutional E-Books, Journals, and PDF resources.")
        title_v.addWidget(title); title_v.addWidget(sub)
        hdr.addLayout(title_v)
        hdr.addStretch()

        upload_btn = QPushButton("➕ Upload New Resource")
        upload_btn.setStyleSheet("background: #2563EB; color: white; border-radius: 8px; padding: 10px 20px; font-weight: bold;")
        upload_btn.clicked.connect(self._upload_digital)
        hdr.addWidget(upload_btn)
        layout.addLayout(hdr)

        # Search & Filter
        search_frame = QFrame()
        search_frame.setObjectName("Card")
        s_lay = QHBoxLayout(search_frame)

        self.search = QLineEdit()
        self.search.setPlaceholderText("Search digital catalog...")
        self.search.textChanged.connect(self._filter)
        s_lay.addWidget(self.search, stretch=3)

        self.cat_filter = QComboBox()
        self.cat_filter.addItem("All Categories")
        self.cat_filter.currentTextChanged.connect(self._filter)
        s_lay.addWidget(self.cat_filter, stretch=1)

        layout.addWidget(search_frame)

        # Main Splitter: List and Preview
        self.splitter = QSplitter(Qt.Orientation.Horizontal)

        # 1. Digital List Table
        self.table = QTableWidget()
        self.table.setColumnCount(4)
        self.table.setHorizontalHeaderLabels(["Title", "Author", "Type", "Status"])
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        self.table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table.setAlternatingRowColors(True)
        self.table.itemSelectionChanged.connect(self._on_select)
        self.splitter.addWidget(self.table)

        # 2. Resource Detail / Preview Panel
        self.preview_panel = QFrame()
        self.preview_panel.setObjectName("Card")
        self.preview_lay = QVBoxLayout(self.preview_panel)
        self.preview_lay.setContentsMargins(20, 20, 20, 20)

        self.prev_title = QLabel("Select a resource")
        self.prev_title.setStyleSheet("font-size: 18px; font-weight: 700;")
        self.prev_title.setWordWrap(True)
        self.preview_lay.addWidget(self.prev_title)

        self.prev_meta = QLabel("")
        self.preview_lay.addWidget(self.prev_meta)

        self.preview_lay.addStretch()

        self.read_btn = QPushButton("📖 READ NOW")
        self.read_btn.setStyleSheet("background: #10B981; color: white; font-weight: 900; border-radius: 8px; padding: 15px; font-size: 14px;")
        self.read_btn.hide()
        self.read_btn.clicked.connect(self._read_now)
        self.preview_lay.addWidget(self.read_btn)

        self.splitter.addWidget(self.preview_panel)
        self.splitter.setStretchFactor(0, 3)
        self.splitter.setStretchFactor(1, 1)

        layout.addWidget(self.splitter)

    def refresh(self):
        all_books = self.db.get_books()
        self._books = [b for b in all_books if b.isDigital]

        cats = sorted(list(set(b.category for b in self._books if b.category)))
        self.cat_filter.clear()
        self.cat_filter.addItem("All Categories")
        self.cat_filter.addItems(cats)

        self._filter()

    def _filter(self):
        q = self.search.text().lower()
        cat = self.cat_filter.currentText()

        filtered = [b for b in self._books
                    if (not q or q in b.title.lower() or q in b.author.lower())
                    and (cat == "All Categories" or b.category == cat)]

        self.table.setRowCount(len(filtered))
        for r, b in enumerate(filtered):
            self.table.setItem(r, 0, QTableWidgetItem(b.title))
            self.table.setItem(r, 1, QTableWidgetItem(b.author))
            self.table.setItem(r, 2, QTableWidgetItem(b.category))
            self.table.setItem(r, 3, QTableWidgetItem("Available"))

        self._current_filtered = filtered

    def _on_select(self):
        row = self.table.currentRow()
        if row < 0: return

        book = self._current_filtered[row]
        self.prev_title.setText(book.title)
        self.prev_meta.setText(f"Author: {book.author}\nCategory: {book.category}\nISBN: {book.isbn}")
        self.read_btn.show()
        self._selected_book = book

    def _read_now(self):
        if not hasattr(self, '_selected_book'): return
        url = self._selected_book.digitalUrl
        if url:
            if url.startswith("http"):
                QDesktopServices.openUrl(QUrl(url))
            else:
                # Handle local path
                import os
                if os.path.exists(url):
                    QDesktopServices.openUrl(QUrl.fromLocalFile(url))
                else:
                    QMessageBox.warning(self, "Not Found", f"Resource file not found at: {url}")

    def _upload_digital(self):
        path, _ = QFileDialog.getOpenFileName(self, "Upload Digital Resource", "", "PDF Files (*.pdf);;EPUB (*.epub)")
        if path:
            # Simple simulation: just create a book record
            from PyQt6.QtWidgets import QInputDialog
            title, ok = QInputDialog.getText(self, "Resource Title", "Enter Title:")
            if ok and title:
                author, ok2 = QInputDialog.getText(self, "Author", "Enter Author:")
                if ok2:
                    new_b = Book(
                        title=title,
                        author=author,
                        isDigital=True,
                        digitalUrl=path,
                        category="Digital Repository"
                    )
                    self.db.save_book(new_b)
                    self.refresh()
                    QMessageBox.information(self, "Success", "Digital resource added to institutional repository.")
