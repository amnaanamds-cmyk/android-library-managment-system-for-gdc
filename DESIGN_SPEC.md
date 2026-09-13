# NEXLIB — Design Spec

A reference design system for bringing NEXLIB's three apps (web, desktop,
Android) to a consistent, professional visual standard. Sourced from the
owner's own Hostyllo Offline hostel-management app
(`github.com/mushtaqahmaduop/HOSTIX-APP`, `renderer/tokens.css` and
`CLAUDE.md`) — a live, WCAG-checked, 50-installs-in-production design
system, not a screenshot approximation. Every value below is real, copied
from that source. This is a spec to implement carefully and verify
per-platform later, not a same-day rewrite.

---

## 1. Why it reads as professional — the governing rules

Quoted/adapted directly from that project's own design governance, because
they're good rules on their own merits, not just "how that app happens to
look":

- **Color = meaning.** If a color isn't telling the user something, it
  shouldn't be there. One primary accent color, used sparingly — not a
  different bright color invented per screen.
- **One primary accent action per screen.** Color means "act," never
  "look." A screen with five blue buttons has zero primary actions.
- **Categories are neutral; hue is reserved for state.** A payment-method
  badge (cash/card/bank) should be gray/neutral. A status badge (paid,
  overdue, pending) is the only thing allowed to use color.
- **Every stat card follows one template** (see §3) — icon chip, uppercase
  label, big number, secondary caption, optional progress/sparkline.
  Consistency across cards is what makes a dashboard read as a system
  rather than a pile of widgets.
- **Design tokens are the single source of truth.** A raw hex value
  hardcoded into a component is a bug, not a style choice — every color
  traces back to one token file. This is the single biggest gap in
  NEXLIB today: colors are hardcoded per-screen on every platform, which
  is why fixing one contrast bug never fixed the next one.
- **Dark surfaces must span more than 8 lightness points apart** for
  visible contrast between stacked layers (card vs. page vs. sunken
  areas). A dark theme built from one or two near-black tones with no
  separation is what makes stacked cards look flat/muddy.
- **Money that might be compared uses tabular numerals** (fixed digit
  width) so columns of numbers align and don't jitter as values change.
- **One currency formatter, used everywhere, never duplicated** — this
  project's own bug history includes a "double-prefix" bug from having
  two currency-formatting code paths. NEXLIB should have exactly one
  `formatCurrency()`/`fmtRs()` per platform, called everywhere money is
  displayed, not a `"Rs. " + value` string built inline per-screen (which
  is exactly how NEXLIB's `$`/`₹` bugs happened — this spec's rule would
  have prevented both).
- **QA floor: 1366×768.** Whatever the design, it must hold up at that
  resolution before shipping — a lot of "looks great on my 27" monitor"
  designs break earlier than expected.

---

## 2. Color tokens (exact values, light + dark)

### Light mode
| Token | Value | Used for |
|---|---|---|
| `surface` | `#FFFFFF` | Card background |
| `surface-muted` | `#FAFBFD` | Subtle recessed areas |
| `surface-sunken` | `#F5F6F9` | Page background |
| `surface-border` | `#E4E7EE` | Card/input borders |
| `surface-divider` | `#D3D8E2` | Stronger dividers |
| `text-primary` | `#152238` | Headings, big numbers |
| `text-secondary` | `#5B647A` | Labels, body text |
| `text-tertiary` | `#677187` | Timestamps, helper text, metadata |
| `text-on-accent` | `#FFFFFF` | Text on a filled accent button |
| `accent-600` (primary) | `#2451D6` | Buttons, active nav, links, focus rings |
| `accent-700` (hover/strong) | `#1B3FAE` | Hover states, pressed states |
| `accent-100` (soft) | `#D6E2FD` | Tinted backgrounds behind accent icons |
| `success-fg` / `success-bg` | `#1D8054` / `#E8F5EE` | Paid, positive, completed |
| `warning-fg` / `warning-bg` | `#95691A` / `#FBF2E2` | Pending, due soon |
| `danger-fg` / `danger-bg` | `#C0402F` / `#FBEBE8` | Overdue, error, destructive |
| `info-fg` / `info-bg` | `#1B3FAE` / `#EAF0FE` | Neutral informational badges |

### Dark mode — NOT a simple inversion
The source project's own hard rule: dark mode is *more* desaturated, not
less, and every dark surface is warmed rather than pure gray (pure-gray
dark themes "go muddy" against a colored accent). Also critical: **the
accent color itself must shift between themes** — the light accent
(`#2451D6`) is too dark to read on a near-black card, and a lifted accent
too light for a white button label. Two different values, not one value
reused:

| Token | Value | Used for |
|---|---|---|
| `surface` | `#1F1E1B` | Card background (warm near-black, not pure gray) |
| `surface-muted` | `#252320` | Subtle recessed areas |
| `surface-sunken` | `#181715` | Page background |
| `surface-border` | `#35322D` | Card/input borders |
| `surface-divider` | `#2A2724` | Stronger dividers |
| `text-primary` | `#FAF9F5` | Headings, big numbers |
| `text-secondary` | `#D8D5CD` | Labels, body text |
| `text-tertiary` | `#A09D96` | Timestamps, helper text |
| `text-on-accent` | `#181715` (near-black, **not white**) | Text on a filled accent button — see note below |
| `accent-600` | `#4E7DFF` | Buttons, active nav, links |
| `accent-700` | `#7BA0FF` | Accent-colored *text* (needs more lift than a button fill) |
| `success-fg` | `#3FBF83` on `rgba(63,191,131,0.14)` bg | Paid, positive |
| `warning-fg` | `#D9A441` on `rgba(217,164,65,0.14)` bg | Pending |
| `danger-fg` | `#F0796A` on `rgba(240,121,106,0.14)` bg | Overdue, error |
| `info-fg` | `#7BA0FF` on `rgba(123,160,255,0.14)` bg | Informational |

