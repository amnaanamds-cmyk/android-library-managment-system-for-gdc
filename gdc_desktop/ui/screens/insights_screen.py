"""
ui/screens/insights_screen.py — Usage analytics: which books are earning
their shelf space, how members differ beyond a raw borrow count, and which
active loans need a reminder now.

Everything shown here comes from services/analytics_service.py — plain,
explainable aggregation over the local SQLite mirror, computed off the UI
thread the same way dashboard_screen.py's StatsWorker does it. No machine
learning: see analytics_service.py's module docstring for why that is a
deliberate choice at this stage, not a missing feature.
"""
from PyQt6.QtWidgets import (
    QWidget, QVBoxLayout, QHBoxLayout, QLabel, QFrame, QTabWidget,
    QTableWidget, QTableWidgetItem, QHeaderView, QGridLayout,
)
from PyQt6.QtCore import Qt, QThread, pyqtSignal
from PyQt6.QtGui import QFont, QColor

from ui.theme import tokens
from ui.widgets.stat_card import StatCard
from services.analytics_service import rank_book_popularity, segment_members, score_overdue_risk


def _current_theme():
    from ui.main_window import MainWindow
    inst = MainWindow.instance()
    return tokens(dark=inst.is_dark if inst else True)


class InsightsWorker(QThread):
    finished = pyqtSignal(dict)
    error = pyqtSignal(str)

    def __init__(self, db_helper):
        super().__init__()
        self.db = db_helper

    def run(self):
        try:
            books = self.db.get_books()
            members = self.db.get_members()
            issues = self.db.get_issues()

            popularity = rank_book_popularity(books, issues)
            segments = segment_members(members, issues)
            risks = score_overdue_risk(issues)

            self.finished.emit({
                "popularity": popularity,
                "segments": segments,
                "risks": risks,
            })
        except Exception as e:
            self.error.emit(str(e))


