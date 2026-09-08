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
| **[RUNNING.md](RUNNING.md)** | Repository structure, running everything from Android Studio and VS Code, and how syncing works. **Start here.** |
| **[DEPLOYMENT.md](DEPLOYMENT.md)** | Production runbook: Firebase setup, signing, Vercel, onboarding a college, and the items that block a real rollout. |
| **[SYNC_ARCHITECTURE.md](SYNC_ARCHITECTURE.md)** | Canonical Firestore layout and the sync envelope. |

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

## Tests

```bash
cd tests/firestore && npm test              # security rules       67
cd functions && npm test                    # directorate rollup   17
cd functions && npm run test:e2e            # two-institution E2E  28
python3 tests/desktop/test_sync_engine.py   # offline sync         29
```

All four run against the Firebase emulators — no real project, no credentials.
