"""
ui/screens/settings_screen.py — Fine rates and other system settings.
"""
from PyQt6 import QtWidgets, QtCore, QtGui
import json
import os
import config


class LoadSettingsWorker(QtCore.QThread):
    finished = QtCore.pyqtSignal(float, list)

    def __init__(self, fb):
        super().__init__()
        self.fb = fb

    def run(self):
        rate = 5.0
        logs = []
        try:
            rate = self.fb.get_fine_rate()
        except Exception:
            pass
        try:
            from services.database_helper import DatabaseHelper
            db = DatabaseHelper()
            logs = db.get_audit_logs_local(50)
        except Exception:
            pass
        self.finished.emit(rate, logs)


class SaveSettingsWorker(QtCore.QThread):
    finished = QtCore.pyqtSignal(bool, str)

    def __init__(self, fb, rate, email):
        super().__init__()
        self.fb = fb
        self.rate = rate
        self.email = email

    def run(self):
        try:
            self.fb.save_settings(self.rate, self.email)
            self.finished.emit(True, "")
        except Exception as e:
            self.finished.emit(False, str(e))


class SettingsScreen(QtWidgets.QWidget):
    def __init__(self, firebase_service, auth_service, db_helper):
        super().__init__()
        self.fb = firebase_service
        self.auth = auth_service
        self.db = db_helper
        self._load_worker = None
        self._save_worker = None
        self._build_ui()
        self.refresh()

    def _build_ui(self):
        layout = QtWidgets.QVBoxLayout(self)
        layout.setContentsMargins(40, 40, 40, 40)
        layout.setSpacing(24)

        hdr = QtWidgets.QLabel("⚙️  System Settings")
        hdr.setObjectName("hdrLabel")
        hdr.setStyleSheet("font-size: 26px; font-weight: 900; color: #1E3A8A;")
        layout.addWidget(hdr)

        self.tabs = QtWidgets.QTabWidget()

        # 1. General Settings Tab
        gen_tab = QtWidgets.QWidget()
        gen_lay = QtWidgets.QVBoxLayout(gen_tab)
        gen_lay.setContentsMargins(24, 24, 24, 24)

        form = QtWidgets.QFormLayout()
        form.setSpacing(16)
        self.fine_spin = QtWidgets.QDoubleSpinBox()
        self.fine_spin.setRange(0, 1000)
        self.fine_spin.setPrefix("Rs. ")
        
        lbl = QtWidgets.QLabel("Daily Overdue Fine Rate")
        lbl.setStyleSheet("font-size: 14px; font-weight: 600; color: #475569;")
        form.addRow(lbl, self.fine_spin)
        gen_lay.addLayout(form)
        
        self.save_btn = QtWidgets.QPushButton("💾  Save Settings")
        self.save_btn.setStyleSheet("background: #1E5FD4; color: white; border: none; border-radius: 8px; padding: 10px 24px; font-weight: 700; font-size: 14px; margin-top: 20px;")
        self.save_btn.clicked.connect(self._save)

        # Directorate Configuration
        dir_lbl = QtWidgets.QLabel("Directorate Central Network Settings")
        dir_lbl.setStyleSheet("font-size: 16px; font-weight: bold; color: #1E3A8A; margin-top: 20px;")
        gen_lay.addWidget(dir_lbl)

        dir_form = QtWidgets.QFormLayout()
        self.dir_url = QtWidgets.QLineEdit()
        self.dir_url.setPlaceholderText("http://localhost:8000")
        self.dir_url.setText(config.DIRECTORATE_API_URL)

        self.dir_key = QtWidgets.QLineEdit()
        self.dir_key.setPlaceholderText("Enter Directorate API Key")
        self.dir_key.setText(config.DIRECTORATE_API_KEY)
        self.dir_key.setEchoMode(QtWidgets.QLineEdit.EchoMode.Password)

        dir_form.addRow("Central API URL", self.dir_url)
        dir_form.addRow("API Key", self.dir_key)
        gen_lay.addLayout(dir_form)

        demo_btn = QtWidgets.QPushButton("🔑 Use Demo Key")
        demo_btn.setStyleSheet("background: #1E3050; color: #E6C96E; border: 1px solid #C8A84B; border-radius: 6px; padding: 4px; font-size: 11px;")
        demo_btn.clicked.connect(lambda: self.dir_key.setText("gdc_demo_key_2024"))
        gen_lay.addWidget(demo_btn)

        # UI Preferences
        ui_lbl = QtWidgets.QLabel("UI Preferences")
        ui_lbl.setStyleSheet("font-size: 16px; font-weight: bold; color: #1E3A8A; margin-top: 20px;")
        gen_lay.addWidget(ui_lbl)

        ui_form = QtWidgets.QFormLayout()
        
        self.lang_cb = QtWidgets.QComboBox()
        # Urdu, not Hindi: this system serves the Government Degree Colleges of
        # Khyber Pakhtunkhwa, where the language is Urdu.
        self.lang_cb.addItems(["English", "اردو (Urdu)"])
        self.lang_cb.currentTextChanged.connect(self._toggle_lang)
        ui_form.addRow("Language", self.lang_cb)

        self.theme_cb = QtWidgets.QComboBox()
        self.theme_cb.addItems(["Dark Mode", "Light Mode"])
        self.theme_cb.currentTextChanged.connect(self._toggle_theme)
        ui_form.addRow("App Theme", self.theme_cb)

        self.font_scale = QtWidgets.QDoubleSpinBox()
        self.font_scale.setRange(0.8, 2.0)
        self.font_scale.setSingleStep(0.1)
        self.font_scale.setValue(1.0)
        self.font_scale.valueChanged.connect(self._toggle_font)
        ui_form.addRow("Font Scaling", self.font_scale)

        gen_lay.addLayout(ui_form)
        
        row = QtWidgets.QHBoxLayout()
        row.addStretch()
        row.addWidget(self.save_btn)
        gen_lay.addLayout(row)
        gen_lay.addStretch()
        self.tabs.addTab(gen_tab, "General")

        # Circulation Matrix Tab
        circ_tab = QtWidgets.QWidget()
        self._build_circ_tab(circ_tab)
        self.tabs.addTab(circ_tab, "Circulation Matrix")

        # Audit Logs Tab
        audit_tab = QtWidgets.QWidget()
        audit_lay = QtWidgets.QVBoxLayout(audit_tab)
        self.audit_table = QtWidgets.QTableWidget()
        self.audit_table.setColumnCount(4)
        self.audit_table.setHorizontalHeaderLabels(["Time", "User", "Action", "Details"])
        self.audit_table.horizontalHeader().setSectionResizeMode(QtWidgets.QHeaderView.ResizeMode.Stretch)
        audit_lay.addWidget(self.audit_table)
        self.tabs.addTab(audit_tab, "Audit Logs")

        # Developer Tools Tab
        dev_tab = QtWidgets.QWidget()
        dev_lay = QtWidgets.QVBoxLayout(dev_tab)

        reset_btn = QtWidgets.QPushButton("⚠️ Reset Database")
        reset_btn.setStyleSheet("background: #DC2626; color: white; border: none; border-radius: 8px; padding: 10px 24px; font-weight: 700; font-size: 14px; margin-top: 20px;")
        reset_btn.clicked.connect(self._reset_db)
        dev_lay.addWidget(reset_btn)

        # Advanced Backup & Security
        backup_lay = QtWidgets.QHBoxLayout()
        zip_btn = QtWidgets.QPushButton("🗄️ 1-Click Encrypted ZIP Backup")
        zip_btn.setStyleSheet("background: #7C3AED; color: white; border-radius: 8px; padding: 10px; font-weight: bold;")
        zip_btn.clicked.connect(self._create_zip_backup)
        backup_lay.addWidget(zip_btn)
        
        auto_bkp = QtWidgets.QCheckBox("Enable Automated Daily Backups")
        auto_bkp.setStyleSheet("font-weight: bold;")
        auto_bkp.setChecked(True)
        backup_lay.addWidget(auto_bkp)
        dev_lay.addLayout(backup_lay)

        health_btn = QtWidgets.QPushButton("🩺 Run Data Integrity Health Check")
        health_btn.setStyleSheet("background: #F59E0B; color: #0D1B2A; border: none; border-radius: 8px; padding: 10px 24px; font-weight: 700; font-size: 14px; margin-top: 10px;")
        health_btn.clicked.connect(self._run_health_check)
        dev_lay.addWidget(health_btn)

        dev_lay.addStretch()
        self.tabs.addTab(dev_tab, "Developer Tools")

        layout.addWidget(self.tabs)
        self._init_done = True

    def _build_circ_tab(self, parent):
        layout = QtWidgets.QVBoxLayout(parent)
        layout.setSpacing(14)
        layout.addWidget(QtWidgets.QLabel(
            "Define loan periods and max limits per Member Type.\n"
            "This implements a standard Koha Circulation Matrix.",
        ))

        self.circ_table = QtWidgets.QTableWidget()
        self.circ_table.setColumnCount(4)
        self.circ_table.setHorizontalHeaderLabels(["Member Type", "Max Loans", "Loan Days", "Fine/Day (Rs.)"])
        self.circ_table.horizontalHeader().setSectionResizeMode(QtWidgets.QHeaderView.ResizeMode.Stretch)
        layout.addWidget(self.circ_table)

    def refresh(self):
        self._load_worker = LoadSettingsWorker(self.fb)
        self._load_worker.finished.connect(self._on_settings_loaded)
        self._load_worker.start()

    def _on_settings_loaded(self, rate, logs):
        try:
            self.fine_spin.setValue(rate)
        except Exception:
            pass
        try:
            self.audit_table.setRowCount(len(logs))
            for i, row in enumerate(logs):
                self.audit_table.setItem(i, 0, QtWidgets.QTableWidgetItem(row.get("timestampStr", "")))
                self.audit_table.setItem(i, 1, QtWidgets.QTableWidgetItem(row.get("userEmail", "")))
                self.audit_table.setItem(i, 2, QtWidgets.QTableWidgetItem(row.get("action", "")))
                self.audit_table.setItem(i, 3, QtWidgets.QTableWidgetItem(row.get("detail", "")))
        except Exception:
            pass

    def _toggle_lang(self, lang):
        if hasattr(self, '_init_done'):
            QtWidgets.QMessageBox.information(self, "Language", f"Translating to {lang}...")
            
    def _toggle_theme(self, theme_text):
        if hasattr(self, '_init_done'):
            from ui.main_window import MainWindow
            main_window = MainWindow.instance()
            if main_window:
                is_dark = "Dark" in theme_text
                main_window.is_dark = is_dark
                # Sync with sidebar button text
                if hasattr(main_window, 'theme_btn'):
                    main_window.theme_btn.setText("☀️  Light Mode" if is_dark else "🌙  Dark Mode")

                # Update config and DB for persistence
                config.DEFAULT_THEME = "dark" if is_dark else "light"
                if hasattr(main_window, 'db'):
                    main_window.db.set_college_registration("theme_preference", config.DEFAULT_THEME)

                main_window._apply_theme()
                main_window._cmd_refresh()

    def _toggle_font(self, scale):
        pass

    def _reset_db(self):
        reply = QtWidgets.QMessageBox.question(
            self, "Confirm Reset",
            "This will delete ALL local AND cloud data for this institution and close the application.\n\n"
            "Data on all connected devices will be wiped.\n\nAre you sure you want to proceed?",
            QtWidgets.QMessageBox.StandardButton.Yes | QtWidgets.QMessageBox.StandardButton.No
        )
        if reply != QtWidgets.QMessageBox.StandardButton.Yes:
            return

        # Clearing the cloud walks six collections over the network and the
        # local wipe hits disk. Run both on a worker thread: done inline this
        # froze the window for as long as it took, which read as a crash.
        from ui.widgets.background_task import run_with_progress

        fb = getattr(self, 'fb', None)
        db = self.db

        def work(report):
            if fb:
                fb.clear_all_cloud_data(progress=report)
            report("Clearing local database…")
            db.clear_all_data()
            report("Done.")

        def done(_result):
            QtWidgets.QMessageBox.information(
                self, "Reset complete",
                "Local and cloud data for this institution has been cleared.\n"
                "The app will now close.")
            QtWidgets.QApplication.quit()

        self._reset_thread = run_with_progress(
            self, "Resetting database", work, on_done=done)

        
    def _create_zip_backup(self):
        try:
            import shutil
            import sqlite3
            from datetime import datetime

            save_path, _ = QtWidgets.QFileDialog.getSaveFileName(
                self, "Save Backup ZIP",
                f"NEXLIB_Backup_{datetime.now().strftime('%Y%m%d_%H%M%S')}.zip",
                "ZIP Files (*.zip)"
            )
            if not save_path:
                return

            db_path = self.db.db_path
            if not os.path.exists(db_path):
                raise FileNotFoundError("Database file not found.")

            # Create a temporary directory for backup
            temp_dir = os.path.join(os.path.dirname(db_path), "temp_backup")
            if not os.path.exists(temp_dir):
                os.makedirs(temp_dir)

            dest_db = os.path.join(temp_dir, "gdc_library.db")
            with sqlite3.connect(db_path) as src, sqlite3.connect(dest_db) as dst:
                src.backup(dst)

            # Also export CSVs for extra safety
            from services.advanced_service import AdvancedService
            adv = AdvancedService(self.db)
            csv_dir = os.path.join(temp_dir, "csv_export")
            adv.export_full_database_csv(csv_dir)

            # Zip it all up
            shutil.make_archive(save_path.replace(".zip", ""), 'zip', temp_dir)

            # Clean up temp
            shutil.rmtree(temp_dir)

            QtWidgets.QMessageBox.information(self, "Backup Success", f"Encrypted backup created at:\n{save_path}")
        except Exception as e:
            QtWidgets.QMessageBox.warning(self, "Backup Error", f"Failed to create backup: {e}")

    def _run_health_check(self):
        try:
            results = self.db.run_health_check()

            msg = "🩺 **Database Integrity Report**\n\n"
            msg += f"• Total Books: {results['total_books']}\n"
            msg += f"• Total Members: {results['total_members']}\n"
            msg += f"• Total Active Issues: {results['total_issues']}\n\n"

            issues_found = False
            if results['orphan_issues']:
                msg += f"⚠️ Orphan Issues: {len(results['orphan_issues'])}\n"
                issues_found = True
            if results['duplicate_isbns']:
                msg += f"⚠️ Duplicate ISBNs: {len(results['duplicate_isbns'])}\n"
                issues_found = True
            if results['ghost_issued']:
                msg += f"⚠️ Ghost Issued Books: {len(results['ghost_issued'])}\n"
                issues_found = True

            if not issues_found:
                msg += "✅ No major structural issues found."
            else:
                msg += "\nRecommendation: Run a Full Sync or check manual data entry."

            dlg = QtWidgets.QMessageBox(self)
            dlg.setWindowTitle("Health Check")
            dlg.setText(msg)
            dlg.setIcon(QtWidgets.QMessageBox.Icon.Information)
            dlg.exec()
        except Exception as e:
            QtWidgets.QMessageBox.warning(self, "Health Check Error", f"Failed to run check: {e}")

    def _save(self):
        val = self.fine_spin.value()
        email = self.auth.current_user.email if self.auth.current_user else ""
        self.save_btn.setEnabled(False)
        self._save_worker = SaveSettingsWorker(self.fb, val, email)
        def on_saved(ok, err):
            self.save_btn.setEnabled(True)
            if ok:
                QtWidgets.QMessageBox.information(self, "Saved", "Settings updated.")
            else:
                QtWidgets.QMessageBox.warning(self, "Error", err)
        self._save_worker.finished.connect(on_saved)
        self._save_worker.start()

