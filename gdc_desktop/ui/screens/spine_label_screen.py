"""
ui/screens/spine_label_screen.py — Spine Label Generator & Printer.
Generates DDC/LC call number spine labels for physical library books.
Supports batch printing and PDF export.
"""
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QTableWidget, QTableWidgetItem, QHeaderView, QDialog,
    QFormLayout, QLineEdit, QMessageBox, QCheckBox, QFileDialog,
    QComboBox, QSpinBox, QFrame, QScrollArea
)
from PyQt6.QtCore import Qt, QThread, pyqtSignal
from PyQt6.QtGui import QColor, QFont, QPainter, QPixmap
import datetime


# ── DDC Schedule helper ─────────────────────────────────────────────────────
DDC_CLASSES = {
    "000": "Computer Science & General Works",
    "100": "Philosophy & Psychology",
    "200": "Religion",
    "300": "Social Sciences",
    "400": "Language",
    "500": "Natural Sciences & Mathematics",
    "600": "Applied Sciences & Technology",
    "700": "Arts & Recreation",
    "800": "Literature",
    "900": "History & Geography",
}


def suggest_ddc(category: str) -> str:
    """Simple DDC suggestion from category keywords."""
    cat = (category or "").lower()
    mapping = {
        "computer": "004", "software": "005", "data": "006",
        "philosophy": "100", "psychology": "150", "logic": "160",
        "religion": "200", "islam": "297", "christianity": "230",
        "social": "300", "economics": "330", "law": "340", "education": "370",
        "language": "400", "english": "420", "urdu": "491",
        "science": "500", "math": "510", "physics": "530", "chemistry": "540",
        "biology": "570", "botany": "580", "zoology": "590",
        "technology": "600", "medicine": "610", "engineering": "620",
        "agriculture": "630", "management": "658",
        "art": "700", "music": "780", "sports": "796",
        "literature": "800", "fiction": "823", "poetry": "811",
        "history": "900", "geography": "910", "pakistan": "954.91",
    }
    for kw, ddc in mapping.items():
        if kw in cat:
            return ddc
    return "020"


# ── Label Preview Widget ─────────────────────────────────────────────────────
class SpineLabelPreview(QFrame):
    """Visual spine label preview (50mm × 30mm approximate)."""

    def __init__(self, call_no: str, author_cut: str, year: str, title: str, parent=None):
        super().__init__(parent)
        self.call_no   = call_no
        self.author_cut = author_cut
        self.year      = year
        self.title     = title
        self.setFixedSize(100, 160)
        self.setObjectName("spineLabel")
        self.setStyleSheet(
            "QFrame#spineLabel { background: white; border: 2px solid #334155; border-radius: 4px; }"
        )

    def paintEvent(self, event):
        super().paintEvent(event)
        p = QPainter(self)
        p.setRenderHint(QPainter.RenderHint.Antialiasing)

        # White background
        p.fillRect(self.rect(), QColor("white"))

        # Top border strip (blue)
        p.fillRect(0, 0, self.width(), 18, QColor("#1E40AF"))

        # Title short (top white text on blue)
        p.setPen(QColor("white"))
        p.setFont(QFont("Segoe UI", 7, QFont.Weight.Bold))
        p.drawText(2, 2, self.width() - 4, 16, Qt.AlignmentFlag.AlignCenter, (self.title or "")[:16])

        # DDC Call number (large, centre)
        p.setPen(QColor("#0F172A"))
        p.setFont(QFont("Courier New", 13, QFont.Weight.Bold))
        p.drawText(0, 22, self.width(), 40, Qt.AlignmentFlag.AlignCenter, self.call_no or "---")

        # Author cutter
        p.setFont(QFont("Courier New", 10))
        p.drawText(0, 62, self.width(), 24, Qt.AlignmentFlag.AlignCenter, self.author_cut or "")

        # Year
        p.setFont(QFont("Courier New", 9))
        p.drawText(0, 86, self.width(), 20, Qt.AlignmentFlag.AlignCenter, self.year or "")

        # Bottom border
        p.fillRect(0, self.height() - 12, self.width(), 12, QColor("#1E40AF"))
        p.end()