class InsightsScreen(QWidget):
    def __init__(self, db_helper, parent=None):
        super().__init__(parent)
        self.db = db_helper
        self._worker = None
        self._t = _current_theme()
        self._build_ui()
        self.refresh()

    # ── UI scaffold ──────────────────────────────────────────────────────

    def _build_ui(self):
        t = self._t
        self.setStyleSheet(f"background: {t.surface_sunken};")
        outer = QVBoxLayout(self)
        outer.setContentsMargins(24, 24, 24, 24)
        outer.setSpacing(18)

        header = QLabel("Usage Insights")
        header.setFont(QFont("Segoe UI", 22, QFont.Weight.Bold))
        header.setStyleSheet(f"color: {t.text_primary}; background: transparent;")
        outer.addWidget(header)

        sub = QLabel(
            "Plain aggregation over this college's own borrowing history — not a prediction, "
            "a description of what already happened."
        )
        sub.setStyleSheet(f"color: {t.text_secondary}; font-size: 12px; background: transparent;")
        outer.addWidget(sub)

        # Summary tiles
        tiles_row = QHBoxLayout()
        tiles_row.setSpacing(14)
        self.card_dead_stock = StatCard("📦", "Dead stock", t, accent_fg=t.warning_fg, accent_bg=t.warning_bg)
        self.card_gems = StatCard("💎", "Hidden gems", t, accent_fg=t.info_fg, accent_bg=t.info_bg)
        self.card_heavy = StatCard("📚", "Heavy readers", t, accent_fg=t.success_fg, accent_bg=t.success_bg)
        self.card_risk = StatCard("⏰", "Active loans at risk", t, accent_fg=t.danger_fg, accent_bg=t.danger_bg)
        for card in (self.card_dead_stock, self.card_gems, self.card_heavy, self.card_risk):
            tiles_row.addWidget(card)
        outer.addLayout(tiles_row)

        # Tabs
        self.tabs = QTabWidget()
        self.tabs.setStyleSheet(f"""
            QTabWidget::pane {{ border: 1px solid {t.surface_border}; background: {t.surface}; border-radius: 8px; }}
            QTabBar::tab {{ background: {t.surface_sunken}; color: {t.text_secondary}; padding: 8px 16px;
                             border: 1px solid {t.surface_border}; border-bottom: none;
                             border-top-left-radius: 6px; border-top-right-radius: 6px; margin-right: 2px; }}
            QTabBar::tab:selected {{ background: {t.surface}; color: {t.text_primary}; font-weight: bold; }}
        """)

        self.tbl_most_borrowed = self._make_table(["Title", "Category", "Copies", "Issues", "Issues/Copy"])
        self.tbl_hidden_gems = self._make_table(["Title", "Category", "Copies", "Issues", "Issues/Copy"])
        self.tbl_dead_stock = self._make_table(["Title", "Category", "Copies", "Last Borrowed"])
        self.tbl_segments = self._make_table(["Member", "Total Issues", "Days Since Last", "Segment", "Reliability"])
        self.tbl_risk = self._make_table(["Member", "Book", "Due Date", "Days", "Prior Late", "Risk", "Reason"])

        self.tabs.addTab(self.tbl_most_borrowed, "Most Borrowed")
        self.tabs.addTab(self.tbl_hidden_gems, "Hidden Gems")
        self.tabs.addTab(self.tbl_dead_stock, "Dead Stock")
        self.tabs.addTab(self.tbl_segments, "Member Segments")
        self.tabs.addTab(self.tbl_risk, "Overdue Risk")

        outer.addWidget(self.tabs, stretch=1)

    def _make_table(self, headers):
        t = self._t
        tbl = QTableWidget(0, len(headers))
        tbl.setHorizontalHeaderLabels(headers)
        tbl.horizontalHeader().setSectionResizeMode(QHeaderView.ResizeMode.Stretch)
        tbl.verticalHeader().setVisible(False)
        tbl.setEditTriggers(QTableWidget.EditTrigger.NoEditTriggers)
        tbl.setAlternatingRowColors(True)
        tbl.setStyleSheet(f"""
            QTableWidget {{ background: {t.surface}; color: {t.text_primary}; border: none;
                             gridline-color: {t.surface_divider}; alternate-background-color: {t.surface_muted}; }}
            QHeaderView::section {{ background: {t.surface_sunken}; color: {t.text_secondary};
                                     padding: 6px; border: none; font-weight: bold; font-size: 11px; }}
        """)
        return tbl

    # ── Data loading ─────────────────────────────────────────────────────

    def refresh(self):
        self._worker = InsightsWorker(self.db)
        self._worker.finished.connect(self._on_ready)
        self._worker.error.connect(lambda msg: print(f"Insights load failed: {msg}"))
        self._worker.start()

    def _on_ready(self, data):
        pop = data["popularity"]
        segments = data["segments"]
        risks = data["risks"]

        self.card_dead_stock.set_value(len(pop["dead_stock"]))
        self.card_dead_stock.set_sub(f"of {pop['total_works']} distinct titles")
        self.card_gems.set_value(len(pop["hidden_gems"]))
        self.card_heavy.set_value(sum(1 for s in segments if s.segment == "Heavy reader"))
        self.card_risk.set_value(sum(1 for r in risks if r.risk in ("High", "Medium")))
        self.card_risk.set_sub(f"{sum(1 for r in risks if r.risk == 'High')} high risk")

        self._fill_popularity(self.tbl_most_borrowed, pop["most_borrowed"])
        self._fill_popularity(self.tbl_hidden_gems, pop["hidden_gems"])
        self._fill_dead_stock(self.tbl_dead_stock, pop["dead_stock"])
        self._fill_segments(self.tbl_segments, segments)
        self._fill_risk(self.tbl_risk, risks)

    def _fill_popularity(self, tbl, rows):
        tbl.setRowCount(len(rows))
        for i, w in enumerate(rows):
            tbl.setItem(i, 0, QTableWidgetItem(w.title))
            tbl.setItem(i, 1, QTableWidgetItem(w.category))
            tbl.setItem(i, 2, QTableWidgetItem(str(w.copies)))
            tbl.setItem(i, 3, QTableWidgetItem(str(w.issue_count)))
            tbl.setItem(i, 4, QTableWidgetItem(f"{w.issues_per_copy:.1f}"))

    def _fill_dead_stock(self, tbl, rows):
        tbl.setRowCount(len(rows))
        for i, w in enumerate(rows):
            tbl.setItem(i, 0, QTableWidgetItem(w.title))
            tbl.setItem(i, 1, QTableWidgetItem(w.category))
            tbl.setItem(i, 2, QTableWidgetItem(str(w.copies)))
            tbl.setItem(i, 3, QTableWidgetItem(w.last_issue_date or "Never borrowed"))

    def _fill_segments(self, tbl, rows):
        tbl.setRowCount(len(rows))
        for i, s in enumerate(rows):
            tbl.setItem(i, 0, QTableWidgetItem(s.name))
            tbl.setItem(i, 1, QTableWidgetItem(str(s.total_issues)))
            tbl.setItem(i, 2, QTableWidgetItem(
                str(s.days_since_last_issue) if s.days_since_last_issue is not None else "—"))
            tbl.setItem(i, 3, QTableWidgetItem(s.segment))
            tbl.setItem(i, 4, QTableWidgetItem(s.reliability))

    def _fill_risk(self, tbl, rows):
        t = self._t
        tbl.setRowCount(len(rows))
        risk_color = {"High": t.danger_fg, "Medium": t.warning_fg, "Low": t.success_fg}
        for i, r in enumerate(rows):
            tbl.setItem(i, 0, QTableWidgetItem(r.member_name))
            tbl.setItem(i, 1, QTableWidgetItem(r.book_title))
            tbl.setItem(i, 2, QTableWidgetItem(r.due_date))
            tbl.setItem(i, 3, QTableWidgetItem(str(r.days_until_due)))
            tbl.setItem(i, 4, QTableWidgetItem(str(r.member_prior_late_count)))
            risk_item = QTableWidgetItem(r.risk)
            risk_item.setForeground(Qt.GlobalColor.white)
            risk_item.setBackground(QColor(risk_color[r.risk]))
            tbl.setItem(i, 5, risk_item)
            tbl.setItem(i, 6, QTableWidgetItem(r.reason))
