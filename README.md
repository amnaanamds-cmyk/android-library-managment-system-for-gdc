# NEXLIB — Library Management System

A multi-tenant library management system for the Government Degree Colleges of
Khyber Pakhtunkhwa. One Firebase backend, four client surfaces, offline-first
on every one of them.

| Surface | Stack | Scope |
|---|---|---|
| Mobile | Kotlin, Jetpack Compose, SQLDelight | Own institution |
| Desktop | Python 3.10+, PyQt6, SQLite | Own institution |
| Web | Next.js 16, React 19 | Own institution |
| Director portal | Next.js 16 (`/director`) | Read-only, all institutions |
| Backend | Cloud Functions (TypeScript) | Registration, auth claims, rollup |

Originally built for Government Degree College Ziam Sherpao; the data model,
security rules and directorate rollup are multi-tenant so additional colleges
are onboarded rather than forked.

## Documentation

| Document | What it covers |
|---|---|
| **[SETUP.md](SETUP.md)** | Setting the system up from scratch, in order: tools, clone, run each app, Firebase, first directorate account, onboarding a college. **Start here.** |
| **[RUNNING.md](RUNNING.md)** | Repository structure, running everything day to day, and how syncing works. |
| **[DIRECTORATE.md](DIRECTORATE.md)** | Running the province-wide director portal: the two things that usually stop it, a fully local route needing no Firebase project, and what works on the free plan. |
| **[DEPLOYMENT.md](DEPLOYMENT.md)** | Production runbook: Firebase setup, signing, Vercel, onboarding a college, and the items that block a real rollout. |
| **[SYNC_ARCHITECTURE.md](SYNC_ARCHITECTURE.md)** | Canonical Firestore layout and the sync envelope. |

## Getting the code

All current work is on the branch **`claude/repo-contents-review-6e0ub3`**, not
`main`. After cloning, switch to it — `update.bat` (Windows) or `./update.sh`
does it in one step, and is also how you pull later changes. See
[RUNNING.md §2a](RUNNING.md).

## Quick start

```bash
cd functions  && npm install && npm run build && cd ..
cd web-app    && npm install && cd ..
cd gdc_desktop && pip install -r requirements.txt && cd ..

firebase emulators:start --only firestore,auth,functions --project demo-nexlib
cd web-app && npm run dev          # http://localhost:3000
```

Android: open the repository root in Android Studio, set the Gradle JDK to 17,
and run the `app` configuration.

## Running it on Windows

Double-click one of these at the repository root:

| File | What it does |
|---|---|
| `run.bat` | menu for everything below |
| `run-android.bat` | build, install and launch the Android app |
| `run-desktop.bat` | the Python/PyQt6 desktop client |
| `run-web.bat` | web dashboard + director portal |
| `update.bat` | pull the latest changes |

## Tests

```bash
cd tests/firestore && npm test              # security rules       67
cd functions && npm test                    # directorate rollup   17
cd functions && npm run test:e2e            # two-institution E2E  28
python3 tests/desktop/test_sync_engine.py   # offline sync         29
```

All four run against the Firebase emulators — no real project, no credentials.
