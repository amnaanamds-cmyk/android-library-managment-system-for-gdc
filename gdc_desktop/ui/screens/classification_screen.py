"""
ui/screens/classification_screen.py — Library Book Classification Module.
DDC (Dewey Decimal Classification) & LC (Library of Congress) classification system.
Provides classification schedules, auto-suggest, batch classify, and conflict detection.
"""
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton,
    QTableWidget, QTableWidgetItem, QHeaderView, QDialog,
    QFormLayout, QLineEdit, QMessageBox, QComboBox,
    QFrame, QTabWidget, QTextEdit, QTreeWidget, QTreeWidgetItem,
    QSplitter, QScrollArea
)
from PyQt6.QtCore import Qt, QThread, pyqtSignal
from PyQt6.QtGui import QColor, QFont
import time


# ── Full DDC Schedule ────────────────────────────────────────────────────────
DDC_SCHEDULE = {
    "000": {
        "name": "Computer Science & General Works",
        "subs": {
            "001": "Knowledge",
            "002": "The Book",
            "004": "Data Processing",
            "005": "Computer Programming",
            "006": "Special Computer Methods",
            "010": "Bibliography",
            "020": "Library Science",
            "030": "General Encyclopedias",
        }
    },
    "100": {
        "name": "Philosophy & Psychology",
        "subs": {
            "100": "Philosophy",
            "120": "Epistemology",
            "150": "Psychology",
            "160": "Philosophical Logic",
            "170": "Ethics",
            "180": "Ancient Philosophy",
            "190": "Modern Philosophy",
        }
    },
    "200": {
        "name": "Religion",
        "subs": {
            "200": "Religion",
            "210": "Natural Theology",
            "220": "Bible",
            "230": "Christian Theology",
            "290": "Other Religions",
            "297": "Islam",
        }
    },
    "300": {
        "name": "Social Sciences",
        "subs": {
            "300": "Social Sciences",
            "310": "Statistics",
            "320": "Political Science",
            "330": "Economics",
            "340": "Law",
            "350": "Public Administration",
            "360": "Social Services",
            "370": "Education",
            "380": "Commerce",
            "390": "Customs",
        }
    },
    "400": {
        "name": "Language",
        "subs": {
            "400": "Language",
            "410": "Linguistics",
            "420": "English",
            "430": "German",
            "440": "French",
            "491": "Urdu",
            "492": "Arabic",
        }
    },
    "500": {
        "name": "Natural Sciences",
        "subs": {
            "500": "Natural Sciences",
            "510": "Mathematics",
            "520": "Astronomy",
            "530": "Physics",
            "540": "Chemistry",
            "550": "Earth Sciences",
            "560": "Paleontology",
            "570": "Biology",
            "580": "Botany",
            "590": "Zoology",
        }
    },
    "600": {
        "name": "Applied Sciences",
        "subs": {
            "600": "Applied Sciences",
            "610": "Medicine",
            "620": "Engineering",
            "630": "Agriculture",
            "640": "Home Economics",
            "650": "Management",
            "658": "Business Management",
            "660": "Chemical Engineering",
            "670": "Manufacturing",
            "680": "Manufacture for Specific Uses",
            "690": "Construction",
        }
    },
    "700": {
        "name": "Arts & Recreation",
        "subs": {
            "700": "The Arts",
            "710": "Landscape",
            "720": "Architecture",
            "730": "Sculpture",
            "740": "Drawing",
            "750": "Painting",
            "760": "Graphic Arts",
            "770": "Photography",
            "780": "Music",
            "790": "Recreation",
            "796": "Sports",
        }
    },
    "800": {
        "name": "Literature",
        "subs": {
            "800": "Literature",
            "810": "American Literature",
            "811": "American Poetry",
            "820": "English Literature",
            "821": "English Poetry",
            "823": "English Fiction",
            "891": "Urdu Literature",
        }
    },
    "900": {
        "name": "History & Geography",
        "subs": {
            "900": "History",
            "910": "Geography",
            "920": "Biography",
            "930": "History of Ancient World",
            "940": "History of Europe",
            "950": "History of Asia",
            "954": "South Asia",
            "954.91": "Pakistan",
            "960": "History of Africa",
            "970": "History of North America",
        }
    },
}

