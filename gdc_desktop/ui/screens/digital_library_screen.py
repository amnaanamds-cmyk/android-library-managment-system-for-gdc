"""
ui/screens/digital_library_screen.py — Dedicated Digital Repository & E-Book Reader.
Supports PDF viewing, digital resource management, and rich categorization.

Uploading used to be a "Simple simulation: just create a book record" —
digitalUrl held the path to the file ON THE UPLOADER'S OWN PC, invisible to
every other device. See services/storage_service.py for why and how that's
now a real Firebase Storage upload instead.
"""
import os
import uuid

from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QLineEdit, QPushButton,
    QTableWidget, QTableWidgetItem, QHeaderView, QFrame, QFileDialog,
    QMessageBox, QComboBox, QSplitter, QScrollArea, QProgressBar, QInputDialog
)
from PyQt6.QtCore import Qt, QUrl, QThread, pyqtSignal
from PyQt6.QtGui import QColor, QDesktopServices
from models import Book
from services.storage_service import StorageService, MAX_UPLOAD_BYTES, MAX_UPLOAD_MB, looks_like_storage_path


class DigitalUploadWorker(QThread):
    finished = pyqtSignal(bool, str)

    def __init__(self, storage: StorageService, local_path: str, dest_filename: str):
        super().__init__()
        self.storage = storage
        self.local_path = local_path
        self.dest_filename = dest_filename

    def run(self):
        ok, result = self.storage.upload(self.local_path, f"digital/{self.dest_filename}")
        self.finished.emit(ok, result)


class SignedUrlWorker(QThread):
    finished = pyqtSignal(str)

    def __init__(self, storage: StorageService, storage_path: str):
        super().__init__()
        self.storage = storage
        self.storage_path = storage_path

    def run(self):
        self.finished.emit(self.storage.signed_url(self.storage_path) or "")


class DigitalLibraryScreen(QWidget):
    def __init__(self, db_helper, firebase_service=None):
        super().__init__()
        self.db = db_helper
        self.storage = StorageService(firebase_service) if firebase_service else None
        self._books = []
        self._upload_worker = None
        self._url_worker = None
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

        self.upload_btn = QPushButton("➕ Upload New Resource")
        self.upload_btn.setStyleSheet("background: #2563EB; color: white; border-radius: 8px; padding: 10px 20px; font-weight: bold;")
        self.upload_btn.clicked.connect(self._upload_digital)
        hdr.addWidget(self.upload_btn)
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
        if not url:
            return

        if url.startswith("http"):
            QDesktopServices.openUrl(QUrl(url))
            return

        if looks_like_storage_path(url):
            if not self.storage:
                QMessageBox.warning(self, "Not Available", "Cloud storage isn't connected on this device.")
                return
            self.read_btn.setEnabled(False)
            self.read_btn.setText("📖 Fetching…")
            self._url_worker = SignedUrlWorker(self.storage, url)
            self._url_worker.finished.connect(self._on_signed_url_ready)
            self._url_worker.start()
            return

        # Legacy records from before real upload existed: digitalUrl is a
        # bare local path, and this only works if it happens to exist on
        # THIS machine — the exact limitation that made the old feature
        # effectively single-device. Left in place so those old records
        # don't regress to "always fails" on the machine that made them.
        if os.path.exists(url):
            QDesktopServices.openUrl(QUrl.fromLocalFile(url))
        else:
            QMessageBox.warning(self, "Not Found",
                                 f"This resource was uploaded before cloud storage was wired up, and its file "
                                 f"only ever existed on the PC that added it. Not found here: {url}")

    def _on_signed_url_ready(self, url: str):
        self.read_btn.setEnabled(True)
        self.read_btn.setText("📖 READ NOW")
        if url:
            QDesktopServices.openUrl(QUrl(url))
        else:
            QMessageBox.warning(self, "Not Available",
                                 "Couldn't generate a download link — check your internet connection and try again.")

    def _upload_digital(self):
        if not self.storage:
            QMessageBox.warning(self, "Not Available", "Cloud storage isn't connected on this device.")
            return

        path, _ = QFileDialog.getOpenFileName(self, "Upload Digital Resource", "", "PDF Files (*.pdf);;EPUB (*.epub)")
        if not path:
            return

        size = os.path.getsize(path)
        if size > MAX_UPLOAD_BYTES:
            QMessageBox.warning(
                self, "File Too Large",
                f"This file is {size / 1024 / 1024:.1f} MB. Digital resources are capped at {MAX_UPLOAD_MB} MB "
                f"each — the free storage plan is shared across every college in the network, so one large file "
                f"uses space every other college's uploads draw from too."
            )
            return

        title, ok = QInputDialog.getText(self, "Resource Title", "Enter Title:")
        if not (ok and title):
            return
        author, ok2 = QInputDialog.getText(self, "Author", "Enter Author:")
        if not ok2:
            return

        self.upload_btn.setEnabled(False)
        self.upload_btn.setText("➕ Uploading…")

        ext = os.path.splitext(path)[1]
        dest_filename = f"{uuid.uuid4()}{ext}"
        self._pending_book = Book(title=title, author=author, isDigital=True, category="Digital Repository")

        self._upload_worker = DigitalUploadWorker(self.storage, path, dest_filename)
        self._upload_worker.finished.connect(self._on_upload_finished)
        self._upload_worker.start()

    def _on_upload_finished(self, ok: bool, result: str):
        self.upload_btn.setEnabled(True)
        self.upload_btn.setText("➕ Upload New Resource")

        if not ok:
            # A failed upload must not create a book record pointing at a
            # file that was never actually stored — that would just be the
            # same lie in a new shape.
            QMessageBox.warning(self, "Upload Failed", f"The file was not uploaded: {result}")
            return

        self._pending_book.digitalUrl = result  # the storage path, not a public URL — see StorageService
        self.db.save_book(self._pending_book)
        self.refresh()
        QMessageBox.information(self, "Success",
                                 "Digital resource uploaded and added to the institutional repository. "
                                 "It's now reachable from any of this college's signed-in devices.")
