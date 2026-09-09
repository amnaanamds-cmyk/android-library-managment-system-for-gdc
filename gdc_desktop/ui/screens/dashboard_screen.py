"""
ui/screens/dashboard_screen.py - the librarian's overview screen.

Colour discipline: everything here draws from ui.theme. The stat tiles are
neutral by default and only take a colour when the number itself means
something is wrong - an overdue count above zero, reservations waiting. When
all seven tiles were coloured, the overdue one no longer stood out, which is
the one a librarian needs to spot from across the desk.
"""
import time
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QFrame,
    QGridLayout, QPushButton
)
from PyQt6.QtCore import Qt, QThread, pyqtSignal, QTimer

import config
from ui.theme import current_palette

import matplotlib
try:
    matplotlib.use('QtAgg')
    from matplotlib.backends.backend_qtagg import FigureCanvasQTAgg as FigureCanvas
    from matplotlib.backends.backend_qtagg import NavigationToolbar2QT as NavigationToolbar
    HAS_MATPLOTLIB = True
except Exception:
    HAS_MATPLOTLIB = False
from matplotlib.figure import Figure


class StatCard(QFrame):
    """One metric. Neutral unless its value indicates a state.

    tone is one of "neutral", "positive", "warning", "danger". Pass a
    tone_rule callable to recolour the tile from the value on each update -
    that is how Overdue turns red only when there actually are overdue books.
    """

    def __init__(self, icon, label, value="—", tone="neutral", tone_rule=None):
        super().__init__()
        self.p = current_palette()
        self._tone_rule = tone_rule
        self._tone = tone
        self.setMinimumSize(180, 108)

        layout = QVBoxLayout(self)
        layout.setContentsMargins(18, 16, 18, 16)
        layout.setSpacing(2)

        top = QHBoxLayout()
        top.setSpacing(8)
        self.icon_lbl = QLabel(icon)
        self.icon_lbl.setObjectName("cardIcon")
        top.addWidget(self.icon_lbl)
        self.txt_lbl = QLabel(label.upper())
        self.txt_lbl.setObjectName("cardLbl")
        top.addWidget(self.txt_lbl)
        top.addStretch()
        layout.addLayout(top)

        self.val_lbl = QLabel(str(value))
        self.val_lbl.setObjectName("cardVal")
        layout.addWidget(self.val_lbl)
        layout.addStretch()

        self._apply_tone()

    def _apply_tone(self):
        p = self.p
        accent = p.get(self._tone, p["text_muted"]) if self._tone != "neutral" else p["border_strong"]
        value_colour = p[self._tone] if self._tone != "neutral" else p["text"]
        self.setStyleSheet(f"""
        QFrame {{
            background: {p['surface']};
            border: 1px solid {p['border']};
            border-left: 3px solid {accent};
            border-radius: 12px;
        }}
        QLabel {{ border: none; background: transparent; }}
        QLabel#cardIcon {{ font-size: 15px; }}
        QLabel#cardLbl  {{ color: {p['text_muted']}; font-size: 11px; font-weight: 700;
                           letter-spacing: 0.6px; }}
        QLabel#cardVal  {{ color: {value_colour}; font-size: 30px; font-weight: 800; }}
        """)

    def update_value(self, val):
        self.val_lbl.setText(str(val))
        if self._tone_rule is not None:
            tone = self._tone_rule(val)
            if tone != self._tone:
                self._tone = tone
                self._apply_tone()


def _tone_when_nonzero(tone):
    """Neutral at zero, `tone` above it. Used by Overdue and Reservations."""
    def rule(val):
        try:
            return tone if float(str(val).replace(",", "")) > 0 else "neutral"
        except (TypeError, ValueError):
            return "neutral"
    return rule


