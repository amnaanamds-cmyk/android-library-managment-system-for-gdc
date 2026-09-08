# NEXLIB — Developer Guide

How the system fits together, and how to run all four surfaces from Android
Studio and VS Code.

For production rollout — signing, Vercel, Firebase plans, onboarding a real
college — see **[DEPLOYMENT.md](DEPLOYMENT.md)**. This document is about
running it on your own machine.

---

## 1. What the system is

Four client surfaces sharing **one Firebase project**. No surface has its own
database or its own schema; they all read and write the same Firestore
documents, and each keeps a local cache so it works offline.

| Surface | Stack | Runs in | Scope |
|---|---|---|---|
| **Mobile** | Kotlin, Jetpack Compose, SQLDelight | Android Studio | Own institution |
| **Desktop** | Python 3.10+, PyQt6, SQLite | VS Code | Own institution |
| **Web** | Next.js 16, React 19 | VS Code | Own institution |
| **Director portal** | Next.js 16, route `/director` | VS Code | Read-only, all institutions |
| **Backend** | Cloud Functions (TypeScript) | VS Code | Registration, claims, rollup |

The directorate is deliberately **not** a member of any college. It reads
aggregate documents only; no patron record ever leaves the college that owns it.

---

## 2. Repository structure

```
android-library-managment-system-for-gdc/
│
├── firestore.rules              ← the security boundary. Read this first.
├── firestore.indexes.json          Composite indexes for every query issued.
├── firebase.json                   Rules, indexes, functions, emulator ports.
│
├── functions/                   ← BACKEND (TypeScript, Cloud Functions v2)
│   ├── src/
│   │   ├── config.ts               Collection names, roles, region. Shared truth.
│   │   ├── claims.ts               Mints { role, institutionId } onto accounts.
│   │   ├── registration.ts         Institution onboarding + approval gate.
│   │   ├── summary.ts              Scheduled directorate rollup.
│   │   └── index.ts                Exports; region and instance limits.
│   └── test/
│       ├── summary.test.mjs        Rollup correctness (17 cases).
│       └── e2e.test.mjs            Two-institution walkthrough (28 cases).
│
├── app/                         ← ANDROID (Kotlin + Compose)
│   ├── build.gradle.kts             Signing config reads keystore.properties.
│   ├── google-services.json         Firebase config — must match every surface.
│   └── src/main/java/com/college/library/
│       ├── MainActivity.kt          Navigation host.
│       ├── data/                    Room DAOs, MARC, RealtimeSyncManager.
│       ├── domain/usecase/          Issue, return, fine calculation.
│       ├── ui/screens/              ~50 feature screens.
│       └── utils/                   Barcodes, receipts, overdue workers.
│
├── shared/                      ← KOTLIN MULTIPLATFORM (used by app + desktopApp)
│   └── src/commonMain/
│       ├── kotlin/.../data/         Models, SyncService, ConflictResolver.
│       └── sqldelight/              .sq schema + migrations.
│
├── gdc_desktop/                 ← DESKTOP (Python + PyQt6) — AUTHORITATIVE
│   ├── main.py                      Entry point (QApplication).
│   ├── config.py                    Loads .env. Never hardcodes secrets.
│   ├── gdc_desktop.spec             PyInstaller build.
│   ├── models/                      Book, Member, IssueRecord, Reservation.
│   ├── services/
│   │   ├── database_helper.py       SQLite: tables, sync_queue, conflicts.
│   │   ├── firebase_service.py      Firestore access (Admin SDK — see note).
│   │   ├── sync_service.py          The offline-first sync engine.
│   │   ├── auth_service.py          Auth REST sign-in, claims, roles.
│   │   └── registry_service.py      Publishes the legacy self-reported rollup.
│   └── ui/screens/                  ~30 PyQt6 screens.
│
├── web-app/                     ← WEB + DIRECTOR PORTAL (Next.js) — AUTHORITATIVE
│   ├── vercel.json                  Build config, Mumbai region, headers.
│   ├── .env.example
│   └── src/
│       ├── lib/
│       │   ├── firebase.ts          SDK init + offline persistence.
│       │   ├── auth-context.tsx     Claims, 30-min idle logout.
│       │   ├── schema.ts            Collection names + record shapes.
│       │   └── directorate.ts       Summary reads, district rollups.
│       └── app/
│           ├── dashboard/           A college's own workspace (~29 routes).
│           └── director/            Directorate portal
│               ├── page.tsx            Network overview + approvals.
│               ├── register/           Onboarding form.
│               └── [collegeId]/        Per-college drill-down.
│
├── tests/
│   ├── firestore/rules.test.mjs     Security rules (67 cases).
│   └── desktop/test_sync_engine.py  Offline sync (29 cases).
│
├── DEPLOYMENT.md                ← production runbook
├── SYNC_ARCHITECTURE.md            data layout and sync envelope
└── RUNNING.md                      this file
```