# ── Label Edit Dialog ────────────────────────────────────────────────────────
class LabelEditDialog(QDialog):
    def __init__(self, book, parent=None):
        super().__init__(parent)
        self.book = book
        self.setWindowTitle(f"Spine Label — {book.title[:40] if book.title else 'Book'}")
        self.setMinimumWidth(520)
        self._build()

    def _build(self):
        lay = QVBoxLayout(self)
        lay.setContentsMargins(24, 20, 24, 20)
        lay.setSpacing(14)

        lay.addWidget(QLabel("🏷️  Spine Label Editor",
                             styleSheet="font-size:18px;font-weight:800;"))

        form = QFormLayout()
        form.setSpacing(10)

        # DDC number auto-suggest
        suggested = suggest_ddc(getattr(self.book, "category", "") or "")
        existing_call = getattr(self.book, "callNumber", "") or suggested

        self.call_no_edit = QLineEdit(existing_call)
        self.call_no_edit.setPlaceholderText("e.g. 005.13")
        self.call_no_edit.textChanged.connect(self._update_preview)

        # Author cutter (first 3 letters of last name + first letter of first name)
        author = getattr(self.book, "author", "") or ""
        parts = author.split()
        cutter = ""
        if parts:
            cutter = parts[-1][:3].upper()
            if len(parts) > 1:
                cutter += parts[0][0].upper()
        self.cutter_edit = QLineEdit(getattr(self.book, "authorCutter", "") or cutter)
        self.cutter_edit.textChanged.connect(self._update_preview)

        year = getattr(self.book, "publishDate", "") or str(datetime.date.today().year)
        self.year_edit = QLineEdit(year[:4] if len(year) >= 4 else year)
        self.year_edit.textChanged.connect(self._update_preview)

        self.copies_spin = QSpinBox()
        self.copies_spin.setRange(1, 100)
        self.copies_spin.setValue(1)

        form.addRow("DDC Call Number *", self.call_no_edit)
        form.addRow("Author Cutter *", self.cutter_edit)
        form.addRow("Year", self.year_edit)
        form.addRow("Copies to Print", self.copies_spin)
        lay.addLayout(form)

        # Suggest DDC label
        ddc_hint = DDC_CLASSES.get(suggested[:3], "")
        if ddc_hint:
            hint_lbl = QLabel(f"💡 Suggested class: {suggested[:3]} – {ddc_hint}")
            hint_lbl.setStyleSheet("color:#64748B;font-size:11px;")
            lay.addWidget(hint_lbl)

        # Preview
        preview_lbl = QLabel("Preview:")
        preview_lbl.setStyleSheet("font-weight:700;")
        lay.addWidget(preview_lbl)

        self._preview_container = QHBoxLayout()
        self._preview_container.setAlignment(Qt.AlignmentFlag.AlignLeft)
        lay.addLayout(self._preview_container)

        self._update_preview()

        # Buttons
        btn_row = QHBoxLayout()
        btn_row.addStretch()
        cancel = QPushButton("Cancel")
        cancel.clicked.connect(self.reject)
        save = QPushButton("💾  Save & Print")
        save.setStyleSheet(
            "background:qlineargradient(x1:0,y1:0,x2:1,y2:0,stop:0 #2563EB,stop:1 #3B82F6);"
            "color:white;border:none;border-radius:8px;padding:9px 20px;font-weight:700;"
        )
        save.clicked.connect(self.accept)
        btn_row.addWidget(cancel)
        btn_row.addWidget(save)
        lay.addLayout(btn_row)

    def _update_preview(self):
        # Remove old preview widgets
        while self._preview_container.count():
            item = self._preview_container.takeAt(0)
            if item.widget():
                item.widget().deleteLater()

        label = SpineLabelPreview(
            self.call_no_edit.text(),
            self.cutter_edit.text(),
            self.year_edit.text(),
            getattr(self.book, "title", "") or ""
        )
        self._preview_container.addWidget(label)

    def get_data(self):
        return {
            "callNumber": self.call_no_edit.text().strip(),
            "authorCutter": self.cutter_edit.text().strip(),
            "copies": self.copies_spin.value(),
        }