# LC Classification top level
LC_SCHEDULE = {
    "A":  "General Works",
    "B":  "Philosophy / Psychology / Religion",
    "C":  "Auxiliary Sciences of History",
    "D":  "World History",
    "E":  "History of the Americas",
    "F":  "Local History of Americas",
    "G":  "Geography / Anthropology",
    "H":  "Social Sciences",
    "J":  "Political Science",
    "K":  "Law",
    "L":  "Education",
    "M":  "Music",
    "N":  "Fine Arts",
    "P":  "Language & Literature",
    "Q":  "Science",
    "R":  "Medicine",
    "S":  "Agriculture",
    "T":  "Technology",
    "U":  "Military Science",
    "V":  "Naval Science",
    "Z":  "Bibliography / Library Science",
}


def auto_classify_ddc(title: str, author: str, category: str, publisher: str) -> str:
    """Keyword-based DDC auto-classification."""
    text = f"{title} {category} {publisher}".lower()
    rules = [
        ("computer", "004"), ("programming", "005"), ("software", "005"),
        ("data", "006"), ("database", "005.74"), ("network", "004.6"),
        ("artificial intelligence", "006.3"), ("machine learning", "006.31"),
        ("library", "020"), ("bibliography", "010"),
        ("philosophy", "100"), ("ethics", "170"), ("psychology", "150"),
        ("logic", "160"), ("metaphysics", "110"),
        ("islam", "297"), ("quran", "297.1"), ("hadith", "297.2"),
        ("christianity", "230"), ("bible", "220"), ("religion", "200"),
        ("economics", "330"), ("law", "340"), ("education", "370"),
        ("political", "320"), ("sociology", "301"), ("statistics", "310"),
        ("mathematics", "510"), ("algebra", "512"), ("calculus", "515"),
        ("geometry", "516"), ("statistics", "519"),
        ("physics", "530"), ("chemistry", "540"), ("biology", "570"),
        ("botany", "580"), ("zoology", "590"), ("astronomy", "520"),
        ("medicine", "610"), ("engineering", "620"), ("civil eng", "624"),
        ("electrical", "621.3"), ("mechanical", "620.1"),
        ("agriculture", "630"), ("management", "658"), ("business", "650"),
        ("accounting", "657"), ("marketing", "658.8"),
        ("art", "700"), ("music", "780"), ("sports", "796"),
        ("poetry", "808.1"), ("fiction", "823"), ("literature", "800"),
        ("urdu", "491.439"), ("history", "900"), ("pakistan", "954.91"),
        ("geography", "910"), ("biography", "920"),
    ]
    for kw, code in rules:
        if kw in text:
            return code
    return "020"


