"""
ui/main_window.py
Main application window with collapsible sidebar, dark/light theme toggle,
inactivity auto-logout timer, and role-based navigation.
"""
import time
import os
from PyQt6 import QtWidgets, QtCore, QtGui
from PyQt6.QtCore import Qt
import config

from ui.agent_overlay import AgentOverlay
from services.agent_service import LibraryAgent

class NavButton(QtWidgets.QPushButton):
    def __init__(self, icon: str, label: str, parent=None):
        super().__init__(parent)
        self.setCheckable(False)
        self._active = False
        self.setObjectName("navBtn")

        layout = QtWidgets.QHBoxLayout(self)
        layout.setContentsMargins(16, 0, 16, 0)
        layout.setSpacing(12)

        self.icon_lbl = QtWidgets.QLabel(icon)
        self.icon_lbl.setStyleSheet("font-size: 18px; background: transparent;")

        self.text_lbl = QtWidgets.QLabel(label)
        self.text_lbl.setStyleSheet("font-weight: 500; font-size: 14px; background: transparent;")

        layout.addWidget(self.icon_lbl)
        layout.addWidget(self.text_lbl)
        layout.addStretch()

        self.setMinimumHeight(50)

    def set_active(self, val: bool):
        self._active = val
        self.setProperty("active", "true" if val else "false")
        color = "#FFFFFF" if val else "#94A3B8"
        if not MainWindow.instance().is_dark and not val:
            color = "#64748B"

        self.text_lbl.setStyleSheet(f"font-weight: {'600' if val else '500'}; font-size: 14px; color: {color}; background: transparent;")
        self.style().unpolish(self)
        self.style().polish(self)


