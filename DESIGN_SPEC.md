# NEXLIB — Design Spec

A reference design system for bringing NEXLIB's three apps (web, desktop,
Android) to a consistent, professional visual standard — modeled on the
Hostyllo hostel-management dashboard the team benchmarked against. This is
a spec to implement carefully and verify per-platform later, not a
same-day rewrite.

Colors below are close approximations read off screenshots — sample the
real reference pixels (or just pick nearby values) before locking them in;
treat every hex here as "about this," not exact.

---

## 1. Why the reference reads as professional

Five concrete patterns, not a vague "polish" — copy the *pattern*, not
necessarily every color:

1. **One stat-card template, used everywhere.** Icon badge (top-left) →
   label (top-right, small caps) → status pill (e.g. "0% full", "0 paid")
   → big number (the actual value) → thin progress bar along the bottom
   edge. Every dashboard tile follows this exact shape. NEXLIB's current
   cards are inconsistent — some are emoji + number, some have no
   secondary context, none have a progress indicator.
2. **A grouped sidebar**, not a flat list. Section labels (MAIN / FINANCE
   / SYSTEM) in a small muted caps style, then nav items under each,
   with one accent color reserved for the active item.
3. **One dark navy + one accent-blue palette, applied consistently.**
   Every button, active state, and chart line pulls from the same small
   set of colors — not a different bright color invented per screen.
4. **Real data visualization**: a donut chart with a legend/breakdown
   table beside it; a trend line with multiple labeled series. Not just
   numbers in boxes.
5. **A calm, purposeful top bar**: search field (with a keyboard-shortcut
   hint), a date control, a notification bell, a theme toggle, then
   exactly one or two primary action buttons on the far right. Nothing
   competes for attention.

---

## 2. Color tokens

| Token | Approx. hex | Used for |
|---|---|---|
| `bg.page` | `#0B0F19` | Main window/page background |
| `bg.sidebar` | `#0F1420` | Sidebar background (very close to page, barely distinguished) |
| `bg.card` | `#151B28` | Card / stat-tile / panel background |
| `bg.cardAlt` | `#1B2333` | Table alternating rows, hover states |
| `border.subtle` | `#232B3D` | Card borders, dividers, input borders |
| `text.primary` | `#F1F5F9` | Main headings, big numbers |
| `text.secondary` | `#8B95A7` | Labels, captions, muted text |
| `accent.blue` | `#2F6FED` | Primary actions, active nav item, links, primary chart line |
| `accent.green` | `#22C55E` | Positive status, "paid"/"profit" pills, success actions |
| `accent.red` | `#EF4444` | Expenses, overdue, destructive actions |
| `accent.gold` | `#D9A441` | Pending/warning status, secondary highlight |
| `accent.purple` | `#8B5CF6` | A fourth category color when four+ stat cards need distinct icons |

Each stat card gets **one** accent color for its icon badge + progress
bar; the card body itself stays `bg.card` — don't tint the whole card,
just the icon chip and the progress fill, matching the reference exactly.

### Light mode

Don't just invert dark values — Hostyllo's own light-mode convention (and
NEXLIB's existing light theme in `main_window.py`) is: `bg.page` →
`#F8FAFC`, `bg.card` → `#FFFFFF`, `border.subtle` → `#E2E8F0`,
`text.primary` → `#1E293B`, `text.secondary` → `#64748B`. Keep the same
five accent colors, slightly deepened (`#2563EB` blue, `#16A34A` green,
`#DC2626` red) so they hold contrast against a white card.

---

## 3. Component patterns

### Stat card
```
┌─────────────────────────────┐
│ [icon]              [pill]  │  ← icon badge (accent color, ~40x40, rounded)
│ LABEL IN SMALL CAPS          │     pill: tiny rounded status, muted bg
│                               │
│ 123                           │  ← large number, text.primary, bold
│ secondary caption             │  ← text.secondary, small
│ ▬▬▬▬▬▬▬▬▬░░░░░░░░░░░░░░░░░░  │  ← progress bar, accent color fill
└─────────────────────────────┘
```
Icon badge: accent color at ~15-20% opacity as the chip background, full
accent color for the icon glyph itself. Progress bar: `border.subtle` as
the track, accent color as the fill, 3-4px tall, fully rounded ends.

### Sidebar
- Section header: `text.secondary`, 11px, uppercase, letter-spacing,
  8-12px vertical padding above each group.
- Nav item: icon + label, 8-10px vertical padding, rounded (8px) on
  hover/active. Active item: `accent.blue` background at full opacity
  with white text, or a left accent bar + tinted background — pick one
  convention and use it for every "active" state across all three apps
  (currently each screen invents its own).

### Top bar
Left-to-right: page title → search input (with a `Ctrl K`–style hint
right-aligned inside the field) → date/context control → notification
bell (badge dot for unread) → theme toggle → 1-2 primary buttons, the
most important one filled/solid, the secondary one outlined.

### Data viz
- Donut/pie: center label showing the total (`0/123` style), legend as a
  table to the right listing each category with a mini progress bar per
  row — not just a color key.
- Trend line: labeled series in the card header (colored dot + name),
  gridlines subtle (`border.subtle`), points marked, axis labels in
  `text.secondary`.

Use the `dataviz` skill's palette/contrast rules when actually building
these charts on any platform — it has the accessibility and dark/light
validation this spec doesn't repeat.

---

## 4. Per-platform notes

**web-app (Next.js/Tailwind)** — most tractable to implement and verify
(the only platform where `npm run build` catches real errors before a
human looks at it). Define the tokens above as CSS variables or a
`tailwind.config` extension once, then rebuild the stat-card component,
sidebar, and top bar as shared components everything else reuses — right
now colors and card markup are duplicated per-screen.

**Android (Jetpack Compose / Material3)** — express the tokens as a custom
`ColorScheme` (or extend the existing `Theme.kt`) so `MaterialTheme.colorScheme.*`
resolves to these values automatically in both light and dark — this also
retroactively fixes any remaining contrast bugs, since it removes the
temptation to hardcode colors per-screen. Build one `StatCard` composable
matching the pattern above and replace the ad-hoc ones in `DashboardScreen.kt`
and elsewhere.

**Desktop (PyQt/QSS)** — extend `main_window.py`'s dark/light QSS blocks
with these tokens (the recent alternating-row-color fix already lives in
that same block, so add these while you're in there), and build one
reusable stat-card widget class instead of the current one-off
`StatCard`/`DoubleStatCard` functions duplicated with different styling
per screen.

---

## 5. What this spec deliberately doesn't cover

- Exact pixel values for spacing/radii — measure from the reference or
  pick sensible round numbers (8px/12px/16px) and stay consistent.
- Urdu/RTL layout implications for the sidebar and top bar — a separate,
  larger piece of work flagged elsewhere (`AppStrings.kt`).
- Icon set choice (Material Icons vs. a custom set) — pick one icon
  library per platform and use it everywhere instead of mixing emoji and
  vector icons, which is part of why the current UI reads as inconsistent.