# ── Classify Dialog ──────────────────────────────────────────────────────────
class ClassifyDialog(QDialog):
    def __init__(self, book, parent=None):
        super().__init__(parent)
        self.book = book
        self.setWindowTitle(f"Classify — {(book.title or '')[:50]}")
        self.setMinimumSize(600, 500)
        self._build()

    def _build(self):
        lay = QVBoxLayout(self)
        lay.setContentsMargins(24, 20, 24, 20)
        lay.setSpacing(12)

        lay.addWidget(QLabel("🗂️  Book Classification",
                             styleSheet="font-size:18px;font-weight:800;"))
        lay.addWidget(QLabel(f"<b>{self.book.title or 'Untitled'}</b>  by {self.book.author or '—'}",
                             styleSheet="color:#64748B;"))

        # System selector
        sys_row = QHBoxLayout()
        sys_row.addWidget(QLabel("Classification System:"))
        self.sys_cb = QComboBox()
        self.sys_cb.addItems(["DDC (Dewey Decimal)", "LC (Library of Congress)"])
        self.sys_cb.currentTextChanged.connect(self._update_schedule)
        sys_row.addWidget(self.sys_cb)
        sys_row.addStretch()
        lay.addLayout(sys_row)

        # Auto suggest
        suggested = auto_classify_ddc(
            self.book.title or "",
            self.book.author or "",
            getattr(self.book, "category", "") or "",
            getattr(self.book, "publisher", "") or ""
        )
        suggest_row = QHBoxLayout()
        sug_lbl = QLabel(f"🤖  AI Suggested Class: <b style='color:#3B82F6'>{suggested}</b>")
        suggest_row.addWidget(sug_lbl)
        use_btn = QPushButton("Use This")
        use_btn.setStyleSheet(
            "background:#1E40AF;color:white;border:none;border-radius:6px;padding:5px 14px;font-weight:600;"
        )
        use_btn.clicked.connect(lambda: self.class_edit.setText(suggested))
        suggest_row.addWidget(use_btn)
        suggest_row.addStretch()
        lay.addLayout(suggest_row)

        form = QFormLayout()
        form.setSpacing(10)
        existing_class = getattr(self.book, "classificationNo", "") or suggested
        self.class_edit = QLineEdit(existing_class)
        self.class_edit.setPlaceholderText("e.g. 005.13 or QA76.73")

        self.subject_edit = QLineEdit(getattr(self.book, "subjectHeading", "") or "")
        self.subject_edit.setPlaceholderText("e.g. Computer Programming — Python")

        self.notes_edit = QLineEdit(getattr(self.book, "classificationNotes", "") or "")
        self.notes_edit.setPlaceholderText("Any classification notes...")

        form.addRow("Classification No. *", self.class_edit)
        form.addRow("Subject Heading", self.subject_edit)
        form.addRow("Notes", self.notes_edit)
        lay.addLayout(form)

        # DDC schedule tree
        lay.addWidget(QLabel("📚  DDC Schedule — click to insert:",
                             styleSheet="font-weight:700;margin-top:8px;"))
        self.tree = QTreeWidget()
        self.tree.setMaximumHeight(180)
        self.tree.setHeaderHidden(True)
        self.tree.itemDoubleClicked.connect(self._insert_from_tree)
        self._populate_tree()
        lay.addWidget(self.tree)

        # Buttons
        btn_row = QHBoxLayout()
        btn_row.addStretch()
        cancel = QPushButton("Cancel")
        cancel.clicked.connect(self.reject)
        save = QPushButton("✅  Classify & Save")
        save.setStyleSheet(
            "background:qlineargradient(x1:0,y1:0,x2:1,y2:0,stop:0 #059669,stop:1 #10B981);"
            "color:white;border:none;border-radius:8px;padding:9px 20px;font-weight:700;"
        )
        save.clicked.connect(self.accept)
        btn_row.addWidget(cancel)
        btn_row.addWidget(save)
        lay.addLayout(btn_row)

    def _populate_tree(self):
        self.tree.clear()
        for code, data in DDC_SCHEDULE.items():
            parent = QTreeWidgetItem([f"{code}  —  {data['name']}"])
            parent.setData(0, Qt.ItemDataRole.UserRole, code)
            for sub_code, sub_name in data["subs"].items():
                child = QTreeWidgetItem([f"{sub_code}  —  {sub_name}"])
                child.setData(0, Qt.ItemDataRole.UserRole, sub_code)
                parent.addChild(child)
            self.tree.addTopLevelItem(parent)

    def _update_schedule(self, system: str):
        if "LC" in system:
            self.tree.clear()
            for code, name in LC_SCHEDULE.items():
                item = QTreeWidgetItem([f"{code}  —  {name}"])
                item.setData(0, Qt.ItemDataRole.UserRole, code)
                self.tree.addTopLevelItem(item)
        else:
            self._populate_tree()

    def _insert_from_tree(self, item, _col):
        code = item.data(0, Qt.ItemDataRole.UserRole)
        if code:
            self.class_edit.setText(code)

    def get_data(self):
        return {
            "classificationNo": self.class_edit.text().strip(),
            "subjectHeading":   self.subject_edit.text().strip(),
            "classificationNotes": self.notes_edit.text().strip(),
            "classificationSystem": self.sys_cb.currentText().split(" ")[0],
        }


