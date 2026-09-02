from PyQt6.QtWidgets import QWidget, QVBoxLayout, QHBoxLayout, QLabel, QTableWidget, QTableWidgetItem, QPushButton, QHeaderView, QMessageBox
from PyQt6.QtCore import Qt, QTimer, QThread, pyqtSignal

class DirectorateDashboardScreen(QWidget):
    def __init__(self, fb_service):
        super().__init__()
        self.fb = fb_service
        self._build_ui()

        # Periodic refresh every 5 minutes (Directorate data is heavy)
        self._timer = QTimer(self)
        self._timer.timeout.connect(self.load_data)
        self._timer.start(300000)

        self.load_data()

    def _build_ui(self):
        lay = QVBoxLayout(self)
        lay.setContentsMargins(40, 40, 40, 40)
        lay.setSpacing(24)

        # Header
        hdr = QHBoxLayout()
        titles = QVBoxLayout()
        header = QLabel("🌍  Directorate Dashboard (Global)")
        header.setStyleSheet("color: #1E3A8A; font-size: 32px; font-weight: 900;")
        sub = QLabel("Aggregated network-wide statistics across all colleges.")
        sub.setStyleSheet("color: #64748B; font-size: 16px;")
        titles.addWidget(header); titles.addWidget(sub)
        hdr.addLayout(titles)
        hdr.addStretch()
        
        # Refresh Button
        self.refresh_btn = QPushButton("🔄 Refresh Aggregated Data")
        self.refresh_btn.setObjectName("ActionBtn")
        self.refresh_btn.clicked.connect(self.load_data)
        hdr.addWidget(self.refresh_btn)
        lay.addLayout(hdr)

        # Stats layout
        stats_lay = QHBoxLayout()
        self.lbl_total_colleges = self._create_stat_card("Total Colleges", "0")
        self.lbl_total_books = self._create_stat_card("Total Books", "0")
        self.lbl_total_members = self._create_stat_card("Total Members", "0")

        stats_lay.addWidget(self.lbl_total_colleges)
        stats_lay.addWidget(self.lbl_total_books)
        stats_lay.addWidget(self.lbl_total_members)
        lay.addLayout(stats_lay)

        # Table
        self.table = QTableWidget(0, 4)
        self.table.setHorizontalHeaderLabels(["College Name", "Invite Code", "Books Count", "Members Count"])
        self.table.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        lay.addWidget(self.table)

    def _create_stat_card(self, title, value):
        card = QLabel(f"<b>{title}</b><br><br><span style='font-size: 28px; color: #2563EB;'>{value}</span>")
        card.setObjectName("Card")
        card.setStyleSheet("padding: 24px; text-align: center;")
        card.setAlignment(Qt.AlignmentFlag.AlignCenter)
        return card

    def load_data(self):
        if not self.fb.db:
            QMessageBox.warning(self, "Offline Mode", "Global Directorate insights require an active cloud connection.")
            return
            
        self.refresh_btn.setText("⏳ Aggregating Data...")
        self.refresh_btn.setEnabled(False)

        class DirectorateWorker(QThread):
            finished = pyqtSignal(object)
            def __init__(self, db):
                super().__init__()
                self.db = db
            def run(self):
                # Read the aggregate counts straight off the registry document.
                # The previous implementation streamed every book and member
                # document of every college on each refresh just to length them,
                # which is O(all documents in the network) per refresh and burns
                # through the Spark plan's daily read quota. Each college now
                # publishes its own counts, so this is one read per college.
                try:
                    index_docs = self.db.collection("directorate_index").stream()
                    data = []
                    total_b = 0
                    total_m = 0
                    for doc in index_docs:
                        idx = doc.to_dict() or {}
                        inst_id = idx.get("institutionId") or doc.id
                        books_count = int(idx.get("booksCount") or 0)
                        members_count = int(idx.get("membersCount") or 0)
                        total_b += books_count
                        total_m += members_count
                        data.append({
                            "name": idx.get("name") or idx.get("collegeName") or inst_id,
                            "invite": inst_id,
                            "books": books_count,
                            "members": members_count,
                        })
                    self.finished.emit((data, len(data), total_b, total_m))
                except Exception as e:
                    print(f"Directorate Fetch Error: {e}")
                    self.finished.emit(None)

        self._worker = DirectorateWorker(self.fb.db)
        def on_result(res):
            self.refresh_btn.setText("🔄 Refresh Aggregated Data")
            self.refresh_btn.setEnabled(True)
            if not res: return
            data, num_colleges, tot_b, tot_m = res
            self.lbl_total_colleges.setText(f"<b>Total Colleges</b><br><br><span style='font-size: 28px; color: #2563EB;'>{num_colleges}</span>")
            self.lbl_total_books.setText(f"<b>Total Books</b><br><br><span style='font-size: 28px; color: #2563EB;'>{tot_b}</span>")
            self.lbl_total_members.setText(f"<b>Total Members</b><br><br><span style='font-size: 28px; color: #2563EB;'>{tot_m}</span>")
            
            self.table.setRowCount(0)
            for i, row in enumerate(data):
                self.table.insertRow(i)
                self.table.setItem(i, 0, QTableWidgetItem(str(row["name"])))
                self.table.setItem(i, 1, QTableWidgetItem(str(row["invite"])))
                self.table.setItem(i, 2, QTableWidgetItem(str(row["books"])))
                self.table.setItem(i, 3, QTableWidgetItem(str(row["members"])))

        self._worker.finished.connect(on_result)
        self._worker.start()