### Two directories that are NOT part of the system

- **`desktopApp/`** — a Compose Desktop (Kotlin) client. It is **superseded** by
  the Python `gdc_desktop/`. It still compiles and is still in
  `settings.gradle.kts`, so Android Studio will show it, but the Python client
  is the one that ships. Ignore it, and ignore KMP-desktop references in older
  planning files.
- **`web/`** — an abandoned Next.js starter scaffold. `web-app/` is the real
  one. Safe to delete.

---

## 2a. Pulling the latest changes

Everything is on the branch **`claude/repo-contents-review-6e0ub3`**, not
`main`. A fresh `git clone` lands you on `main`, which does **not** have any of
it — so the first thing to do is switch.

### The one-step way

From the repository root:

| Platform | Command |
|---|---|
| Windows | double-click **`update.bat`**, or run it in a terminal |
| macOS / Linux | `./update.sh` |

It fetches, switches to the working branch, and fast-forwards. Then in Android
Studio: **File → Sync Project with Gradle Files**.

The script **refuses to run if you have uncommitted changes**, and tells you the
three ways out (commit / stash / discard) rather than silently stashing or
overwriting your edits. Your local config — `local.properties`, `.env`,
`keystore.properties`, `serviceAccountKey.json` — is git-ignored and is never
touched.

### From inside Android Studio

1. **Git → Fetch**
2. Bottom-right **branch selector** → `origin/claude/repo-contents-review-6e0ub3`
   → **Checkout** (only needed the first time)
3. Afterwards, just **Git → Pull** (or `Ctrl+T` / `⌘T`)
4. **File → Sync Project with Gradle Files**

### By hand

```bash
git fetch origin
git checkout claude/repo-contents-review-6e0ub3   # first time only
git pull --ff-only
```

### When a pull is not enough

| After a change to | Also run |
|---|---|
| `functions/` | `cd functions && npm install && npm run build` |
| `web-app/` | `cd web-app && npm install` |
| `gdc_desktop/requirements.txt` | `pip install -r gdc_desktop/requirements.txt` |
| `firestore.rules` / indexes | `firebase deploy --only firestore:rules,firestore:indexes` |
| Gradle files | **Sync Project with Gradle Files** in Android Studio |

If Gradle still misbehaves after a sync: **File → Invalidate Caches… →
Invalidate and Restart**.

---

## 3. Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| **Node.js** | 20 or 22 | functions, web-app, emulators, tests |
| **Java JDK** | 17 | Android build (Gradle targets 17) |
| **Android Studio** | Ladybug or newer | Android app |
| **Android SDK** | API 34 (min 23) | Android app |
| **Python** | 3.10+ | Desktop client |
| **firebase-tools** | 15+ | Emulators, deploy |

```bash
npm install -g firebase-tools
```

---

## 4. First-time setup

```bash
git clone <repo> && cd android-library-managment-system-for-gdc

# Backend
cd functions && npm install && npm run build && cd ..

# Web + director portal
cd web-app && npm install && cd ..

# Desktop
cd gdc_desktop && pip install -r requirements.txt && cd ..

# Rules tests
cd tests/firestore && npm install && cd ../..
```

### Local config files (all git-ignored — create them yourself)

**`local.properties`** (repo root — Android Studio writes `sdk.dir` for you):
```properties
sdk.dir=/path/to/Android/Sdk
GEMINI_API_KEY="your-key"
```
`GEMINI_API_KEY` is read in `app/build.gradle.kts` and becomes a
`BuildConfig` field. An empty value builds fine; the AI screens just return
nothing.