class StatsWorker(QThread):
    finished = pyqtSignal(dict, list)

    def __init__(self, fb, db):
        super().__init__()
        self.fb = fb
        self.db = db

    def run(self):
        stats = {}
        logs = []
        try:
            # Prioritize Local DB for immediate UI feedback in Offline-First architecture
            books = self.db.get_books()
            members = self.db.get_members()
            issues = self.db.get_issues()
            reservations = self.db.get_reservations()

            total_books = len(books)
            available = sum(1 for b in books if b.status == "Available")
            issued_count = sum(1 for b in books if b.status == "Issued")

            today = time.strftime("%Y-%m-%d")
            overdue = sum(1 for r in issues if r.status == "Issued" and r.dueDate and r.dueDate < today)

            total_fine = sum(r.fine for r in issues if r.status == "Returned" and r.fine > 0)

            from datetime import datetime, timedelta
            now = datetime.now()
            this_month_start = now.replace(day=1).strftime("%Y-%m-%d")
            last_month_start = (now.replace(day=1) - timedelta(days=1)).replace(day=1).strftime("%Y-%m-%d")
            this_month_count = sum(1 for r in issues if r.issueDate and r.issueDate >= this_month_start)
            last_month_count = sum(1 for r in issues if r.issueDate and last_month_start <= r.issueDate < this_month_start)

            stats = {
                "totalBooks": total_books,
                "availableBooks": available,
                "issuedBooks": issued_count,
                "activeIssues": issued_count, # Mapping for Director Dashboard
                "totalMembers": len(members),
                "overdueCount": overdue,
                "pendingReservations": len([r for r in reservations if r.status == "Pending"]),
                "totalFineCollected": total_fine,
                "thisMonthIssues": this_month_count,
                "lastMonthIssues": last_month_count,
            }

            # Digitalization Progress (HEC Mandate)
            with_isbn = sum(1 for b in books if b.isbn and len(b.isbn) > 5)
            with_ebook = sum(1 for b in books if b.isDigital)
            stats["digitalizationProgress"] = {
                "withIsbn": round(with_isbn / total_books * 100, 1) if total_books else 0,
                "withEBook": round(with_ebook / total_books * 100, 1) if total_books else 0,
            }

            cats = {}
            for b in books:
                c = b.category or "Uncategorized"
                cats[c] = cats.get(c, 0) + 1
            stats["categories"] = cats

            logs = self.db.get_audit_logs_local(10)

        except Exception as e:
            print(f"Error calculating dashboard stats: {e}")

        self.finished.emit(stats, logs)


