"""
ui/theme.py — Design tokens for the desktop app.

Single source of truth for color, per DESIGN_SPEC.md at the repo root.
Qt Style Sheets have no var()/custom-property mechanism, so — unlike the
web app's globals.css — these are plain Python constants: build widget
stylesheets with f-strings against these names instead of hardcoding hex
values per screen. That hardcoding (a different bright color invented per
screen, a hex typed inline) is exactly what made the app's contrast bugs
hard to fix consistently — one central place means one fix reaches every
screen that imports it.

Surface/text/accent values are NOT the raw DESIGN_SPEC.md hex values —
they're matched to the cool navy-blue palette main_window.py's QSS blocks
already establish everywhere (#0F172A/#1E293B/#334155/#F1F5F9, accent
#2563EB), so this new widget set sits on the app's existing chrome
instead of introducing a second, warmer palette that would clash next to
it. The semantic status colors (success/warning/danger/info) are a pure
addition — nothing in the app defines these consistently today — chosen
to read clearly against this same navy palette.
"""
from dataclasses import dataclass


@dataclass(frozen=True)
class Tokens:
    surface: str
    surface_muted: str
    surface_sunken: str
    surface_border: str
    surface_divider: str

    text_primary: str
    text_secondary: str
    text_tertiary: str
    text_on_accent: str

    accent_600: str
    accent_700: str
    accent_100: str

    success_fg: str
    success_bg: str
    warning_fg: str
    warning_bg: str
    danger_fg: str
    danger_bg: str
    info_fg: str
    info_bg: str


DARK = Tokens(
    surface="#1E293B",
    surface_muted="#243247",
    surface_sunken="#0F172A",
    surface_border="#334155",
    surface_divider="#2A3A52",
    text_primary="#F1F5F9",
    text_secondary="#94A3B8",
    text_tertiary="#64748B",
    text_on_accent="#FFFFFF",
    accent_600="#2563EB",
    accent_700="#3B82F6",
    accent_100="rgba(37, 99, 235, 0.16)",
    success_fg="#34D399",
    success_bg="rgba(16, 185, 129, 0.14)",
    warning_fg="#FBBF24",
    warning_bg="rgba(245, 158, 11, 0.14)",
    danger_fg="#F87171",
    danger_bg="rgba(239, 68, 68, 0.14)",
    info_fg="#7DD3FC",
    info_bg="rgba(14, 165, 233, 0.14)",
)

LIGHT = Tokens(
    surface="#FFFFFF",
    surface_muted="#F8FAFC",
    surface_sunken="#F1F5F9",
    surface_border="#E2E8F0",
    surface_divider="#CBD5E1",
    text_primary="#1E293B",
    text_secondary="#64748B",
    text_tertiary="#94A3B8",
    text_on_accent="#FFFFFF",
    accent_600="#2563EB",
    accent_700="#1D4ED8",
    accent_100="#DBEAFE",
    success_fg="#059669",
    success_bg="#D1FAE5",
    warning_fg="#B45309",
    warning_bg="#FEF3C7",
    danger_fg="#DC2626",
    danger_bg="#FEE2E2",
    info_fg="#0284C7",
    info_bg="#E0F2FE",
)


def tokens(dark: bool) -> Tokens:
    """Look up the active token set. Call with the same bool the rest of
    the app uses to pick dark/light (e.g. MainWindow.instance().is_dark)."""
    return DARK if dark else LIGHT
