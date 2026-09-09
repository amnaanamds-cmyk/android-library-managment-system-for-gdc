# NEXLIB — Library Management System

A multi-college library management system built for Government Degree
Colleges under the Higher Education Department, Khyber Pakhtunkhwa. Each
college runs its own catalog, circulation, and membership records, synced in
real time through Firebase, with a directorate dashboard that aggregates
live stats across every onboarded college.

## Apps in this repo

| Platform | Path | Stack |
|---|---|---|
| Mobile | `app/` | Android, Kotlin, shares sync engine with `shared/` |
| Desktop | `gdc_desktop/` | Python, PyQt6, Firebase Admin SDK |
| Web | `web-app/` | Next.js, Firebase Web SDK |
| Shared sync engine | `shared/` | Kotlin Multiplatform (used by `app/`) |

Firestore rules and indexes live at the repo root (`firestore.rules`,
`firestore.indexes.json`) and are shared by all three apps against a single
Firebase project (`nexlib-e7970`).

## Getting started

- **Deploying today / setting up sync for the first time?** Follow
  [`RUNBOOK.md`](./RUNBOOK.md) — it's the exact step-by-step sequence:
  credentials, rules deploy, rebuilds, onboarding colleges, and turning on
  the directorate dashboard.
- **Want to understand how sync and multi-tenancy actually work?** Read
  [`SYNC_ARCHITECTURE.md`](./SYNC_ARCHITECTURE.md).