# ── Main Screen ──────────────────────────────────────────────────────────────
class ClassificationScreen(QWidget):

    COLS = ["Acc No", "Title", "Author", "Category", "Class No", "Subject Heading", "System", "Status"]

    def __init__(self, db_helper):
        super().__init__()
        self.db = db_helper
        self._books = []
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
        title = QLabel("🗂️  Book Classification")
        title.setStyleSheet("font-size:22px;font-weight:800;font-family:'Segoe UI';")
        sub = QLabel("Assign DDC or LC classification numbers to library books. Auto-suggest based on category & keywords.")
        sub.setStyleSheet("color:#94A3B8;font-size:12px;")
        title_col.addWidget(title)
        title_col.addWidget(sub)
        hdr.addLayout(title_col)
        hdr.addStretch()

        classify_btn = QPushButton("🗂️  Classify Selected")
        classify_btn.setStyleSheet(
            "background:qlineargradient(x1:0,y1:0,x2:1,y2:0,stop:0 #2563EB,stop:1 #3B82F6);"
            "color:white;border:none;border-radius:8px;padding:9px 18px;font-weight:700;"
        )
        classify_btn.clicked.connect(self._classify)
        hdr.addWidget(classify_btn)

        auto_btn = QPushButton("🤖  Auto-Classify All Unclassified")
        auto_btn.setStyleSheet(
            "background:qlineargradient(x1:0,y1:0,x2:1,y2:0,stop:0 #7C3AED,stop:1 #8B5CF6);"
            "color:white;border:none;border-radius:8px;padding:9px 18px;font-weight:700;"
        )
        auto_btn.clicked.connect(self._auto_classify_all)
        hdr.addWidget(auto_btn)

        layout.addLayout(hdr)

        # ── Filter row ─────────────────────────────────────
        filter_row = QHBoxLayout()
        self.search_bar = QLineEdit()
        self.search_bar.setPlaceholderText("🔍  Search books…")
        self.search_bar.textChanged.connect(self._filter)
        filter_row.addWidget(self.search_bar)

        self.filter_cb = QComboBox()
        self.filter_cb.addItems(["All", "Unclassified", "DDC Classified", "LC Classified"])
        self.filter_cb.currentTextChanged.connect(lambda _: self._filter())
        filter_row.addWidget(self.filter_cb)
        layout.addLayout(filter_row)

        # ── Stats ──────────────────────────────────────────
        self.stats_lbl = QLabel("")
        self.stats_lbl.setStyleSheet("color:#64748B;font-size:12px;")
        layout.addWidget(self.stats_lbl)

        # ── Tabs ───────────────────────────────────────────
        tabs = QTabWidget()

        # Books table tab
        tab_books = QWidget()
        tb_lay = QVBoxLayout(tab_books)
        tb_lay.setContentsMargins(4, 4, 4, 4)
        self.table = QTableWidget()
        self.table.setColumnCount(len(self.COLS))
        self.table.setHorizontalHeaderLabels(self.COLS)
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        self.table.setSelectionBehavior(QTableWidget.SelectionBehavior.SelectRows)
        self.table.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        self.table.setAlternatingRowColors(True)
        self.table.doubleClicked.connect(self._classify)
        tb_lay.addWidget(self.table)
        tabs.addTab(tab_books, "📚  Books")

        # DDC Schedule Reference tab
        tab_ref = QWidget()
        ref_lay = QVBoxLayout(tab_ref)
        ref_lay.setContentsMargins(4, 8, 4, 4)
        ref_lbl = QLabel("DDC Schedule — Full Reference")
        ref_lbl.setStyleSheet("font-size:15px;font-weight:800;margin-bottom:6px;")
        ref_lay.addWidget(ref_lbl)

        ref_text = QTextEdit()
        ref_text.setReadOnly(True)
        ref_text.setFont(QFont("Courier New", 10))
        lines = []
        for code, data in DDC_SCHEDULE.items():
            lines.append(f"\n{'='*50}")
            lines.append(f"{code}  ──  {data['name']}")
            for sub_code, sub_name in data["subs"].items():
                lines.append(f"    {sub_code}  ──  {sub_name}")
        lines.append(f"\n{'='*50}")
        lines.append("LC CLASSIFICATION")
        lines.append(f"{'='*50}")
        for code, name in LC_SCHEDULE.items():
            lines.append(f"  {code}  ──  {name}")
        ref_text.setText("\n".join(lines))
        ref_lay.addWidget(ref_text)
        tabs.addTab(tab_ref, "📖  DDC/LC Schedule")

        layout.addWidget(tabs)

    def refresh(self):
        try:
            self._books = self.db.get_books()
        except Exception:
            self._books = []
        self._filter()

    def _filter(self):
        q = self.search_bar.text().lower()
        fc = self.filter_cb.currentText()
        filtered = [b for b in self._books if q in (
            (b.title or "") + (b.author or "") + (b.accNo or "") + (getattr(b, "category", "") or "")
        ).lower()]
        if fc == "Unclassified":
            filtered = [b for b in filtered if not getattr(b, "classificationNo", "")]
        elif fc == "DDC Classified":
            filtered = [b for b in filtered if getattr(b, "classificationSystem", "") == "DDC"]
        elif fc == "LC Classified":
            filtered = [b for b in filtered if getattr(b, "classificationSystem", "") == "LC"]
        self._displayed = filtered
        self._populate(filtered)

    def _populate(self, books):
        classified = sum(1 for b in books if getattr(b, "classificationNo", ""))
        self.stats_lbl.setText(
            f"Showing: {len(books)}  |  ✅ Classified: {classified}  |  ⏳ Unclassified: {len(books) - classified}"
        )
        self.table.setRowCount(len(books))
        for row, b in enumerate(books):
            has_class = bool(getattr(b, "classificationNo", ""))
            for col, val in enumerate([
                getattr(b, "accNo", "") or "",
                b.title or "",
                b.author or "",
                getattr(b, "category", "") or "",
                getattr(b, "classificationNo", "") or "—",
                getattr(b, "subjectHeading", "") or "—",
                getattr(b, "classificationSystem", "") or "—",
                "✅ Classified" if has_class else "⏳ Pending"
            ]):
                item = QTableWidgetItem(val)
                if col == 7:
                    item.setForeground(QColor("#10B981" if has_class else "#F59E0B"))
                self.table.setItem(row, col, item)

    def _classify(self):
        row = self.table.currentRow()
        if row < 0 or row >= len(self._displayed):
            QMessageBox.information(self, "Select", "Please select a book to classify.")
            return
        book = self._displayed[row]
        dlg = ClassifyDialog(book, self)
        if dlg.exec() == QDialog.DialogCode.Accepted:
            data = dlg.get_data()
            book.classificationNo     = data["classificationNo"]
            book.subjectHeading       = data["subjectHeading"]
            book.classificationNotes  = data["classificationNotes"]
            book.classificationSystem = data["classificationSystem"]
            try:
                book.lastUpdated = int(time.time() * 1000)
                self.db.save_book(book)
                QMessageBox.information(
                    self, "Classified",
                    f"✅ '{book.title}' classified as:\n{book.classificationNo}  ({book.classificationSystem})"
                )
                self.refresh()
            except Exception as e:
                QMessageBox.warning(self, "Error", str(e))

    def _auto_classify_all(self):
        unclassified = [b for b in self._books if not getattr(b, "classificationNo", "")]
        if not unclassified:
            QMessageBox.information(self, "All Done", "All books are already classified.")
            return
        reply = QMessageBox.question(
            self, "Auto-Classify",
            f"Auto-classify {len(unclassified)} unclassified books using AI keyword analysis?\n\n"
            "You can review each classification afterwards.",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
        )
        if reply != QMessageBox.StandardButton.Yes:
            return
        count = 0
        for book in unclassified:
            code = auto_classify_ddc(
                book.title or "",
                book.author or "",
                getattr(book, "category", "") or "",
                getattr(book, "publisher", "") or ""
            )
            book.classificationNo     = code
            book.classificationSystem = "DDC"
            book.lastUpdated          = int(time.time() * 1000)
            try:
                self.db.save_book(book)
                count += 1
            except Exception:
                pass
        QMessageBox.information(
            self, "Auto-Classify Complete",
            f"✅ Successfully auto-classified {count} books.\n\n"
            "Please review classifications and adjust as needed."
        )
        self.refresh()
