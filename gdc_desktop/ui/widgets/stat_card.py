"""
ui/widgets/stat_card.py — Shared stat-card widget.

One template for every dashboard tile, per DESIGN_SPEC.md §3: icon chip,
uppercase label (+ optional pill), a big value, an optional caption, and
an optional progress bar. Replaces the ad-hoc StatCard/DoubleStatCard
classes that used to be duplicated with slightly different inline styles
per screen — one widget, colors from ui/theme.py, so a theme fix here
reaches every screen that uses it instead of needing to be repeated.
"""
from typing import Optional

from PyQt6.QtWidgets import QFrame, QVBoxLayout, QHBoxLayout, QLabel, QProgressBar
from PyQt6.QtCore import Qt

from ui.theme import Tokens


class StatCard(QFrame):
    def __init__(
        self,
        icon: str,
        label: str,
        tokens: Tokens,
        accent_fg: Optional[str] = None,
        accent_bg: Optional[str] = None,
        pill: Optional[str] = None,
        sub: Optional[str] = None,
        parent=None,
    ):
        super().__init__(parent)
        self._tokens = tokens
        self._accent_fg = accent_fg or tokens.accent_600
        self._accent_bg = accent_bg or tokens.accent_100
        self._build_ui(icon, label, pill, sub)

    def _build_ui(self, icon: str, label: str, pill: Optional[str], sub: Optional[str]):
        t = self._tokens
        self.setStyleSheet(f"""
            StatCard {{
                background: {t.surface};
                border: 1px solid {t.surface_border};
                border-radius: 12px;
            }}
        """)
        outer = QVBoxLayout(self)
        outer.setContentsMargins(16, 14, 16, 14)
        outer.setSpacing(6)

        top = QHBoxLayout()
        top.setSpacing(10)
        chip = QLabel(icon)
        chip.setFixedSize(36, 36)
        chip.setAlignment(Qt.AlignmentFlag.AlignCenter)
        chip.setStyleSheet(f"""
            background: {self._accent_bg};
            color: {self._accent_fg};
            border-radius: 8px;
            font-size: 16px;
        """)
        top.addWidget(chip)

        label_lbl = QLabel(label.upper())
        label_lbl.setStyleSheet(f"color: {t.text_secondary}; font-size: 11px; font-weight: 700; letter-spacing: 0.5px; background: transparent;")
        top.addWidget(label_lbl, stretch=1)

        if pill:
            pill_lbl = QLabel(pill)
            pill_lbl.setStyleSheet(f"""
                background: {t.surface_sunken};
                color: {t.text_tertiary};
                border-radius: 9px;
                padding: 2px 8px;
                font-size: 10px;
                font-weight: 700;
            """)
            top.addWidget(pill_lbl)
        outer.addLayout(top)

        self.value_lbl = QLabel("0")
        self.value_lbl.setStyleSheet(f"color: {t.text_primary}; font-size: 26px; font-weight: 800; background: transparent;")
        outer.addWidget(self.value_lbl)

        self.sub_lbl = QLabel(sub or "")
        self.sub_lbl.setStyleSheet(f"color: {t.text_tertiary}; font-size: 11px; background: transparent;")
        self.sub_lbl.setVisible(bool(sub))
        outer.addWidget(self.sub_lbl)

        self.progress = QProgressBar()
        self.progress.setTextVisible(False)
        self.progress.setFixedHeight(4)
        self.progress.setRange(0, 100)
        self.progress.setValue(0)
        self.progress.setStyleSheet(f"""
            QProgressBar {{
                background: {t.surface_sunken};
                border: none;
                border-radius: 2px;
            }}
            QProgressBar::chunk {{
                background: {self._accent_fg};
                border-radius: 2px;
            }}
        """)
        self.progress.setVisible(False)
        outer.addWidget(self.progress)

    def set_value(self, value):
        self.value_lbl.setText(str(value))

    def set_sub(self, text: str):
        self.sub_lbl.setText(text)
        self.sub_lbl.setVisible(bool(text))

    def set_progress(self, percent: Optional[float]):
        if percent is None:
            self.progress.setVisible(False)
            return
        self.progress.setVisible(True)
        self.progress.setValue(max(0, min(100, int(percent))))