**`gdc_desktop/.env`** (copy from `.env.example`):
```properties
COLLEGE_ID=GDC-ZIAM-SHERPAO-CHARSADDA
FIREBASE_CRED_PATH=serviceAccountKey.json
FIREBASE_WEB_API_KEY=...
INACTIVITY_TIMEOUT=1800
```
`COLLEGE_ID` must exactly match a registered `institutionId`. A mismatch does
not error — the desktop just syncs into a tenant nobody is looking at.

**`web-app/.env.local`** — only if pointing at a non-default project; the
defaults in `firebase.ts` work as-is.

---

## 5. Running in VS Code

Open the **repository root** in VS Code (not a subfolder — the tests reference
paths across directories).

Recommended extensions: Python, Pylance, ESLint, Tailwind CSS IntelliSense.

### 5.1 The emulators (start these first)

Everything can run against local emulators, so you never need a real Firebase
project during development.

```bash
firebase emulators:start --only firestore,auth,functions --project demo-nexlib
```

| Emulator | Port | UI |
|---|---|---|
| Firestore | 8080 | http://127.0.0.1:4000 |
| Auth | 9099 | |
| Functions | 5001 | |

### 5.2 Web + director portal

```bash
cd web-app
npm run dev          # http://localhost:3000
```

- `http://localhost:3000/dashboard` — a college's own workspace
- `http://localhost:3000/director` — the directorate portal
- `http://localhost:3000/director/register` — onboard a college

The portal requires an account whose role is **`directorate`**. A college
`admin` will see an access-denied screen, by design — see §7.3.

### 5.3 Backend

```bash
cd functions
npm run watch                       # recompile TypeScript on save
npm run serve                       # build + emulators (functions, firestore, auth)
npm run deploy                      # deploy to the real project
```

### 5.4 Desktop client

```bash
cd gdc_desktop
python main.py
```

The repository root also has a `main.py` shim, so `python main.py` from the root
works too.

`.vscode/launch.json` is checked in, so **Run and Debug** already offers
*Desktop client (PyQt6)*, *Web + director portal*, and *Desktop sync tests*.
`.vscode/tasks.json` adds the emulators and all four test suites under
**Terminal → Run Task**.

The desktop needs `serviceAccountKey.json` to reach Firestore. Without it it
starts in mock mode: the UI works, sync does not.

### 5.5 Tests

```bash
cd tests/firestore && npm test          # security rules            67 cases
cd functions && npm test                # directorate rollup        17 cases
cd functions && npm run test:e2e        # two-institution E2E       28 cases
python3 tests/desktop/test_sync_engine.py   # offline sync          29 cases

# Kotlin static check — catches "No value passed for parameter 'x'" caused by a
# trailing lambda binding to the wrong parameter. Worth running before pushing
# Kotlin changes, since :app cannot be compiled in every environment.
python3 tools/check_kotlin_trailing_lambda.py
```

All four run against emulators with no credentials and no real project. Run the
rules tests before touching `firestore.rules` — a rules regression is invisible
from the desktop client (it uses the Admin SDK and bypasses rules) and shows up
only as "sync doesn't work" on Android and web.

---

## 6. Running in Android Studio

### 6.1 Open the project

**File → Open** → select the **repository root** (the folder containing
`settings.gradle.kts`). Do not open `app/` directly.

Android Studio will index three Gradle modules:

- `:app` — the Android application
- `:shared` — Kotlin Multiplatform, shared with the desktop module
- `:desktopApp` — the superseded Compose Desktop client (§2)

### 6.2 Configure

1. **File → Project Structure → SDK Location** — set the Android SDK path.
2. **Settings → Build → Build Tools → Gradle** — set **Gradle JDK to 17**.
   The build targets Java 17; a different JDK fails with an unhelpful
   Kotlin/JVM target mismatch.
3. Install **SDK Platform 34** in the SDK Manager.
4. First sync downloads AGP 8.13.2 and Gradle 8.13 — several minutes.

### 6.3 Run