class MainWindow(QtWidgets.QMainWindow):
    logout_requested = QtCore.pyqtSignal()

    NAV_SECTIONS = [
        ("Core Services", [
            ("🏠", "Dashboard",        "dashboard"),
            ("🛂", "Gate Log",         "visitor_log"),
            ("📋", "Issue / Return",   "issue_return"),
        ]),
        ("Inventory & Catalog", [
            ("📚", "Books",            "books"),
            ("🌐", "Digital Library",  "digital"),
            ("📑", "MARC Catalog",     "marc"),
            ("🗂️", "Classification",   "classification"),
            ("🏷️", "Spine Labels",     "spine_label"),
            ("📦", "Inventory",        "inventory"),
            ("💰", "Acquisitions",     "acquisitions"),
            ("📰", "Serials",          "serials"),
            ("⭐", "Wishlist",         "wishlist"),
        ]),
        ("Patron Management", [
            ("👥", "Members",          "members"),
            ("🔐", "Biometric",        "biometric"),
            ("🌐", "Union Catalogue",  "union_catalog"),
            ("🌍", "ILL Network",      "ill"),
            ("🔄", "Book Transfers",   "transfers"),
            ("🪪", "Digital ID Cards", "digital_id"),
            ("🔗", "Sync Network",     "sync_network"),
        ]),
        ("Analytics & Intelligence", [
            ("🔍", "OPAC Monitor",     "opac"),
            ("📊", "Reports",          "reports"),
            ("🤖", "AI Recommender",   "recommendations"),
            ("🔥", "Usage Heatmap",    "heatmap"),
            ("🎯", "Reading Goals",    "reading_goals"),
        ]),
        ("Administration", [
            ("🏛️", "College Profile",  "college_profile"),
            ("🚀", "Enterprise Feat.", "enterprise"),
            ("⚖️", "Fine Waiver AI",   "fine_waiver"),
            ("⚙️", "Settings",         "settings"),
        ])
    ]
    
    DIRECTOR_NAV_SECTIONS = [
        ("Director", [
            ("🎯", "Director Dashboard", "director"),
            ("📊", "Reports",            "reports"),
        ])
    ]
    DIRECTORATE_NAV_SECTIONS = [
        ("Directorate", [
            ("🌍", "Directorate Dashboard", "directorate"),
            ("📊", "Reports",               "reports"),
        ])
    ]

    _instance = None
    @classmethod
    def instance(cls): return cls._instance

    def __init__(self, auth_service, firebase_service, db_helper, sync_service):
        super().__init__()
        MainWindow._instance = self
        self.auth = auth_service
        self.fb = firebase_service
        self.db = db_helper
        self.sync = sync_service
        self.is_dark = (getattr(config, 'DEFAULT_THEME', 'dark') == "dark")
        self.current_screen = ""
        self._nav_btns = {}

        self.agent = LibraryAgent(self.db)
        self.agent_overlay = None

        self._last_activity = time.time()
        self._idle_timer = QtCore.QTimer(self)
        self._idle_timer.timeout.connect(self._check_inactivity)
        self._idle_timer.start(30000)

        self.setWindowTitle(f"{config.APP_NAME} — {config.APP_VERSION}")
        self.setMinimumSize(1280, 800)
        self._build_ui()
        self._apply_theme()
        
        self.sync.sync_status.connect(self._update_sync_status)
        self.sync.data_updated.connect(self._cmd_refresh)
        if self.auth.is_directorate_admin:
            self.navigate_to("directorate")
        else:
            self.navigate_to("director" if self.auth.is_director else "dashboard")
        self._setup_shortcuts()

    def _setup_shortcuts(self):
        from PyQt6.QtGui import QKeySequence, QShortcut
        QShortcut(QKeySequence("Ctrl+F"), self).activated.connect(self._cmd_search)
        QShortcut(QKeySequence("Ctrl+M"), self).activated.connect(self._cmd_add_member)
        QShortcut(QKeySequence("Ctrl+N"), self).activated.connect(self._cmd_add_book)
        QShortcut(QKeySequence("Ctrl+I"), self).activated.connect(self._cmd_issue)
        QShortcut(QKeySequence("F5"), self).activated.connect(self._cmd_refresh)

    def _cmd_search(self):
        if self.current_screen in ("books", "members", "opac"):
            screen = self._screens[self.current_screen]
            if hasattr(screen, "search_bar"):
                screen.search_bar.setFocus()

    def _cmd_add_member(self):
        self.navigate_to("members")
        if hasattr(self._screens["members"], "_add_member"):
            self._screens["members"]._add_member()

    def _cmd_add_book(self):
        self.navigate_to("books")
        if hasattr(self._screens["books"], "_add_book"):
            self._screens["books"]._add_book()

    def _cmd_issue(self):
        self.navigate_to("issue_return")
        if hasattr(self._screens["issue_return"], "_issue_book"):
            self._screens["issue_return"]._issue_book()

    def _cmd_refresh(self):
        screen = self._screens.get(self.current_screen)
        if hasattr(screen, "refresh"):
            screen.refresh()

    def _build_ui(self):
        central = QtWidgets.QWidget()
        self.setCentralWidget(central)
        root = QtWidgets.QHBoxLayout(central)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)

        self.sidebar = QtWidgets.QFrame()
        self.sidebar.setObjectName("sidebar")
        self.sidebar.setFixedWidth(240)
        sb_main_layout = QtWidgets.QVBoxLayout(self.sidebar)
        sb_main_layout.setContentsMargins(0, 0, 0, 0)
        sb_main_layout.setSpacing(0)

        sb_scroll = QtWidgets.QScrollArea()
        sb_scroll.setWidgetResizable(True)
        sb_scroll.setStyleSheet("QScrollArea { border: none; background: transparent; }")

        sb_container = QtWidgets.QWidget()
        sb_container.setObjectName("sidebarContainer")
        sb_layout = QtWidgets.QVBoxLayout(sb_container)
        sb_layout.setContentsMargins(16, 20, 16, 4)
        sb_layout.setSpacing(4)

        # Dynamic Institutional Branding
        reg = self.db.get_college_registration()
        c_name = reg.get("name", getattr(config, 'COLLEGE_NAME', 'GDC LIBRARY50')).upper()
        brand = QtWidgets.QLabel(c_name)
        brand.setObjectName("appBrand")
        brand.setWordWrap(True)
        sb_layout.addWidget(brand)

        self.agent_btn = QtWidgets.QPushButton("🤖  AI ASSISTANT")
        self.agent_btn.setObjectName("agentBtn")
        self.agent_btn.clicked.connect(self._toggle_agent)
        sb_layout.addWidget(self.agent_btn)

        self.sync_btn = QtWidgets.QPushButton("\U0001f504  FORCE SYNC NOW")
        self.sync_btn.setObjectName("syncBtn")
        self.sync_btn.setStyleSheet("background: #1E5FD4; color: white; font-weight: bold; border-radius: 8px; padding: 12px; margin-top: 5px;")
        self.sync_btn.clicked.connect(self._do_manual_sync)
        sb_layout.addWidget(self.sync_btn)

        self.link_btn = QtWidgets.QPushButton("\U0001f4f1  LINK MOBILE DEVICE")
        self.link_btn.setObjectName("linkBtn")
        self.link_btn.setStyleSheet("background: #059669; color: white; font-weight: bold; border-radius: 8px; padding: 12px; margin-top: 5px;")
        self.link_btn.clicked.connect(self._show_link_qr)
        sb_layout.addWidget(self.link_btn)

        user = self.auth.current_user
        role_badge = user.role.upper() if user else "UNKNOWN"
        self.user_lbl = QtWidgets.QLabel(f"{(user.name or user.email) if user else 'User'}\n{role_badge}")
        self.user_lbl.setObjectName("userInfo")
        self.user_lbl.setWordWrap(True)
        sb_layout.addWidget(self.user_lbl)

        divider = QtWidgets.QFrame()
        divider.setFixedHeight(1)
        divider.setObjectName("sbDivider")
        sb_layout.addWidget(divider)

        if self.auth.is_directorate_admin:
            nav_sections = self.DIRECTORATE_NAV_SECTIONS
        elif self.auth.is_director:
            nav_sections = self.DIRECTOR_NAV_SECTIONS
        else:
            nav_sections = self.NAV_SECTIONS
            
        for section_name, items in nav_sections:
            sec_lbl = QtWidgets.QLabel(section_name.upper())
            sec_lbl.setObjectName("navSectionHeader")
            sec_lbl.setStyleSheet("color: #64748B; font-size: 11px; font-weight: 700; padding-top: 10px; padding-bottom: 2px;")
            sb_layout.addWidget(sec_lbl)
            
            for icon, label, key in items:
                btn = NavButton(icon, label)
                btn.clicked.connect(lambda _, k=key: self.navigate_to(k))
                self._nav_btns[key] = btn
                sb_layout.addWidget(btn)

        sb_layout.addSpacerItem(QtWidgets.QSpacerItem(0, 0, QtWidgets.QSizePolicy.Policy.Minimum,
                                            QtWidgets.QSizePolicy.Policy.Expanding))

        sb_scroll.setWidget(sb_container)
        sb_main_layout.addWidget(sb_scroll)

        # ── Fixed bottom panel (always visible, never scrolls away) ──────────
        bottom_panel = QtWidgets.QWidget()
        bottom_panel.setObjectName("sidebarBottomPanel")
        bottom_layout = QtWidgets.QVBoxLayout(bottom_panel)
        bottom_layout.setContentsMargins(12, 8, 12, 12)
        bottom_layout.setSpacing(6)

        # Thin divider
        div = QtWidgets.QFrame()
        div.setFixedHeight(1)
        div.setObjectName("sbDivider")
        bottom_layout.addWidget(div)

        self.status_lbl = QtWidgets.QLabel("⏳ Connecting...")
        self.status_lbl.setObjectName("statusBar")
        self.status_lbl.setAlignment(QtCore.Qt.AlignmentFlag.AlignCenter)
        bottom_layout.addWidget(self.status_lbl)

        self.theme_btn = QtWidgets.QPushButton("☀️  Light Mode" if self.is_dark else "🌙  Dark Mode")
        self.theme_btn.setObjectName("themeActionBtn")
        self.theme_btn.clicked.connect(self._toggle_theme)
        bottom_layout.addWidget(self.theme_btn)

        logout_btn = QtWidgets.QPushButton("🚪  Logout")
        logout_btn.setObjectName("logoutBtn")
        logout_btn.setMinimumHeight(42)
        logout_btn.setStyleSheet(
            "QPushButton#logoutBtn { background: #DC2626; color: white; font-weight: bold; "
            "border-radius: 8px; padding: 10px; font-size: 13px; }"
            "QPushButton#logoutBtn:hover { background: #EF4444; }"
        )
        logout_btn.clicked.connect(self._do_logout)
        bottom_layout.addWidget(logout_btn)

        sb_main_layout.addWidget(bottom_panel)
        root.addWidget(self.sidebar)

        self.stack = QtWidgets.QStackedWidget()
        root.addWidget(self.stack)
        self._init_screens()

    def _init_screens(self):
        # LAZY INITIALIZATION: We don't create all 20+ screens at once to prevent startup freezing.
        # Screens will be created on-demand in navigate_to().
        self._screens = {}
        self._scroll_areas = {}

    def navigate_to(self, key: str):
        if self.auth.is_directorate_admin and key not in ("directorate", "reports", "transfers"):
            return
        if self.auth.is_director and not self.auth.is_directorate_admin and key not in ("director", "reports", "transfers"):
            return

        # Lazy load the screen if it doesn't exist
        if key not in self._screens:
            self._load_screen(key)

        if key not in self._scroll_areas:
            return

        self.current_screen = key
        self.stack.setCurrentWidget(self._scroll_areas[key])
        for k, btn in self._nav_btns.items():
            btn.set_active(k == key)
        
        screen = self._screens[key]
        if hasattr(screen, "refresh"):
            screen.refresh()

    def _load_screen(self, key: str):
        """Internal helper to create a screen instance on demand."""
        try:
            is_ro = self.auth.is_director or self.auth.is_directorate_admin
            screen = None

            if key == "dashboard":
                from ui.screens.dashboard_screen import DashboardScreen
                screen = DashboardScreen(self.fb, self.db)
            elif key == "visitor_log":
                from ui.screens.visitor_log_screen import VisitorLogScreen
                screen = VisitorLogScreen(self.db, self.fb)
            elif key == "books":
                from ui.screens.books_screen import BooksScreen
                screen = BooksScreen(self.fb, self.db, self.auth, read_only=is_ro)
            elif key == "members":
                from ui.screens.members_screen import MembersScreen
                screen = MembersScreen(self.fb, self.db, self.auth, read_only=is_ro)
            elif key == "issue_return":
                from ui.screens.issue_return_screen import IssueReturnScreen
                screen = IssueReturnScreen(self.fb, self.db, self.auth)
            elif key == "opac":
                from ui.screens.opac_screen import OpacScreen
                screen = OpacScreen(self.fb, self.db)
            elif key == "digital":
                from ui.screens.digital_library_screen import DigitalLibraryScreen
                screen = DigitalLibraryScreen(self.db)
            elif key == "marc":
                from ui.screens.marc_catalog_screen import MarcCatalogScreen
                screen = MarcCatalogScreen(self.db)
            elif key == "inventory":
                from ui.screens.inventory_screen import InventoryScreen
                screen = InventoryScreen(self.db)
            elif key == "ill":
                from ui.screens.ill_screen import ILLScreen
                screen = ILLScreen(self.db, self.fb)
            elif key == "acquisitions":
                from ui.screens.acquisitions_screen import AcquisitionsScreen
                screen = AcquisitionsScreen(self.db, self.fb)
            elif key == "serials":
                from ui.screens.serials_screen import SerialsScreen
                screen = SerialsScreen(self.db, self.fb)
            elif key == "sync_network":
                # College pairing, so a Windows-only librarian can join the
                # network without needing the web app.
                from ui.screens.sync_network_screen import SyncNetworkScreen
                screen = SyncNetworkScreen(self.fb)
            elif key == "wishlist":
                from ui.screens.wishlist_screen import WishlistScreen
                screen = WishlistScreen(self.fb, self.auth)
            elif key == "enterprise":
                from ui.screens.enterprise_screen import EnterpriseFeaturesScreen
                screen = EnterpriseFeaturesScreen(self.db, self.fb)
            elif key == "college_profile":
                from ui.screens.college_profile_screen import CollegeProfileScreen
                screen = CollegeProfileScreen(self.db, self.fb)
            elif key == "reports":
                from ui.screens.reports_screen import ReportsScreen
                screen = ReportsScreen(self.fb, self.db, self.auth)
            elif key == "settings":
                from ui.screens.settings_screen import SettingsScreen
                screen = SettingsScreen(self.fb, self.auth, self.db)
            elif key == "director":
                from ui.screens.director_screen import DirectorScreen
                screen = DirectorScreen(self.fb, self.db)
            elif key == "transfers":
                from ui.screens.transfer_screen import TransferScreen
                screen = TransferScreen()
            elif key == "classification":
                from ui.screens.classification_screen import ClassificationScreen
                screen = ClassificationScreen(self.db)
            elif key == "spine_label":
                from ui.screens.spine_label_screen import SpineLabelScreen
                screen = SpineLabelScreen(self.db)
            elif key == "biometric":
                from ui.screens.biometric_screen import BiometricScreen
                screen = BiometricScreen(self.db)
            elif key == "union_catalog":
                from ui.screens.union_catalog_screen import UnionCatalogScreen
                screen = UnionCatalogScreen(self.fb, self.db)
            elif key == "recommendations":
                from ui.screens.recommendation_screen import RecommendationScreen
                screen = RecommendationScreen(self.db)
            elif key == "heatmap":
                from ui.screens.heatmap_screen import HeatmapScreen
                screen = HeatmapScreen(self.db)
            elif key == "fine_waiver":
                from ui.screens.fine_waiver_screen import FineWaiverScreen
                screen = FineWaiverScreen(self.db, self.agent)
            elif key == "reading_goals":
                from ui.screens.reading_goals_screen import ReadingGoalsScreen
                screen = ReadingGoalsScreen(self.db)
            elif key == "digital_id":
                from ui.screens.digital_id_screen import DigitalIdScreen
                screen = DigitalIdScreen(self.db)
            if screen:
                if key in ("dashboard", "settings", "enterprise", "director"):
                    screen.setMinimumHeight(1000)

                scroll = QtWidgets.QScrollArea()
                scroll.setWidgetResizable(True)
                scroll.setWidget(screen)
                scroll.setStyleSheet("QScrollArea { border: none; background: transparent; }")
                self.stack.addWidget(scroll)
                self._screens[key] = screen
                self._scroll_areas[key] = scroll
        except Exception as e:
            import logging
            logging.getLogger(__name__).error(f"Failed to lazy-load screen {key}: {e}", exc_info=True)


    def _update_sync_status(self, text: str):
        from ui.theme import palette
        p = palette(self.is_dark)
        self.status_lbl.setText(text)
        if "Offline" in text:
            self.status_lbl.setStyleSheet(f"color: {p['danger']};")
        elif "Syncing" in text:
            self.status_lbl.setStyleSheet(f"color: {p['warning']};")
        else:
            self.status_lbl.setStyleSheet(f"color: {p['positive']};")

        # Re-enable the manual sync button on the real outcome rather than on a
        # timer. It used to say "RECONNECTED" two seconds after the click
        # whether or not anything had connected.
        if getattr(self, "_manual_sync_pending", False) and "Syncing" not in text:
            self._manual_sync_pending = False
            self.sync_btn.setEnabled(True)
            self.sync_btn.setText("\U0001f504  FORCE SYNC NOW")

    def _toggle_agent(self):
        if not self.agent_overlay:
            self.agent_overlay = AgentOverlay(self.agent, self)
            self.agent_overlay.show()
            self._reposition_agent()
        else:
            if self.agent_overlay.isVisible():
                self.agent_overlay.hide()
            else:
                self.agent_overlay.show()
                self._reposition_agent()

    def _reposition_agent(self):
        if self.agent_overlay:
            margin = 20
            self.agent_overlay.move(
                self.width() - self.agent_overlay.width() - margin,
                self.height() - self.agent_overlay.height() - margin
            )

    def resizeEvent(self, event):
        super().resizeEvent(event)
        self._reposition_agent()

    def _toggle_theme(self):
        self.is_dark = not self.is_dark
        # Persist theme choice in config and database
        config.DEFAULT_THEME = "dark" if self.is_dark else "light"
        self.db.set_college_registration("theme_preference", config.DEFAULT_THEME)

        self._apply_theme()
        self.theme_btn.setText("☀️  Light Mode" if self.is_dark else "🌙  Dark Mode")

        # Force refresh of current screen to apply new colors to sub-widgets
        self._cmd_refresh()

    def _apply_theme(self):
        base_qss = ""
        try:
            style_path = os.path.join(os.path.dirname(os.path.dirname(__file__)), "assets", "style.qss")
            if os.path.exists(style_path):
                with open(style_path, "r") as f:
                    base_qss = f.read()
        except Exception:
            pass
            
        if self.is_dark:
            colors = """
            QMainWindow, QWidget { background: #0F172A; color: #F1F5F9; font-family: 'Inter', 'Segoe UI'; }
            QFrame#sidebar { background: #1E293B; border-right: 1px solid #334155; }

            QLabel { color: #F1F5F9; }
            QLabel#appBrand { color: #3B82F6; font-size: 20px; font-weight: 800; margin-bottom: 20px; }
            QLabel#userInfo { color: #94A3B8; border-bottom: 1px solid #334155; padding-bottom: 10px; margin-bottom: 10px; }
            QLabel#navSectionHeader { color: #64748B; font-size: 11px; font-weight: 700; padding-top: 15px; padding-bottom: 5px; }

            QPushButton { background-color: #2563EB; color: white; border: none; border-radius: 8px; padding: 8px 16px; font-weight: 600; }
            QPushButton:hover { background-color: #3B82F6; }

            QPushButton#navBtn { background: transparent; color: #94A3B8; border: none; text-align: left; padding: 12px; font-weight: 500; border-radius: 8px; }
            QPushButton#navBtn:hover { background: #334155; color: #F1F5F9; }
            QPushButton#navBtn[active="true"] { background: #2563EB; color: #FFFFFF; font-weight: 600; }

            QPushButton#agentBtn { background: #3B82F6; color: white; font-weight: bold; border-radius: 8px; padding: 12px; }

            /* Inputs */
            QLineEdit, QSpinBox, QDoubleSpinBox, QComboBox, QDateEdit, QTextEdit {
                background: #1E293B; border: 1.5px solid #334155; color: #F1F5F9; border-radius: 8px; padding: 10px;
            }
            QLineEdit:focus { border: 1.5px solid #3B82F6; }

            /* Tables */
            QTableWidget, QTableView { background: #1E293B; color: #F1F5F9; gridline-color: #334155; border: 1px solid #334155; selection-background-color: #2563EB; }
            QHeaderView::section { background: #0F172A; color: #94A3B8; border-bottom: 1px solid #334155; }

            /* Tabs */
            QTabWidget::pane { border: 1px solid #334155; background: #1E293B; }
            QTabBar::tab { background: #0F172A; color: #94A3B8; padding: 10px 20px; }
            QTabBar::tab:selected { background: #1E293B; color: #F1F5F9; border-bottom: 2px solid #3B82F6; }

            /* Cards */
            QGroupBox, QFrame#Card { background: #1E293B; border: 1px solid #334155; border-radius: 12px; }
            QGroupBox::title { color: #3B82F6; }

            /* Scrollbars */
            QScrollBar:vertical { background: #0F172A; width: 10px; }
            QScrollBar::handle:vertical { background: #334155; border-radius: 5px; }
            """
            self.setStyleSheet(base_qss + colors)
        else:
            colors = """
            QMainWindow, QWidget { background: #F8FAFC; color: #1E293B; font-family: 'Inter', 'Segoe UI'; }
            QFrame#sidebar { background: #FFFFFF; border-right: 1px solid #E2E8F0; }

            QLabel { color: #1E293B; }
            QLabel#appBrand { color: #1E40AF; font-size: 20px; font-weight: 800; margin-bottom: 20px; }
            QLabel#userInfo { color: #64748B; border-bottom: 1px solid #E2E8F0; padding-bottom: 10px; margin-bottom: 10px; }
            QLabel#navSectionHeader { color: #94A3B8; font-size: 11px; font-weight: 700; padding-top: 15px; padding-bottom: 5px; }

            QPushButton { background-color: #2563EB; color: white; border: none; border-radius: 8px; padding: 8px 16px; font-weight: 600; }
            QPushButton:hover { background-color: #1D4ED8; }

            QPushButton#navBtn { background: transparent; color: #64748B; border: none; text-align: left; padding: 12px; font-weight: 500; border-radius: 8px; }
            QPushButton#navBtn:hover { background: #F1F5F9; color: #1E293B; }
            QPushButton#navBtn[active="true"] { background: #2563EB; color: #FFFFFF; font-weight: 600; }

            QPushButton#agentBtn { background: #2563EB; color: white; font-weight: bold; border-radius: 8px; padding: 12px; }

            /* Input fields */
            QLineEdit, QSpinBox, QDoubleSpinBox, QComboBox, QDateEdit, QTextEdit {
                background: #FFFFFF; border: 1.5px solid #E2E8F0; color: #1E293B; border-radius: 8px; padding: 10px;
            }
            QLineEdit:focus, QComboBox:focus, QDateEdit:focus, QTextEdit:focus, QSpinBox:focus, QDoubleSpinBox:focus {
                border: 1.5px solid #2563EB; background: #FFFFFF;
            }

            /* Tables */
            QTableWidget, QTableView { background: #FFFFFF; color: #1E293B; gridline-color: #F1F5F9; border: 1px solid #E2E8F0; selection-background-color: #EFF6FF; selection-color: #2563EB; outline: none; border-radius: 8px; }
            QHeaderView::section { background: #F8FAFC; color: #64748B; border: none; border-bottom: 2px solid #F1F5F9; font-weight: 700; font-size: 12px; padding: 10px; }
            QTableWidget::item { padding: 8px; border-bottom: 1px solid #F1F5F9; }

            /* Tabs */
            QTabWidget::pane { border: 1px solid #E2E8F0; border-radius: 8px; background: #FFFFFF; }
            QTabBar::tab { background: #F8FAFC; color: #64748B; padding: 12px 24px; font-weight: 600; border-top-left-radius: 8px; border-top-right-radius: 8px; margin-right: 4px; }
            QTabBar::tab:selected { background: #FFFFFF; color: #2563EB; border-bottom: 3px solid #2563EB; }

            /* Cards */
            QGroupBox, QFrame#Card { background: #FFFFFF; border: 1px solid #E2E8F0; border-radius: 12px; }
            QGroupBox::title { color: #2563EB; }

            QScrollArea { border: none; background: transparent; }
            QScrollBar:vertical { background: #F8FAFC; width: 10px; border-radius: 5px; }
            QScrollBar::handle:vertical { background: #E2E8F0; border-radius: 5px; }
            QScrollBar::handle:vertical:hover { background: #CBD5E1; }
            """
            self.setStyleSheet(base_qss + colors)

    def _do_manual_sync(self):
        self.sync_btn.setEnabled(False)
        self.sync_btn.setText("Syncing…")
        self._manual_sync_pending = True

        # Returns immediately; the sync thread does the work and reports back
        # through sync_status, which re-enables this button.
        self.sync.force_reconnect()
        self._cmd_refresh()

        # Safety net: if the thread never reports (it is dead, or mock mode
        # emits nothing), give the button back rather than leaving it stuck.
        QtCore.QTimer.singleShot(15000, self._release_manual_sync)

    def _release_manual_sync(self):
        if getattr(self, "_manual_sync_pending", False):
            self._manual_sync_pending = False
            self.sync_btn.setEnabled(True)
            self.sync_btn.setText("\U0001f504  FORCE SYNC NOW")

    def _show_link_qr(self):
        try:
            import qrcode
            from io import BytesIO

            inst_id = self.fb.college_id or "gdc11"
            role = self.auth.role or "admin"
            # Format: PREFIX|INST_ID|ROLE|TIMESTAMP
            qr_data = f"NEXLIB_LINK|{inst_id}|{role}|{int(time.time())}"

            # Generate QR
            qr = qrcode.QRCode(version=1, box_size=10, border=5)
            qr.add_data(qr_data)
            qr.make(fit=True)
            img = qr.make_image(fill_color="black", back_color="white")

            # Convert to QPixmap
            buffer = BytesIO()
            img.save(buffer, format="PNG")
            qimg = QtGui.QImage.fromData(buffer.getvalue())
            pixmap = QtGui.QPixmap.fromImage(qimg)

            # Show Dialog
            dlg = QtWidgets.QDialog(self)
            dlg.setWindowTitle("Link Mobile Device")
            dlg.setFixedSize(400, 500)
            dlg.setStyleSheet("background: white; color: black;")

            vlay = QtWidgets.QVBoxLayout(dlg)
            vlay.setContentsMargins(30,30,30,30)

            title = QtWidgets.QLabel("WhatsApp-Style Device Link")
            title.setStyleSheet("font-size: 18px; font-weight: bold; color: #1E3A8A;")
            title.setAlignment(Qt.AlignmentFlag.AlignCenter)
            vlay.addWidget(title)

            desc = QtWidgets.QLabel("Open NEXLIB on your phone and scan this code to login instantly.")
            desc.setWordWrap(True)
            desc.setAlignment(Qt.AlignmentFlag.AlignCenter)
            vlay.addWidget(desc)

            qr_lbl = QtWidgets.QLabel()
            qr_lbl.setPixmap(pixmap.scaled(300, 300, Qt.AspectRatioMode.KeepAspectRatio))
            qr_lbl.setAlignment(Qt.AlignmentFlag.AlignCenter)
            vlay.addWidget(qr_lbl)

            close_btn = QtWidgets.QPushButton("Close")
            close_btn.clicked.connect(dlg.close)
            vlay.addWidget(close_btn)

            dlg.exec()
        except Exception as e:
            QtWidgets.QMessageBox.warning(self, "QR Error", f"Could not generate QR: {e}")

    def _do_logout(self, auto: bool = False):
        msg = "You have been logged out due to inactivity." if auto else "Are you sure you want to log out?"
        if not auto:
            reply = QtWidgets.QMessageBox.question(self, "Logout", msg,
                                         QtWidgets.QMessageBox.StandardButton.Yes |
                                         QtWidgets.QMessageBox.StandardButton.No)
            if reply == QtWidgets.QMessageBox.StandardButton.No:
                return
        self.auth.sign_out()
        self.sync.stop()
        self._idle_timer.stop()
        self.logout_requested.emit()

    def mousePressEvent(self, event):
        self._last_activity = time.time()
        super().mousePressEvent(event)

    def keyPressEvent(self, event):
        self._last_activity = time.time()
        super().keyPressEvent(event)

    def _check_inactivity(self):
        elapsed = time.time() - self._last_activity
        if elapsed > getattr(config, 'INACTIVITY_TIMEOUT', 900):
            self._do_logout(auto=True)

    def closeEvent(self, event):
        if hasattr(self, "sync") and self.sync:
            self.sync.stop()
        if hasattr(self, "_idle_timer") and self._idle_timer:
            self._idle_timer.stop()
        super().closeEvent(event)