class DashboardScreen(QWidget):
    def __init__(self, firebase_service, db_helper):
        super().__init__()
        self.fb = firebase_service
        self.db = db_helper
        self.p = current_palette()
        self.worker = None
        self._build_ui()
        self._timer = QTimer(self)
        self._timer.timeout.connect(self.refresh)
        self._timer.start(60_000)
        QTimer.singleShot(500, self.refresh)

    def _build_ui(self):
        layout = QVBoxLayout(self)
        layout.setContentsMargins(32, 28, 32, 28)
        layout.setSpacing(18)

        layout.addWidget(self._build_banner())
        layout.addLayout(self._build_header())
        layout.addWidget(self._build_ai_card())
        layout.addLayout(self._build_stats_grid())
        layout.addWidget(self._section_label("Quick Actions"))
        layout.addLayout(self._build_quick_actions())
        layout.addLayout(self._build_charts_and_activity())
        layout.addStretch()

    # ---------------------------------------------------------------- banner
    def _build_banner(self):
        p = self.p
        self.banner = QFrame()
        self.banner.setFixedHeight(132)
        # The one gradient in the app. Because nothing else uses one, it reads
        # as the masthead rather than as decoration.
        self.banner.setStyleSheet(f"""
            QFrame {{
                background: qlineargradient(x1:0, y1:0, x2:1, y2:0,
                    stop:0 #16264A, stop:1 #23407A);
                border-radius: 14px;
            }}
            QLabel {{ background: transparent; border: none; }}
        """)
        banner_lay = QHBoxLayout(self.banner)
        banner_lay.setContentsMargins(28, 0, 28, 0)

        text_v = QVBoxLayout()
        text_v.setSpacing(4)
        text_v.setAlignment(Qt.AlignmentFlag.AlignVCenter)

        c_name = getattr(config, 'COLLEGE_NAME', 'Government Degree College')
        name_lbl = QLabel(c_name)
        name_lbl.setStyleSheet("color: #FFFFFF; font-size: 23px; font-weight: 800;")

        sub = QLabel("NEXLIB  ·  Library Management System  ·  "
                     "Higher Education Department, Khyber Pakhtunkhwa")
        sub.setStyleSheet("color: rgba(255,255,255,0.72); font-size: 12px; font-weight: 600;")

        text_v.addWidget(name_lbl)
        text_v.addWidget(sub)
        banner_lay.addLayout(text_v)
        banner_lay.addStretch()

        self.banner_stats = QLabel(getattr(config, 'COLLEGE_ID', '') or "")
        self.banner_stats.setStyleSheet(
            "color: rgba(255,255,255,0.9); background: rgba(255,255,255,0.12);"
            "padding: 8px 16px; border-radius: 8px; font-weight: 700; font-size: 12px;"
        )
        banner_lay.addWidget(self.banner_stats)
        return self.banner

    # ---------------------------------------------------------------- header
    def _build_header(self):
        p = self.p
        hdr = QHBoxLayout()
        hdr.setSpacing(8)

        title = QLabel("Dashboard")
        title.setStyleSheet(f"font-size: 22px; font-weight: 800; color: {p['text']};")
        hdr.addWidget(title)

        self.last_update = QLabel("·  updated —")
        self.last_update.setStyleSheet(f"color: {p['text_muted']}; font-size: 12px;")
        hdr.addWidget(self.last_update)
        hdr.addStretch()

        # These two used to pop a message box claiming a turnstile was being
        # monitored. Nothing was. Both features exist for real on their own
        # screens, so the buttons now go there.
        self.gate_btn = self._ghost_button("Gate Log")
        self.gate_btn.clicked.connect(lambda: self._navigate("visitor_log"))
        hdr.addWidget(self.gate_btn)

        self.kiosk_btn = self._ghost_button("Self-Checkout")
        self.kiosk_btn.clicked.connect(lambda: self._navigate("opac"))
        hdr.addWidget(self.kiosk_btn)

        self.refresh_btn = self._ghost_button("↻  Refresh")
        self.refresh_btn.clicked.connect(self.refresh)
        hdr.addWidget(self.refresh_btn)
        return hdr

    def _ghost_button(self, text):
        p = self.p
        btn = QPushButton(text)
        btn.setCursor(Qt.CursorShape.PointingHandCursor)
        btn.setStyleSheet(f"""
            QPushButton {{
                background: {p['surface']}; color: {p['text_body']};
                border: 1px solid {p['border']}; border-radius: 8px;
                padding: 7px 14px; font-size: 12px; font-weight: 600;
            }}
            QPushButton:hover {{ border-color: {p['accent']}; color: {p['accent']}; }}
            QPushButton:disabled {{ color: {p['text_muted']}; }}
        """)
        return btn

    def _section_label(self, text):
        lbl = QLabel(text.upper())
        lbl.setStyleSheet(
            f"color: {self.p['text_muted']}; font-size: 11px; font-weight: 700;"
            "letter-spacing: 0.8px; margin-top: 6px;"
        )
        return lbl

    # -------------------------------------------------------------- AI card
    def _build_ai_card(self):
        p = self.p
        self.ai_card = QFrame()
        self.ai_card.setStyleSheet(f"""
            QFrame {{ background: {p['accent_soft']}; border: 1px solid {p['border']};
                      border-radius: 12px; }}
            QLabel {{ border: none; background: transparent; }}
        """)
        ai_lay = QHBoxLayout(self.ai_card)
        ai_lay.setContentsMargins(16, 12, 16, 12)

        ai_v = QVBoxLayout()
        ai_v.setSpacing(2)
        ai_title = QLabel("AI Librarian")
        ai_title.setStyleSheet(f"font-weight: 700; color: {p['accent']}; font-size: 13px;")
        self.ai_msg = QLabel("Ask for a read on today's circulation, overdue risk or stock gaps.")
        self.ai_msg.setWordWrap(True)
        self.ai_msg.setStyleSheet(f"font-size: 12px; color: {p['text_body']};")
        ai_v.addWidget(ai_title)
        ai_v.addWidget(self.ai_msg)
        ai_lay.addLayout(ai_v, stretch=1)

        ai_btn = QPushButton("Ask for insights")
        ai_btn.setCursor(Qt.CursorShape.PointingHandCursor)
        ai_btn.setStyleSheet(
            f"background: {p['accent']}; color: {p['on_accent']}; font-weight: 700;"
            "border: none; border-radius: 8px; padding: 9px 16px; font-size: 12px;"
        )
        ai_btn.clicked.connect(self._run_dashboard_ai)
        ai_lay.addWidget(ai_btn)
        return self.ai_card

    # ------------------------------------------------------------ stat grid
    def _build_stats_grid(self):
        grid = QGridLayout()
        grid.setSpacing(14)

        self.cards = {
            "totalBooks":          StatCard("\U0001f4da", "Total Books"),
            "availableBooks":      StatCard("✓", "Available"),
            "issuedBooks":         StatCard("\U0001f4d6", "Issued"),
            "totalMembers":        StatCard("\U0001f465", "Members"),
            "overdueCount":        StatCard("⏰", "Overdue",
                                            tone_rule=_tone_when_nonzero("danger")),
            "pendingReservations": StatCard("\U0001f514", "Reservations",
                                            tone_rule=_tone_when_nonzero("warning")),
            "totalFineCollected":  StatCard("\U0001f4b0", "Fines Collected"),
        }
        positions = [(0, 0), (0, 1), (0, 2), (0, 3), (1, 0), (1, 1), (1, 2)]
        for (r, c), card in zip(positions, self.cards.values()):
            grid.addWidget(card, r, c)
        for col in range(4):
            grid.setColumnStretch(col, 1)
        return grid

    # -------------------------------------------------------- quick actions
    def _build_quick_actions(self):
        p = self.p
        qa_row = QHBoxLayout()
        qa_row.setSpacing(8)
        self._qa_btns = []

        # Exactly one primary. Issuing and returning is what the front desk
        # does all day; everything else is a secondary route to a screen.
        qa_items = [
            ("Issue / Return", "issue_return", True),
            ("Add Book", "books", False),
            ("Add Member", "members", False),
            ("Search Catalogue", "opac", False),
            ("Reports", "reports", False),
        ]
        for text, nav_key, primary in qa_items:
            if primary:
                btn = QPushButton(text)
                btn.setCursor(Qt.CursorShape.PointingHandCursor)
                btn.setStyleSheet(
                    f"background: {p['accent']}; color: {p['on_accent']}; border: none;"
                    "border-radius: 8px; padding: 9px 18px; font-size: 12px; font-weight: 700;"
                )
            else:
                btn = self._ghost_button(text)
            btn.clicked.connect(lambda _, k=nav_key: self._navigate(k))
            qa_row.addWidget(btn)
            self._qa_btns.append((btn, nav_key))
        qa_row.addStretch()
        return qa_row

    # ---------------------------------------------------- charts + activity
    def _build_charts_and_activity(self):
        p = self.p
        chart_lay = QHBoxLayout()
        chart_lay.setSpacing(14)

        if HAS_MATPLOTLIB:
            chart_container = QFrame()
            chart_container.setObjectName("Card")
            chart_container.setMinimumHeight(300)
            chart_vlay = QVBoxLayout(chart_container)
            chart_vlay.setContentsMargins(12, 12, 12, 12)

            self.figure = Figure(figsize=(5, 4), dpi=100, facecolor=p["chart_bg"])
            self.canvas = FigureCanvas(self.figure)

            self.ax_cat = self.figure.add_subplot(121)
            self.ax_month = self.figure.add_subplot(122)
            for ax, title in ((self.ax_cat, "Categories"),
                              (self.ax_month, "Monthly Issues")):
                self._style_axes(ax, title)

            self.figure.tight_layout(pad=2)

            self.toolbar = NavigationToolbar(self.canvas, self)
            self.toolbar.setStyleSheet(
                f"background: {p['surface']}; color: {p['text_body']}; border: none;"
            )

            chart_vlay.addWidget(self.toolbar)
            chart_vlay.addWidget(self.canvas)
            chart_lay.addWidget(chart_container, stretch=2)
        else:
            no_chart = QLabel("Charts need matplotlib. Run: pip install -r requirements.txt")
            no_chart.setStyleSheet(f"color: {p['text_muted']}; font-style: italic;")
            no_chart.setAlignment(Qt.AlignmentFlag.AlignCenter)
            chart_lay.addWidget(no_chart)

        activity_col = QVBoxLayout()
        activity_col.setSpacing(8)
        activity_col.addWidget(self._section_label("Recent Activity"))

        self.activity_frame = QFrame()
        self.activity_frame.setObjectName("Card")
        self.activity_layout = QVBoxLayout(self.activity_frame)
        self.activity_layout.setContentsMargins(16, 12, 16, 12)
        self.activity_layout.setSpacing(2)
        self.no_activity = QLabel("Loading recent activity…")
        self.no_activity.setStyleSheet(f"color: {p['text_muted']}; font-size: 12px;")
        self.activity_layout.addWidget(self.no_activity)
        activity_col.addWidget(self.activity_frame)
        # Rows are added top-down; without this the frame floats in the
        # middle of the column once the chart makes the row tall.
        activity_col.addStretch()

        chart_lay.addLayout(activity_col, stretch=3)
        return chart_lay

    def _style_axes(self, ax, title):
        p = self.p
        ax.set_facecolor(p["chart_bg"])
        ax.tick_params(colors=p["chart_text"], labelsize=7)
        for side, spine in ax.spines.items():
            # Only the axis lines the eye needs. A full box round a bar chart
            # is chartjunk.
            spine.set_visible(side in ("left", "bottom"))
            spine.set_color(p["chart_grid"])
        ax.set_title(title, color=p["chart_text"], fontsize=9, fontweight="bold")

    # ------------------------------------------------------------ behaviour
    def _navigate(self, key):
        """Find the MainWindow and navigate."""
        win = self.window()
        if hasattr(win, 'navigate_to'):
            win.navigate_to(key)
        else:
            # Fallback for nested widgets
            curr = self.parentWidget()
            while curr:
                if hasattr(curr, 'navigate_to'):
                    curr.navigate_to(key)
                    return
                curr = curr.parentWidget()

    def _run_dashboard_ai(self):
        self.ai_msg.setText("Analysing…")
        win = self.window()
        if hasattr(win, 'agent'):
            stats = self.db.compute_snapshot()
            prompt = (f"Based on library stats: {stats}, give a one-sentence friendly "
                      "greeting and a quick tip for the librarian today.")

            from ui.agent_overlay import AgentWorker
            self.ai_worker = AgentWorker(win.agent, prompt)
            self.ai_worker.response_received.connect(self.ai_msg.setText)
            self.ai_worker.start()

    def refresh(self):
        self.refresh_btn.setEnabled(False)
        self.worker = StatsWorker(self.fb, self.db)
        self.worker.finished.connect(self._on_stats)
        self.worker.start()

    def _on_stats(self, stats: dict, logs: list):
        p = self.p
        self.refresh_btn.setEnabled(True)
        for key, card in self.cards.items():
            if key in stats:
                val = stats[key]
                if key == "totalFineCollected":
                    val = f"Rs {val:,.0f}"
                card.update_value(val)
        self.last_update.setText(f"·  updated {time.strftime('%H:%M')}")

        if HAS_MATPLOTLIB:
            cats = stats.get("categories", {})
            if cats:
                self.ax_cat.clear()
                self._style_axes(self.ax_cat, "Categories (Top 5)")
                top = sorted(cats.items(), key=lambda kv: kv[1], reverse=True)[:5]
                labels = [k for k, _ in top][::-1]
                values = [v for _, v in top][::-1]
                self.ax_cat.barh(labels, values, color=p["chart_series"], height=0.6)

            this_month = stats.get("thisMonthIssues", 0)
            last_month = stats.get("lastMonthIssues", 0)
            self.ax_month.clear()
            self._style_axes(self.ax_month, "Issues: Last vs This Month")
            bar_values = [last_month, this_month]
            self.ax_month.bar(
                ["Last Month", "This Month"], bar_values,
                color=[p["chart_series_alt"], p["chart_series"]], width=0.5,
            )
            headroom = max(bar_values + [1]) * 0.03
            for i, v in enumerate(bar_values):
                self.ax_month.text(i, v + headroom, str(v), ha='center',
                                   color=p["chart_text"], fontsize=9, fontweight='bold')

            self.canvas.draw()

        self._render_activity(logs)

    def _render_activity(self, logs):
        p = self.p
        while self.activity_layout.count():
            item = self.activity_layout.takeAt(0)
            if item.widget():
                item.widget().deleteLater()
        if not logs:
            lbl = QLabel("No recent activity.")
            lbl.setStyleSheet(f"color: {p['text_muted']}; font-size: 12px;")
            self.activity_layout.addWidget(lbl)
            return
        for entry in logs:
            row = QLabel(
                f"<span style='color:{p['text_muted']}'>{entry.get('timestampStr','')}</span>"
                f"&nbsp;&nbsp;<b>{entry.get('action','')}</b>"
                f"&nbsp;&nbsp;{entry.get('detail','')}"
                f"&nbsp;&nbsp;<span style='color:{p['text_muted']}'>"
                f"{entry.get('userEmail','')}</span>"
            )
            row.setTextFormat(Qt.TextFormat.RichText)
            row.setStyleSheet(f"font-size: 12px; padding: 4px 0; color: {p['text_body']};")
            self.activity_layout.addWidget(row)
