"""
ui/dialogs/backup_restore_dialog.py — Backup & Restore Data dialog.

Wraps existing, already-in-use data operations (BackupService.do_backup /
restore_from_backup / export_full_backup_zip, DatabaseHelper.clear_all_data,
FirebaseService.clear_all_cloud_data) in one dedicated dialog instead of the
scattered Settings buttons, per DESIGN_SPEC.md.

Two safety additions beyond what already existed:
  1. Clear All Data now requires re-entering the current user's password
     (AuthService.verify_password) before it runs — previously a single
     Yes/No confirmation was the only gate.
  2. Both Clear All Data and Restore take an automatic timestamped backup
     immediately before running, on top of restore's own existing
     pre_restore safety copy — so there is always a fallback even if
     nobody thought to export one first.

NEXLIB syncs to Firestore (unlike a fully offline product) — the copy here
is honest about that instead of claiming "nothing leaves this machine."
Restore only ever touches the LOCAL database; it does not push the
restored data to Firestore, since a synced institution's next sync cycle
would otherwise overwrite other devices with a possibly-stale backup. That
is called out explicitly in the dialog so it isn't a silent surprise.
"""
from pathlib import Path

from PyQt6.QtWidgets import (
    QDialog, QVBoxLayout, QHBoxLayout, QLabel, QPushButton, QLineEdit,
    QFileDialog, QMessageBox, QFrame, QWidget
)
from PyQt6.QtCore import Qt

from ui.theme import Tokens
from services.backup_service import BackupService


