import time
import json
import datetime
from PyQt6 import QtWidgets, QtCore, QtGui

import config

class EnterpriseFeaturesScreen(QtWidgets.QWidget):
    def __init__(self, db_helper, firebase_service=None, auth=None):
        super().__init__()
        self.db = db_helper
        # Shared Firestore collection, so records raised here reach the
        # Android and web apps. These features were local-SQLite-only.
        self.fb = firebase_service
        self.auth = auth
        self.ops = None
        if firebase_service is not None:
            from services.operations_service import OperationsService
            self.ops = OperationsService(firebase_service)
        self._build_ui()

    def _current_user_label(self):
        if self.auth and getattr(self.auth, "current_user", None):
            return self.auth.current_user.email
        return ""
        
    def _build_ui(self):
        layout = QtWidgets.QVBoxLayout(self)
        layout.setContentsMargins(28, 24, 28, 24)
        layout.setSpacing(14)
        
        hdr = QtWidgets.QLabel("🚀  Enterprise & Unique Features")
        hdr.setStyleSheet("font-size:22px;font-weight:800;color:#E8EEF8;font-family:'Inter', 'Segoe UI';")
        layout.addWidget(hdr)
        
        self.tabs = QtWidgets.QTabWidget()
        self.tabs.setStyleSheet("""
            QTabWidget::pane{background:#071428;border:1px solid #1E3050;border-radius:8px;}
            QTabBar::tab{background:#0D1B2A;color:#6B8CAE;padding:12px 20px;font-size:13px;font-weight:700;border-top-left-radius:8px;border-top-right-radius:8px;}
            QTabBar::tab:selected{background:#1E3050;color:#E6C96E;border-bottom:3px solid #C8A84B;}
        """)
        
        self._build_reading_room(self.tabs)
        self._build_lost_found(self.tabs)
        self._build_events(self.tabs)
        self._build_gamification(self.tabs)
        self._build_ai_procurement(self.tabs)
        
        layout.addWidget(self.tabs)
        
    def _build_reading_room(self, tabs):
        w = QtWidgets.QWidget(); l = QtWidgets.QVBoxLayout(w); l.setContentsMargins(16,16,16,16)
        l.addWidget(QtWidgets.QLabel("Manage seat assignments for the physical reading room.", styleSheet="color:#A0B4CC;font-size:14px;"))

        grid = QtWidgets.QGridLayout()
        self.seats = {}
        for i in range(1, 21):
            btn = QtWidgets.QPushButton(f"Seat {i}\n(Empty)")
            btn.setFixedSize(100, 80)
            btn.setStyleSheet("background:#10B981;color:white;border-radius:8px;font-weight:bold;")
            btn.clicked.connect(lambda _, s=i: self._toggle_seat(s))
            self.seats[i] = btn
            grid.addWidget(btn, (i-1)//5, (i-1)%5)

        l.addLayout(grid)
        l.addStretch()
        tabs.addTab(w, "🪑 Reading Room")
        self._load_seats()

    def _load_seats(self):
        # Seat occupancy used to be plain button text, reset to empty every
        # time this screen was reopened. OperationsService.seats() reads the
        # same `reading_room` Firestore collection the Android app's own
        # Enterprise screen already uses, so a seat assigned on one device
        # now shows the same way on the other.
        by_seat = {}
        if self.ops:
            for rec in self.ops.seats():
                try:
                    by_seat[int(rec.get("seatNumber") or 0)] = rec
                except (TypeError, ValueError):
                    continue
        for num, btn in self.seats.items():
            occupant = (by_seat.get(num) or {}).get("occupantName") or ""
            if occupant:
                btn.setText(f"Seat {num}\n{occupant[:12]}")
                btn.setStyleSheet("background:#EF4444;color:white;border-radius:8px;font-weight:bold;")
            else:
                btn.setText(f"Seat {num}\n(Empty)")
                btn.setStyleSheet("background:#10B981;color:white;border-radius:8px;font-weight:bold;")

    def _toggle_seat(self, seat_num):
        if not self.ops:
            QtWidgets.QMessageBox.warning(self, "Offline", "Reading room seating requires a connected Firebase session.")
            return
        btn = self.seats[seat_num]
        if "(Empty)" in btn.text():
            name, ok = QtWidgets.QInputDialog.getText(self, "Assign Seat", f"Occupant name for Seat {seat_num}:")
            if ok and name.strip():
                mem_id, _ = QtWidgets.QInputDialog.getText(self, "Assign Seat", "Member ID (optional):")
                if self.ops.assign_seat(seat_num, name.strip(), (mem_id or "").strip()):
                    self._load_seats()
                else:
                    QtWidgets.QMessageBox.warning(self, "Error", "Could not assign the seat. Check your connection and try again.")
        else:
            reply = QtWidgets.QMessageBox.question(self, "Free Seat", f"Make Seat {seat_num} empty?", QtWidgets.QMessageBox.StandardButton.Yes | QtWidgets.QMessageBox.StandardButton.No)
            if reply == QtWidgets.QMessageBox.StandardButton.Yes:
                if self.ops.free_seat(seat_num):
                    self._load_seats()
                else:
                    QtWidgets.QMessageBox.warning(self, "Error", "Could not free the seat. Check your connection and try again.")

    def _build_lost_found(self, tabs):
        w = QtWidgets.QWidget(); l = QtWidgets.QVBoxLayout(w); l.setContentsMargins(16,16,16,16)
        l.addWidget(QtWidgets.QLabel("Track items lost or found within the library premises.", styleSheet="color:#A0B4CC;font-size:14px;"))

        self.lf_table = QtWidgets.QTableWidget(0, 4)
        self.lf_table.setHorizontalHeaderLabels(["Date", "Item Description", "Location", "Status"])
        self.lf_table.horizontalHeader().setSectionResizeMode(QtWidgets.QHeaderView.ResizeMode.Stretch)
        self.lf_table.setEditTriggers(QtWidgets.QTableWidget.EditTrigger.NoEditTriggers)
        self.lf_table.setStyleSheet("QTableWidget { background:#0D1B2A; color:#E8EEF8; border:1px solid #1E3050; } QHeaderView::section { background:#1E3050; color:#E8EEF8; }")
        l.addWidget(self.lf_table)

        btn_row = QtWidgets.QHBoxLayout()
        add_btn = QtWidgets.QPushButton("➕ Report Item")
        add_btn.setStyleSheet("background:#2563EB;color:white;border-radius:8px;padding:8px 16px;font-weight:bold;")
        add_btn.clicked.connect(self._add_lf_item)
        btn_row.addWidget(add_btn)

        claim_btn = QtWidgets.QPushButton("✅ Mark as Claimed/Resolved")
        claim_btn.setStyleSheet("background:#059669;color:white;border-radius:8px;padding:8px 16px;font-weight:bold;")
        claim_btn.clicked.connect(self._resolve_lf_item)
        btn_row.addWidget(claim_btn)
        btn_row.addStretch()
        l.addLayout(btn_row)

        tabs.addTab(w, "🔍 Lost & Found")
        self._load_lost_found()

    def _load_lost_found(self):
        # Used to re-insert the same two hardcoded demo rows ("Blue Water
        # Bottle", "HP Laptop Charger") every time this tab opened, and
        # anything reported through "Report Item" only ever lived in this
        # table widget — gone the moment the screen closed. Now reads the
        # shared `lost_found` Firestore collection the Android app's
        # Enterprise screen already writes to.
        self._lf_records = self.ops.lost_found() if self.ops else []
        self.lf_table.setRowCount(0)
        for rec in self._lf_records:
            ts = rec.get("reportedAt")
            date_str = time.strftime("%Y-%m-%d", time.localtime(ts / 1000)) if ts else ""
            self._add_lf_row(date_str, rec.get("itemName", ""), rec.get("location", ""), rec.get("status", ""))

    def _add_lf_row(self, date, item, loc, status):
        row = self.lf_table.rowCount()
        self.lf_table.insertRow(row)
        self.lf_table.setItem(row, 0, QtWidgets.QTableWidgetItem(date))
        self.lf_table.setItem(row, 1, QtWidgets.QTableWidgetItem(item))
        self.lf_table.setItem(row, 2, QtWidgets.QTableWidgetItem(loc))
        itm = QtWidgets.QTableWidgetItem(status)
        if status == "Lost": itm.setForeground(QtGui.QColor("#EF4444"))
        elif status == "Found": itm.setForeground(QtGui.QColor("#10B981"))
        elif status == "Claimed": itm.setForeground(QtGui.QColor("#C8A84B"))
        self.lf_table.setItem(row, 3, itm)

    def _add_lf_item(self):
        if not self.ops:
            QtWidgets.QMessageBox.warning(self, "Offline", "Lost & Found requires a connected Firebase session.")
            return
        desc, ok = QtWidgets.QInputDialog.getText(self, "Report Item", "Item Description:")
        if ok and desc.strip():
            loc, ok2 = QtWidgets.QInputDialog.getText(self, "Location", "Location:")
            if ok2:
                status, ok3 = QtWidgets.QInputDialog.getItem(self, "Status", "Is it Lost or Found?", ["Lost", "Found"], 0, False)
                if ok3:
                    if self.ops.log_lost_found(desc.strip(), loc.strip(), status=status, reported_by=self._current_user_label()):
                        self._load_lost_found()
                    else:
                        QtWidgets.QMessageBox.warning(self, "Error", "Could not save the report. Check your connection and try again.")

    def _resolve_lf_item(self):
        r = self.lf_table.currentRow()
        if r < 0 or r >= len(self._lf_records):
            return
        sync_id = self._lf_records[r].get("syncId")
        if not sync_id or not self.ops:
            return
        if self.ops.set_lost_found_status(sync_id, "Claimed", claimed_by=self._current_user_label()):
            self._load_lost_found()
        else:
            QtWidgets.QMessageBox.warning(self, "Error", "Could not update the record. Check your connection and try again.")
            
    def _build_events(self, tabs):
        w = QtWidgets.QWidget(); l = QtWidgets.QVBoxLayout(w); l.setContentsMargins(16,16,16,16)
        l.addWidget(QtWidgets.QLabel("Schedule library workshops, author talks, and community events.", styleSheet="color:#A0B4CC;font-size:14px;"))

        self.ev_table = QtWidgets.QTableWidget(0, 4)
        self.ev_table.setHorizontalHeaderLabels(["Date & Time", "Event Title", "Speaker/Host", "Registered Attendees"])
        self.ev_table.horizontalHeader().setSectionResizeMode(QtWidgets.QHeaderView.ResizeMode.Stretch)
        self.ev_table.setEditTriggers(QtWidgets.QTableWidget.EditTrigger.NoEditTriggers)
        self.ev_table.setStyleSheet("QTableWidget { background:#0D1B2A; color:#E8EEF8; border:1px solid #1E3050; } QHeaderView::section { background:#1E3050; color:#E8EEF8; }")
        l.addWidget(self.ev_table)

        btn_row = QtWidgets.QHBoxLayout()
        add_btn = QtWidgets.QPushButton("📅 Schedule New Event")
        add_btn.setStyleSheet("background:#7C3AED;color:white;border-radius:8px;padding:8px 16px;font-weight:bold;")
        add_btn.clicked.connect(self._add_event)
        btn_row.addWidget(add_btn)
        btn_row.addStretch()
        l.addLayout(btn_row)

        tabs.addTab(w, "📅 Event Scheduler")
        self._load_events()

    def _load_events(self):
        # Used to always show two hardcoded demo events ("Introduction to
        # Python", "Literature & Modern World") with made-up attendee
        # counts, and "Schedule New Event" only added a row to this table —
        # never persisted, never visible to Android/web. Now backed by the
        # shared `library_events` Firestore collection. There is no
        # capacity field in the shared schema (see Operations.kt's
        # LibraryEvent), so attendees is shown as a plain registered count,
        # not a fabricated "x/50".
        self._ev_records = self.ops.events() if self.ops else []
        self.ev_table.setRowCount(0)
        for rec in self._ev_records:
            self._add_ev_row(rec.get("eventDate", ""), rec.get("title", ""), rec.get("organiser", ""), str(rec.get("attendees", 0)))

    def _add_ev_row(self, dt, title, host, att):
        row = self.ev_table.rowCount()
        self.ev_table.insertRow(row)
        self.ev_table.setItem(row, 0, QtWidgets.QTableWidgetItem(dt))
        self.ev_table.setItem(row, 1, QtWidgets.QTableWidgetItem(title))
        self.ev_table.setItem(row, 2, QtWidgets.QTableWidgetItem(host))
        self.ev_table.setItem(row, 3, QtWidgets.QTableWidgetItem(att))

    def _add_event(self):
        if not self.ops:
            QtWidgets.QMessageBox.warning(self, "Offline", "Scheduling events requires a connected Firebase session.")
            return
        title, ok = QtWidgets.QInputDialog.getText(self, "New Event", "Event Title:")
        if ok and title.strip():
            host, ok2 = QtWidgets.QInputDialog.getText(self, "Host", "Speaker/Host:")
            if ok2:
                dt = datetime.datetime.now() + datetime.timedelta(days=7)
                if self.ops.add_event(title.strip(), dt.strftime("%Y-%m-%d %H:%M"), organiser=host.strip()):
                    self._load_events()
                else:
                    QtWidgets.QMessageBox.warning(self, "Error", "Could not save the event. Check your connection and try again.")

    def _build_gamification(self, tabs):
        w = QtWidgets.QWidget(); l = QtWidgets.QVBoxLayout(w); l.setContentsMargins(16,16,16,16)
        l.addWidget(QtWidgets.QLabel("Boost reading engagement! Ranks update automatically based on reading history.", styleSheet="color:#A0B4CC;font-size:14px;"))
        
        self.gam_lbl = QtWidgets.QLabel("Enter Member ID to view their Reading Badges and Level:")
        self.gam_lbl.setStyleSheet("color:#E8EEF8;font-weight:bold;")
        l.addWidget(self.gam_lbl)
        
        search_lay = QtWidgets.QHBoxLayout()
        self.g_search = QtWidgets.QLineEdit()
        self.g_search.setPlaceholderText("Member ID...")
        self.g_search.setStyleSheet("background:#0D1B2A;color:#E8EEF8;border:1px solid #1E3050;padding:8px;")
        search_lay.addWidget(self.g_search)
        
        btn = QtWidgets.QPushButton("Analyze Profile")
        btn.setStyleSheet("background:#C8A84B;color:#0D1B2A;border-radius:4px;padding:8px 16px;font-weight:bold;")
        btn.clicked.connect(self._analyze_gamification)
        search_lay.addWidget(btn)
        l.addLayout(search_lay)
        
        self.badge_display = QtWidgets.QLabel("\n\n\nProfile Data Will Appear Here")
        self.badge_display.setAlignment(QtCore.Qt.AlignmentFlag.AlignCenter)
        self.badge_display.setStyleSheet("background:#0D1B2A;border:1px dashed #1E3050;color:#6B8CAE;font-size:16px;")
        l.addWidget(self.badge_display)
        l.addStretch()
        tabs.addTab(w, "🏆 Gamification")
        
    def _analyze_gamification(self):
        mid = self.g_search.text().strip()
        if not mid: return
        members = [m for m in self.db.get_members() if m.memberId == mid]
        if not members:
            self.badge_display.setText("Member Not Found.")
            return
            
        m = members[0]
        read = m.booksIssued
        
        if read < 5:
            rank, badge = "Novice Reader", "🌱"
            next_tgt = 5
        elif read < 20:
            rank, badge = "Avid Bookworm", "🐛"
            next_tgt = 20
        elif read < 50:
            rank, badge = "Library Scholar", "🎓"
            next_tgt = 50
        else:
            rank, badge = "Grandmaster of Pages", "👑"
            next_tgt = "MAX"
            
        html = f"""
        <h2 style='color:#E6C96E;'>{badge} {m.name}</h2>
        <p>Current Rank: <b>{rank}</b></p>
        <p>Books Read: <b>{read}</b></p>
        """
        if next_tgt != "MAX":
            html += f"<p><i>Read {next_tgt - read} more books to rank up!</i></p>"
        self.badge_display.setText(html)

    def _build_ai_procurement(self, tabs):
        w = QtWidgets.QWidget(); l = QtWidgets.QVBoxLayout(w); l.setContentsMargins(16,16,16,16)
        l.addWidget(QtWidgets.QLabel("Analyzes circulation history to automatically suggest books to purchase based on high demand.", styleSheet="color:#A0B4CC;font-size:14px;"))
        
        btn = QtWidgets.QPushButton("🤖 Run AI Demand Analysis")
        btn.setStyleSheet("background:qlineargradient(x1:0,y1:0,x2:1,y2:0,stop:0 #EF4444,stop:1 #F59E0B);color:white;border-radius:8px;padding:12px;font-weight:bold;font-size:14px;")
        btn.clicked.connect(self._run_ai_procurement)
        l.addWidget(btn)
        
        self.ai_res = QtWidgets.QTextEdit()
        self.ai_res.setReadOnly(True)
        self.ai_res.setStyleSheet("background:#071428;color:#A0B4CC;border:1px solid #1E3050;font-size:13px;")
        l.addWidget(self.ai_res)
        
        tabs.addTab(w, "🛒 AI Procurement")
        
    def _run_ai_procurement(self):
        self.ai_res.setText("Analyzing circulation patterns...")
        import random
        issues = self.db.get_issues()
        if not issues:
            self.ai_res.append("\nNot enough data.")
            return
            
        titles = [i.bookTitle for i in issues]
        from collections import Counter
        top = Counter(titles).most_common(3)
        
        res = "\n✅ Analysis Complete:\n\n"
        for t, count in top:
            res += f"🔹 High Demand: '{t}' ({count} times)\n"
        self.ai_res.append(res)
