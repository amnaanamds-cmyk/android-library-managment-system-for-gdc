"""
ui/theme.py - the single source of colour for the desktop app.

Why this file exists
--------------------
Every screen used to pick its own colours inline. The dashboard alone had
seven stat cards in seven saturated gradients and six quick-action buttons in
six more, so the same product looked different on every screen and nothing on
a screen looked more important than anything else.

The rule here is: colour carries meaning, or it is not used.

  * Neutrals do the layout. Surfaces, borders and text are greys.
  * ACCENT marks the one interactive thing on a screen.
  * POSITIVE / WARNING / DANGER mark state, and only state - an overdue count
    turns red because it is overdue, not because it is the fifth card.

A card is not coloured because it is a card. If every card is coloured, the
overdue one stops standing out, which is the only one a librarian needs to
see from across the desk.

Usage:
    from ui.theme import palette
    p = palette(is_dark)
    lbl.setStyleSheet(f"color: {p['text']};")
"""

# Shared across both modes. These are the only saturated colours in the app,
# and each one means something specific.
ACCENT = "#1D4ED8"          # the primary action, links, active nav
ACCENT_HOVER = "#1E40AF"
POSITIVE = "#15803D"        # available, returned, in-sync, approved
WARNING = "#B45309"         # issued, pending, needs attention
DANGER = "#B91C1C"          # overdue, failed, suspended

LIGHT = {
    "mode": "light",
    "bg": "#F8FAFC",         # window background
    "surface": "#FFFFFF",    # cards, tables, inputs
    "surface_alt": "#F1F5F9",# table headers, hover, subtle fills
    "border": "#E2E8F0",
    "border_strong": "#CBD5E1",
    "text": "#0F172A",       # headings, figures
    "text_body": "#334155",  # paragraphs
    "text_muted": "#64748B", # labels, captions, timestamps
    "accent": ACCENT,
    "accent_hover": ACCENT_HOVER,
    "accent_soft": "#EFF6FF",
    "positive": POSITIVE,
    "positive_soft": "#F0FDF4",
    "warning": WARNING,
    "warning_soft": "#FFFBEB",
    "danger": DANGER,
    "danger_soft": "#FEF2F2",
    "on_accent": "#FFFFFF",
    # matplotlib
    "chart_bg": "#FFFFFF",
    "chart_grid": "#E2E8F0",
    "chart_text": "#334155",
    "chart_series": ACCENT,
    "chart_series_alt": "#93C5FD",
}

DARK = {
    "mode": "dark",
    "bg": "#0F172A",
    "surface": "#1E293B",
    "surface_alt": "#0F172A",
    "border": "#334155",
    "border_strong": "#475569",
    "text": "#F1F5F9",
    "text_body": "#CBD5E1",
    "text_muted": "#94A3B8",
    # Lifted a step on dark: #1D4ED8 on #1E293B fails contrast for small text.
    "accent": "#60A5FA",
    "accent_hover": "#93C5FD",
    "accent_soft": "#1E3A5F",
    "positive": "#4ADE80",
    "positive_soft": "#14342A",
    "warning": "#FBBF24",
    "warning_soft": "#3B2F14",
    "danger": "#F87171",
    "danger_soft": "#3B1D1D",
    "on_accent": "#0F172A",
    "chart_bg": "#1E293B",
    "chart_grid": "#334155",
    "chart_text": "#CBD5E1",
    "chart_series": "#60A5FA",
    "chart_series_alt": "#1E40AF",
}


def palette(is_dark: bool = False) -> dict:
    """Return the colour tokens for the active mode."""
    return DARK if is_dark else LIGHT


def current_palette() -> dict:
    """Palette for whatever mode the main window is in right now.

    Screens are built before and rebuilt after a theme toggle, so they can ask
    for this at build time. Falls back to light if the window does not exist
    yet (during startup, or in tests).
    """
    try:
        from ui.main_window import MainWindow
        win = MainWindow.instance()
        if win is not None:
            return palette(bool(getattr(win, "is_dark", False)))
    except Exception:
        pass
    return LIGHT