class BackupRestoreDialog(QDialog):
    def __init__(self, db_helper, firebase_service, auth_service, tokens: Tokens, parent=None):
        super().__init__(parent)
        self.db = db_helper
        self.fb = firebase_service
        self.auth = auth_service
        self.t = tokens
        self.backup_service = BackupService(self.db)

        self.setWindowTitle("Backup & Restore Data")
        self.setMinimumWidth(560)
        self._build_ui()

    # ── UI ───────────────────────────────────────────────────────────────────
    def _build_ui(self):
        t = self.t
        self.setStyleSheet(f"QDialog {{ background: {t.surface}; color: {t.text_primary}; }}")
        outer = QVBoxLayout(self)
        outer.setContentsMargins(24, 24, 24, 24)
        outer.setSpacing(16)

        title = QLabel("🛡️  Backup & Restore Data")
        title.setStyleSheet(f"font-size: 18px; font-weight: 800; color: {t.text_primary};")
        outer.addWidget(title)

        sub = QLabel("Secure your library's data with export, backup and restore options")
        sub.setStyleSheet(f"color: {t.text_secondary}; font-size: 12px;")
        outer.addWidget(sub)

        # Honest data-location notice — NEXLIB syncs, unlike a fully offline product.
        notice = QLabel(
            "ℹ️ Your data is stored on this computer AND synced to your institution's "
            "cloud account. Exporting a local backup protects you if this machine's disk "
            "fails; it does not replace the cloud copy other devices see."
        )
        notice.setWordWrap(True)
        notice.setStyleSheet(f"""
            background: {t.info_bg}; color: {t.info_fg};
            border-radius: 8px; padding: 12px; font-size: 12px;
        """)
        outer.addWidget(notice)

        outer.addWidget(self._section(
            "⬇️  Export / Download Backup",
            "Save a full backup of this college's library data to your computer, a USB "
            "stick, or a cloud drive.",
            self._build_export_row(),
        ))

        outer.addWidget(self._section(
            "🔄  Restore from Backup",
            "Restoring replaces this device's LOCAL data with the backup's contents. "
            "It does not touch the cloud copy — export a backup first if you want to "
            "keep what's here now.",
            self._build_restore_row(),
            warn=True,
        ))

        outer.addWidget(self._section(
            "⚠️  Danger Zone — Clear All Data",
            "Permanently deletes every book, member, loan and reservation for this "
            "institution — locally AND in the cloud, on every connected device. "
            "This cannot be undone. A safety backup is taken automatically first.",
            self._build_danger_row(),
            warn=True,
        ))

        close_row = QHBoxLayout()
        close_row.addStretch()
        close_btn = QPushButton("Close")
        close_btn.setStyleSheet(f"background: {t.surface_sunken}; color: {t.text_primary}; padding: 8px 20px; border-radius: 8px;")
        close_btn.clicked.connect(self.accept)
        close_row.addWidget(close_btn)
        outer.addLayout(close_row)

    def _section(self, heading: str, body: str, content: QWidget, warn: bool = False) -> QFrame:
        t = self.t
        frame = QFrame()
        border = t.danger_fg if warn else t.surface_border
        frame.setStyleSheet(f"""
            QFrame {{ background: {t.surface_muted}; border: 1px solid {border}; border-radius: 10px; }}
        """)
        lay = QVBoxLayout(frame)
        lay.setContentsMargins(16, 14, 16, 14)
        lay.setSpacing(8)

        head = QLabel(heading)
        head.setStyleSheet(f"font-weight: 700; font-size: 13px; color: {t.text_primary}; background: transparent;")
        lay.addWidget(head)

        desc = QLabel(body)
        desc.setWordWrap(True)
        desc.setStyleSheet(f"color: {t.text_secondary}; font-size: 11.5px; background: transparent;")
        lay.addWidget(desc)

        lay.addWidget(content)
        return frame

    def _build_export_row(self) -> QWidget:
        t = self.t
        row = QWidget()
        h = QHBoxLayout(row)
        h.setContentsMargins(0, 4, 0, 0)
        btn = QPushButton("⬇️  Download Backup (.zip)")
        btn.setStyleSheet(f"background: {t.accent_600}; color: {t.text_on_accent}; font-weight: 700; padding: 10px 16px; border-radius: 8px;")
        btn.clicked.connect(self._on_export)
        h.addWidget(btn)
        h.addStretch()
        return row

    def _build_restore_row(self) -> QWidget:
        t = self.t
        row = QWidget()
        h = QHBoxLayout(row)
        h.setContentsMargins(0, 4, 0, 0)
        btn = QPushButton("📂  Choose Backup File & Restore…")
        btn.setStyleSheet(f"background: {t.surface}; color: {t.text_primary}; border: 1px solid {t.surface_border}; font-weight: 700; padding: 10px 16px; border-radius: 8px;")
        btn.clicked.connect(self._on_restore)
        h.addWidget(btn)
        h.addStretch()
        return row

    def _build_danger_row(self) -> QWidget:
        t = self.t
        row = QWidget()
        v = QVBoxLayout(row)
        v.setContentsMargins(0, 4, 0, 0)
        v.setSpacing(8)

        label = QLabel("YOUR PASSWORD")
        label.setStyleSheet(f"color: {t.text_tertiary}; font-size: 10px; font-weight: 700; background: transparent;")
        v.addWidget(label)

        self.password_input = QLineEdit()
        self.password_input.setEchoMode(QLineEdit.EchoMode.Password)
        self.password_input.setPlaceholderText("Enter your password…")
        self.password_input.setStyleSheet(f"background: {t.surface}; border: 1px solid {t.surface_border}; border-radius: 8px; padding: 10px; color: {t.text_primary};")
        v.addWidget(self.password_input)

        btn_row = QHBoxLayout()
        btn_row.addStretch()
        delete_btn = QPushButton("Delete Everything")
        delete_btn.setStyleSheet(f"background: {t.danger_fg}; color: white; font-weight: 700; padding: 10px 20px; border-radius: 8px;")
        delete_btn.clicked.connect(self._on_clear_all)
        btn_row.addWidget(delete_btn)
        v.addLayout(btn_row)
        return row

    # ── Actions ──────────────────────────────────────────────────────────────
    def _on_export(self):
        default_name = f"NEXLIB_Backup_{__import__('time').strftime('%Y%m%d_%H%M%S')}.zip"
        save_path, _ = QFileDialog.getSaveFileName(self, "Save Backup", default_name, "ZIP Files (*.zip)")
        if not save_path:
            return
        try:
            path = self.backup_service.export_full_backup_zip(save_path)
            QMessageBox.information(self, "Backup Saved", f"Backup saved to:\n{path}")
        except Exception as e:
            QMessageBox.warning(self, "Backup Failed", f"Could not create backup: {e}")

    def _on_restore(self):
        path, _ = QFileDialog.getOpenFileName(self, "Select Backup", "", "Backup Files (*.db *.zip)")
        if not path:
            return
        if path.lower().endswith(".zip"):
            QMessageBox.warning(
                self, "Unzip First",
                "This restores a raw database backup (.db). Extract the .db file from "
                "that ZIP first, then choose it here."
            )
            return

        confirm = QMessageBox.warning(
            self, "Confirm Restore",
            "This replaces this device's LOCAL library data with the selected backup.\n\n"
            "It does not affect the cloud copy or other devices. This cannot be undone "
            "for THIS device — a safety copy of what's here now will be taken first.\n\n"
            "Continue?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
        )
        if confirm != QMessageBox.StandardButton.Yes:
            return

        try:
            self.backup_service.do_backup(backup_type="pre_restore_manual")
            self.backup_service.restore_from_backup(path)
            QMessageBox.information(
                self, "Restore Complete",
                "Local data restored. Restart the app for every screen to pick up the change."
            )
        except Exception as e:
            QMessageBox.critical(self, "Restore Failed", f"Restore did not complete: {e}")

    def _on_clear_all(self):
        password = self.password_input.text()
        if not password:
            QMessageBox.warning(self, "Password Required", "Enter your password to proceed.")
            return
        if not self.auth.verify_password(password):
            QMessageBox.warning(self, "Incorrect Password", "That password is incorrect.")
            return

        confirm = QMessageBox.warning(
            self, "Final Confirmation",
            "This permanently deletes every book, member, loan and reservation for "
            "this institution — locally AND in the cloud, on every connected device.\n\n"
            "A safety backup will be taken first, but this action itself cannot be "
            "undone. Are you absolutely sure?",
            QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
        )
        if confirm != QMessageBox.StandardButton.Yes:
            return

        try:
            self.backup_service.do_backup(backup_type="pre_clear")
        except Exception as e:
            proceed = QMessageBox.warning(
                self, "Backup Failed",
                f"Could not take a safety backup first: {e}\n\nProceed anyway without one?",
                QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No,
            )
            if proceed != QMessageBox.StandardButton.Yes:
                return

        try:
            if not self.fb.mock_mode:
                self.fb.clear_all_cloud_data()
            self.db.clear_all_data()
            QMessageBox.information(self, "Data Cleared", "All data has been deleted. The app will now close.")
            from PyQt6.QtWidgets import QApplication
            QApplication.quit()
        except Exception as e:
            QMessageBox.critical(self, "Clear Failed", f"Could not clear all data: {e}")