Select the **`app`** run configuration and a device or emulator (API 23+).

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:testDebugUnitTest      # JVM unit tests
./gradlew :app:compileDebugKotlin     # compile check only
```

> **Please run `:app:compileDebugKotlin` on your first build.** The custom
> claims change in `AuthViewModel.kt` was reviewed but never compiled — the
> environment it was written in had no Android SDK.

### 6.4 Release builds

Create **`keystore.properties`** at the repo root (git-ignored):

```properties
storeFile=/absolute/path/to/nexlib-release.jks
storePassword=...
keyAlias=nexlib-key
keyPassword=...
```

```bash
./gradlew :app:assembleRelease        # APK
./gradlew :app:bundleRelease          # AAB for Play Store
```

Without those credentials the release build produces an **unsigned** APK and
warns. That is deliberate: falling back to the debug key produces an APK that
installs fine and can then never be upgraded by a properly signed one.

> The previously committed keystore must be treated as compromised. See
> DEPLOYMENT.md §0.1 before publishing anything.

### 6.5 Running the Kotlin desktop module (optional)

```bash
./gradlew :desktopApp:run
```

Superseded by the Python client. Useful only for inspecting the shared KMP code.

---

## 7. How syncing works

### 7.1 One data layout, four clients

```
/institutions/{institutionId}/
    books/{syncId}            printed catalogue — publicly readable (OPAC)
    ebooks/{syncId}           digital catalogue — college members only
    members/{syncId}          patrons
    issued_books/{syncId}     circulation
    reservations/{syncId}     holds
    settings/{docId}          fine rate, loan period
    audit_log/{entryId}       append-only trail
    sync_conflicts/{id}       both versions of a collided offline edit
    meta/profile              college name, district, contact

/users/{uid}                  role + institutionId (claims are minted from this)
/institution_registry/{id}    name, district, region, status
/directorate_summary/{id}     denormalized rollup — function-written only
/directorate_index/{id}       LEGACY self-published rollup (Spark fallback)
```

`institutionId` is never hardcoded. It is resolved at login from the account's
custom claims, and falls back to `users/{uid}` for accounts that predate claims.

### 7.2 The sync envelope

Every tenant record carries these five fields. All four clients depend on them:

| Field | Purpose |
|---|---|
| `syncId` | UUID, **and also the Firestore document id** |
| `collegeId` | owning tenant |
| `lastUpdated` | epoch millis, drives conflict ordering |
| `deleted` | soft delete — records are never hard-deleted |
| `syncStatus` | `synced` / `pending` |

**`syncId` as the document id is what makes a retry idempotent.** Pushing the
same record twice overwrites; it never creates a duplicate. Without that, an
unreliable connection silently multiplies records.

**Deletes are soft, always.** A hard delete would be resurrected by the next
offline peer that syncs, because that peer has no way to know the record was
meant to be gone.

### 7.3 Roles

Minted as custom claims and enforced by `firestore.rules`:

| Role | Scope |
|---|---|
| `directorate` | **No institution.** Reads aggregates across all colleges. |
| `admin` | Full CRUD in its own college, plus settings. |
| `librarian` / `staff` | Full CRUD in its own college. |
| `student` | Own patron record and own loans. Public catalogue. **No writes.** |

> A college's **`director` is that college's administrator**, not a directorate
> official. They are separate roles. Anyone needing province-wide oversight must
> be `directorate` — see DEPLOYMENT.md §2.

An unrecognised or missing role falls to the **least** privilege, never to staff.

### 7.4 The write path (identical on desktop and mobile)

```
librarian issues a book
        │
        ▼
1. write to local SQLite/SQLDelight  ──┐  one transaction
2. insert a row into sync_queue      ──┘
        │
        ▼
3. UI repaints from the LOCAL database        ← never waits on the network
        │
        ▼
   ── background worker thread ────────────────────────────
        │
4. read the server's current lastUpdated for this document
        │
        ├── unchanged since we last saw it → batch write, clear the queue row
        │
        └── server moved on → CONFLICT
              write both versions to sync_conflicts
              flag the record for review
              never overwrite
        │
5. on failure → retryCount++, back off 5s → 15s → 60s → 5min
                the row is never dropped