**Why `text-on-accent` is dark, not white, in dark mode:** this is a real,
measured accessibility fix worth preserving. A lifted accent bright enough
to read as *text* on a near-black card (`#4E7DFF`) fails contrast as a
*button fill* with white text (3.77:1, below WCAG AA). Darkening the fill
to pass with white text made the button itself nearly invisible against
the card. The fix that actually works: keep the lifted, legible accent
fill, and flip the label to near-black instead of white (4.89:1, passes).
**Test every accent-colored button's label contrast in dark mode
specifically — don't assume white-on-accent is always safe.**

---

## 3. Component patterns

### Stat card (the KPI tile pattern)
Real class structure from the source, directly portable as a naming
convention on any platform:

```
.stat-card                     (the outer card container)
  .stat-card__top               (flex row: icon chip + label, + optional pill on the right)
    .stat-card__chip             (icon badge, accent-tinted background)
    .stat-card__label            (11px, uppercase, letter-spacing .9px, text-secondary)
    .stat-card__pill              (optional status pill, right-aligned)
  .stat-card__value              (the big number — text-primary, tabular-nums if money)
  .stat-card__sub                (secondary caption, 11px, text-tertiary)
  .stat-card__track / __spark    (optional progress bar or mini sparkline)
```
Grid: `repeat(auto-fit, minmax(200px, 1fr))`, ~12px gap — cards reflow
naturally down to 2-column, then 1-column, rather than a fixed count that
overflows on a small window (remember the 1366×768 floor).

Elevation: a real, subtle `box-shadow` (not flat/borderless) — cards on a
tinted page background read as outlines rather than surfaces without one.

### Sidebar
- Section header: `text-tertiary`, 11px, uppercase, letter-spacing.
- Nav item: icon + label, one accent treatment for the active item
  (filled accent background + `text-on-accent` text is the simplest to
  keep consistent) — pick one convention and use it everywhere; NEXLIB
  currently has each screen invent its own "active" look.

### Top bar
Search field → date/context control → notification indicator → theme
toggle → 1-2 primary buttons (the more important one filled, the other
outlined). Exactly one filled/primary button visible at a time, per the
"one primary action" rule above.

### Status badges vs. category badges
Two visually distinct badge types, never conflated:
- **Status badge** (paid/overdue/pending): colored, from the semantic
  token set (`success`/`warning`/`danger`/`info`) — this is the *only*
  place those colors are allowed to appear.
  Corresponds to the pattern.
- **Category badge** (payment method, book category, department): always
  neutral gray, regardless of how many categories exist. Don't invent a
  color per category — that's decoration, not meaning, and it's exactly
  the kind of "different bright color per screen" the governing rules
  above warn against.

---

## 4. Per-platform notes

**web-app (Next.js/Tailwind)** — define the tokens above as CSS custom
properties (mirroring the source's own `:root` / `body.light-theme`
pattern is a proven, working approach — copy the *mechanism*, not just
the values) or a Tailwind theme extension. Build one `StatCard` component
everything reuses, instead of the current per-screen hardcoded cards.

**Android (Compose/Material3)** — express both token tables as a custom
`ColorScheme` so `MaterialTheme.colorScheme.*` resolves correctly in both
themes automatically. This is exactly the class of fix already applied
ad-hoc several times this session (Reports tabs, login screen, OPAC) —
a proper token-based `ColorScheme` prevents needing to hunt down the next
one individually. Pay specific attention to the `text-on-accent` dark-mode
finding above — check every filled-accent button's label contrast in dark
mode specifically, don't assume white text is always safe.

**Desktop (PyQt/QSS)** — implemented as `ui/theme.py` (token constants —
QSS has no `var()` mechanism, so these are plain Python, not CSS) and
`ui/widgets/stat_card.py` (the shared widget), applied first to
`dashboard_screen.py`. **Note:** the surface/text/accent values there are
NOT the raw hex from §2 above — `main_window.py`'s QSS already establishes
a cool navy-blue palette everywhere (`#0F172A`/`#1E293B`/`#334155`, accent
`#2563EB`), so `ui/theme.py` matches *that* instead of introducing
Hostyllo's warmer tones, which would clash sitting next to the app's
existing chrome. The semantic status colors (success/warning/danger/info)
are a pure addition harmonized with this same navy palette. If desktop
ever gets a full rebrand to the warmer palette, that's a deliberate,
all-at-once decision — not something to half-apply via one new widget.

**All platforms** — audit for hardcoded `$`/`₹`/`Rs.` string-building at
call sites (the exact bug pattern already found and fixed twice this
session) and replace with one shared currency-formatting function per
platform, per the governing rule above.

---

## 5. What this spec deliberately doesn't cover

- Exact spacing/radius scale — the source uses a 4/8/12/16/20/24/32/40/48px
  scale and 4/6/8/12px radii; adopt a similar scale and stay consistent
  rather than copying pixel-for-pixel.
- Urdu/RTL layout implications for the sidebar and top bar — separate,
  larger work flagged in `AppStrings.kt`.
- Icon set choice — pick one icon library per platform and use it
  everywhere instead of mixing emoji and vector icons.
- Font choice — the source uses Barlow (tabular figures for numerals,
  bundled locally since the app is offline-first); NEXLIB can pick its
  own, the *tabular-numerals-for-money* rule matters more than the
  specific typeface.