# ── Main Screen ──────────────────────────────────────────────────────────────
class SpineLabelScreen(QWidget):

    COLS = ["Acc No", "Title", "Author", "Category", "Call No", "Cutter", "Label Status"]

    def __init__(self, db_helper):
        super().__init__()
        self.db = db_helper
        self._books = []
        self._build_ui()
        self.refresh()

    def _build_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(28, 24, 28, 24)
        layout.setSpacing(14)

        # ── Header ─────────────────────────────────────────
        hdr = QHBoxLayout()
        title_col = QVBoxLayout()
        title = QLabel("🏷️  Spine Label Generator")
        title.setStyleSheet("font-size:22px;font-weight:800;font-family:'Segoe UI';")
        sub = QLabel("Generate DDC/LC call number spine labels for physical books. Print individually or in batch.")
        sub.setStyleSheet("color:#94A3B8;font-size:12px;")
        title_col.addWidget(title)
        title_col.addWidget(sub)
        hdr.addLayout(title_col)
        hdr.addStretch()

        edit_btn = QPushButton("✏️  Edit Label")
        edit_btn.setStyleSheet(
            "background:qlineargradient(x1:0,y1:0,x2:1,y2:0,stop:0 #2563EB,stop:1 #3B82F6);"
            "color:white;border:none;border-radius:8px;padding:9px 18px;font-weight:700;"
        )
        edit_btn.clicked.connect(self._edit_label)
        hdr.addWidget(edit_btn)

        batch_btn = QPushButton("🖨️  Batch Print PDF")
        batch_btn.setStyleSheet(
            "background:qlineargradient(x1:0,y1:0,x2:1,y2:0,stop:0 #059669,stop:1 #10B981);"
            "color:white;border:none;border-radius:8px;padding:9px 18px;font-weight:700;"
        )
        batch_btn.clicked.connect(self._batch_print)
        hdr.addWidget(batch_btn)

        layout.addLayout(hdr)

        # ── Search bar ─────────────────────────────────────
        search_row = QHBoxLayout()
        self.search_bar = QLineEdit()
        self.search_bar.setPlaceholderText("🔍  Search by title, author, Acc No, or category…")
        self.search_bar.textChanged.connect(self._filter)
        search_row.addWidget(self.search_bar)

        filter_cb = QComboBox()
        filter_cb.addItems(["All Books", "Labels Missing", "Labels Done"])
        filter_cb.currentTextChanged.connect(self._apply_status_filter)
        self._status_filter = "All Books"
        search_row.addWidget(filter_cb)

        layout.addLayout(search_row)

        # ── Stats strip ────────────────────────────────────
        self.stats_lbl = QLabel("")
        self.stats_lbl.setStyleSheet("color:#64748B;font-size:12px;")
        layout.addWidget(self.stats_lbl)

        # ── Table ──────────────────────────────────────────
        self.table = QTableWidget()
        self.table.setColumnCount(len(self.COLS))
        self.table.setHorizontalHeaderLabels(self.COLS)
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        self.table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.table.setAlternatingRowColors(True)
        self.table.doubleClicked.connect(self._edit_label)
        layout.addWidget(self.table)

        # ── DDC Reference Panel ────────────────────────────
        ref_frame = QFrame()
        ref_frame.setObjectName("Card")
        ref_lay = QVBoxLayout(ref_frame)
        ref_lay.setContentsMargins(16, 12, 16, 12)
        ref_lbl = QLabel("📖  DDC Class Reference  (Dewey Decimal Classification)")
        ref_lbl.setStyleSheet("font-weight:700;font-size:13px;")
        ref_lay.addWidget(ref_lbl)

        ddc_grid = QHBoxLayout()
        for code, name in list(DDC_CLASSES.items())[:5]:
            chip = QLabel(f"<b>{code}</b><br/><span style='font-size:10px;color:#94A3B8'>{name}</span>")
            chip.setStyleSheet("border:1px solid #334155;border-radius:6px;padding:6px;min-width:100px;")
            chip.setAlignment(Qt.AlignmentFlag.AlignCenter)
            ddc_grid.addWidget(chip)
        ref_lay.addLayout(ddc_grid)

        ddc_grid2 = QHBoxLayout()
        for code, name in list(DDC_CLASSES.items())[5:]:
            chip = QLabel(f"<b>{code}</b><br/><span style='font-size:10px;color:#94A3B8'>{name}</span>")
            chip.setStyleSheet("border:1px solid #334155;border-radius:6px;padding:6px;min-width:100px;")
            chip.setAlignment(Qt.AlignmentFlag.AlignCenter)
            ddc_grid2.addWidget(chip)
        ref_lay.addLayout(ddc_grid2)

        layout.addWidget(ref_frame)

    def refresh(self):
        try:
            self._books = self.db.get_books()
        except Exception:
            self._books = []
        self._populate(self._books)

    def _apply_status_filter(self, text: str):
        self._status_filter = text
        self._filter(self.search_bar.text())

    def _filter(self, q: str = ""):
        q = q.lower()
        filtered = [b for b in self._books if q in (
            (b.title or "") + (b.author or "") + (b.accNo or "") + (b.category or "")
        ).lower()]
        if self._status_filter == "Labels Missing":
            filtered = [b for b in filtered if not getattr(b, "callNumber", "")]
        elif self._status_filter == "Labels Done":
            filtered = [b for b in filtered if getattr(b, "callNumber", "")]
        self._populate(filtered)

    def _populate(self, books):
        done  = sum(1 for b in books if getattr(b, "callNumber", ""))
        total = len(books)
        self.stats_lbl.setText(
            f"Total: {total}  |  ✅ Labels Done: {done}  |  ⏳ Missing: {total - done}"
        )
        self.table.setRowCount(total)
        for row, b in enumerate(books):
            has_label = bool(getattr(b, "callNumber", ""))
            for col, val in enumerate([
                getattr(b, "accNo", "") or "",
                b.title or "",
                b.author or "",
                getattr(b, "category", "") or "",
                getattr(b, "callNumber", "") or "—",
                getattr(b, "authorCutter", "") or "—",
                "✅ Done" if has_label else "⏳ Missing"
            ]):
                item = QTableWidgetItem(val)
                if col == 6:
                    item.setForeground(QColor("#10B981" if has_label else "#F59E0B"))
                self.table.setItem(row, col, item)
        self._displayed_books = books

    def _edit_label(self):
        row = self.table.currentRow()
        books = getattr(self, "_displayed_books", self._books)
        if row < 0 or row >= len(books):
            QMessageBox.information(self, "Select", "Please select a book first.")
            return
        book = books[row]
        dlg = LabelEditDialog(book, self)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            data = dlg.get_data()
            book.callNumber   = data["callNumber"]
            book.authorCutter = data["authorCutter"]
            try:
                book.lastUpdated = int(__import__("time").time() * 1000)
                self.db.save_book(book)
                QMessageBox.information(
                    self, "Saved",
                    f"✅ Spine label saved for '{book.title}'.\n"
                    f"Call No: {book.callNumber} / {book.authorCutter}\n\n"
                    f"{data['copies']} label(s) ready for printing."
                )
                self.refresh()
            except Exception as e:
                QMessageBox.warning(self, "Error", str(e))

    def _batch_print(self):
        books_with_labels = [b for b in self._books if getattr(b, "callNumber", "")]
        if not books_with_labels:
            QMessageBox.information(
                self, "No Labels",
                "No books have call numbers assigned yet.\n"
                "Please edit books to add spine labels first."
            )
            return

        try:
            from services.advanced_service import HAS_REPORTLAB
            if not HAS_REPORTLAB:
                raise ImportError("reportlab not installed")

            path, _ = QFileDialog.getSaveFileName(
                self, "Save Batch Spine Labels", "SpineLabels_Batch.pdf", "PDF Files (*.pdf)"
            )
            if not path:
                return

            from reportlab.lib.pagesizes import A4
            from reportlab.lib.units import mm
            from reportlab.pdfgen import canvas as rl_canvas
            from reportlab.lib import colors

            c = rl_canvas.Canvas(path, pagesize=A4)
            W, H = A4
            x_start, y_start = 15 * mm, H - 20 * mm
            label_w, label_h = 50 * mm, 30 * mm
            cols_per_row = 4
            rows_per_page = int((H - 30 * mm) / (label_h + 5 * mm))

            col, row_num = 0, 0
            for book in books_with_labels:
                x = x_start + col * (label_w + 5 * mm)
                y = y_start - row_num * (label_h + 5 * mm)

                # Box
                c.setStrokeColor(colors.HexColor("#334155"))
                c.setFillColor(colors.HexColor("#1E40AF"))
                c.rect(x, y, label_w, 8 * mm, fill=1, stroke=0)

                # Title text (white on blue)
                c.setFillColor(colors.white)
                c.setFont("Helvetica-Bold", 7)
                short_title = (book.title or "")[:18]
                c.drawCentredString(x + label_w / 2, y + 2 * mm, short_title)

                # Call number
                c.setFillColor(colors.HexColor("#0F172A"))
                c.rect(x, y - 22 * mm, label_w, 22 * mm, fill=1, stroke=1)
                c.setFillColor(colors.HexColor("#1E3A8A"))
                c.setFont("Courier-Bold", 14)
                c.drawCentredString(x + label_w / 2, y - 10 * mm, book.callNumber or "")
                c.setFont("Courier", 10)
                c.setFillColor(colors.HexColor("#374151"))
                c.drawCentredString(x + label_w / 2, y - 16 * mm, getattr(book, "authorCutter", "") or "")

                col += 1
                if col >= cols_per_row:
                    col = 0
                    row_num += 1
                    if row_num >= rows_per_page:
                        c.showPage()
                        row_num = 0

            c.save()
            QMessageBox.information(
                self, "Batch Print Ready",
                f"✅ {len(books_with_labels)} spine labels exported to:\n{path}"
            )
            import os
            if os.name == "nt":
                os.startfile(path)

        except ImportError:
            QMessageBox.information(
                self, "Print Labels",
                f"📄 {len(books_with_labels)} books ready for spine labels.\n\n"
                "Install 'reportlab' for PDF export:\n  pip install reportlab"
            )
        except Exception as e:
            QMessageBox.warning(self, "Error", f"Could not generate PDF:\n{e}")