```

Four rules this enforces, each because breaking it loses work invisibly:

1. **The UI thread never touches Firestore.** A single network call from the UI
   thread freezes the app for as long as the network takes — minutes, on a bad
   rural link.
2. **The local write always succeeds first.** Issue/return/add never blocks on
   connectivity.
3. **Conflicts are never resolved by overwriting.** The comparison is against
   the version *this device last observed*, not the two records' own
   timestamps — device clocks in the field are routinely wrong, and comparing
   them directly discards whichever librarian had the slower clock.
4. **A failed push is never dropped.** It waits, with a growing backoff, rather
   than hammering a dead connection.

### 7.5 The read path

A real-time Firestore listener, scoped to the logged-in institution only, writes
incoming changes into the local database. Snapshots are **debounced by 300ms**,
so a burst of twenty incoming records triggers one repaint rather than twenty —
that per-snapshot repaint is the usual reason a desktop app feels slow under
sync load.

Local SQLite remains the read source for everything the UI renders. Firestore is
the sync target, not the rendering source. That is what keeps the client
responsive regardless of network quality.

### 7.6 The directorate rollup

The dashboard **never** queries institutions live. At three colleges that merely
feels slow; at three hundred it is thousands of document reads per viewer per
page load.

```
every 6 hours (or "Recompute figures")
        │
        ▼
walk institution_registry where status == "active"    ← paginated, 50 at a time
        │
        ▼
for each college: COUNT aggregations                  ← counts index entries,
  books · ebooks · members · issued · overdue           not documents
        │
        ▼
write ONE document to /directorate_summary/{id}
        │
        ▼
the portal reads only that collection
```

A COUNT is billed per 1000 index entries instead of per document, so a
50,000-book college costs a handful of reads to summarise rather than 50,000.

**Fallback chain**, shown in the portal's `source` column:

| Source | Meaning |
|---|---|
| `summary` | The server rollup ran. Trustworthy. |
| `self-reported` | Functions are not deployed (Spark). Each college published its own figures. |
| `registry-only` | Nothing has reported yet; names only. |

Clients cannot write `directorate_summary` or `institution_registry`. A college
that could author its own summary row could report whatever it liked; one that
could set its own registry status would defeat the approval gate.

---

## 8. Deployment in brief

Full runbook: **[DEPLOYMENT.md](DEPLOYMENT.md)**.

| Surface | Build | Goes to |
|---|---|---|
| Android | `./gradlew :app:bundleRelease` | APK link now, Play Store later |
| Desktop | `python -m PyInstaller --clean gdc_desktop.spec` | `dist/NEXLIB/` folder, zipped |
| Web | `next build` | Vercel, root directory `web-app`, region `bom1` |
| Director | same build | Vercel — same app, `/director` |
| Backend | `firebase deploy --only functions` | asia-south1 |
| Rules | `firebase deploy --only firestore:rules,firestore:indexes` | |

### Onboarding a college

1. A `directorate` account opens **/director → Onboard College**.
2. Submit name, district, and the administrator's name and email.
3. **Record the temporary password** — it is shown once and stored nowhere.
4. Approve from the network overview. Approval summarises the college
   immediately rather than leaving it invisible for six hours.
5. Give the college its `institutionId` (desktop `.env`) and its invite code
   (Android/desktop join flow).

A **pending** college can sign in and run its library. It just does not reach
the directorate.

### Three things to settle before a real rollout

1. **The signing key is compromised** — it was committed with its password and
   is still in git history. Rotate and purge.
2. **The desktop ships an Admin SDK credential that bypasses the rules.** At
   300 colleges every front desk holds a key to every other college's patron
   records. The fix is routing it through the ID token it already obtains.
3. **Cloud Functions need the Blaze plan.** On Spark the system still runs, but
   registration, claims and the rollup do not exist and the dashboard falls back
   to self-reported figures.

Each is explained in DEPLOYMENT.md §0.

---

## 9. Troubleshooting

| Symptom | Cause |
|---|---|
| Android and web can't write, desktop can | A rules regression. The desktop uses the Admin SDK and bypasses rules. Run the rules tests. |
| Director portal loads then every query fails | The account is `director`/`admin`, not `directorate`. |
| Dashboard shows a college with zero counts | It never synced, or its `COLLEGE_ID` does not match a registered `institutionId`. |
| Counts look suspiciously low | Indexes still building, or records predating the sync envelope — check **Unsynced Records** in the drill-down. |
| Desktop starts but never syncs | Missing `serviceAccountKey.json`, so it is in mock mode. |
| Records duplicating on reconnect | A write path not using `syncId` as the document id. |
| Kotlin/JVM target mismatch in Android Studio | Gradle JDK is not 17. |
