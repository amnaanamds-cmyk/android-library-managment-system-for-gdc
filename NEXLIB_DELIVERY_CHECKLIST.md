# NEXLIB — Delivery Checklist

Autonomous completion report for the NEXLIB Library Management System
(Compose Desktop + Android via KMP `shared`, Firestore real-time sync,
multi-college Director Network, Vercel web dashboard).

## Task 1 — Real-time Sync Engine ✅

- [x] `shared/.../data/SyncStatus.kt` — `Synced / Syncing / Offline / Error` sealed interface.
- [x] Models extended with `collegeId` + `syncStatus` (`Book`, `Member`, `IssuedBook`, `Reservation`).
- [x] `FirestoreService.kt` rewritten: realtime `observe*` streams (`snapshots()`), incremental
      `fetch*(since)` queries, server-timestamp uploads (`set(..., merge=true)` + `update(...)`),
      ebooks isolated in `institutions/{collegeId}/ebooks`.
- [x] `SyncService.kt` — interface + `SyncServiceImpl`: 5 realtime collectors with `retryWhen` +
      5s backoff, LWW apply via `find*BySyncId` + `ConflictResolver`, mutex-guarded push,
      periodic full sync, `StateFlow<SyncStatus>`.
- [x] `ConflictResolver.kt` rewritten (LWW) for all 4 entity types.
- [x] `get*BySyncId` queries added to `BookQueries.sq`, `MemberQueries.sq`,
      `IssuedBookQueries.sq`, `ReservationQueries.sq`; implemented in `DatabaseService` /
      `DatabaseServiceImpl`.
- [x] `SyncStatusBadge` component wired into `MainLayout` header (green/amber/red dot).
- [x] Realtime sync starts on login; manual "Sync Now" button + 120s periodic full sync.

## Task 2 — Multi-College Director Dashboard ✅

- [x] Models: `College`, `Director`, `UserProfile`, `CollegeLink`.
- [x] `CollegeQrHelper` (JSON QR payload) + `QrImageGenerator` (ZXing → Compose `ImageBitmap`).
- [x] `CollegeService`: create college (auto id + 6-digit invite code), find by invite code,
      `assignDirector` (`arrayUnion`), realtime `observeColleges` / `observeCollege` /
      `observeCollegeBooks`.
- [x] `FirebaseSyncClient` rewritten: real auth + `registerCollege` / `joinCollege`,
      `users/{uid}` profile resolution, offline demo fallback (director/admin credentials).
- [x] `DirectorDashboardScreen`: college cards with live counters, Create / Join dialogs,
      QR link dialog, live catalog drill-down; offline DEMO card fallback on desktop.
- [x] Navigation: new `Screen.DIRECTOR` (Director role only) in `MainLayout` + `Main.kt`.
- [x] `FirebaseAvailability` expect/actual probe (JVM = offline demo; Android checks
      `FirebaseApp.getInstance()`); added direct `dev.gitlive:firebase-app` dependency;
      desktop `libs.zxing.core` + `compose.materialIconsExtended`.

## Task 3 — Vercel Web-App Prep (gdc web app) ✅

- [x] `.env.example` with `VITE_FIREBASE_*` placeholders + `VITE_FIREBASE_COLLEGE_ID`.
- [x] `vercel.json` — SPA rewrite to `index.html`, build/output config.
- [x] `src/lib/firebase.js` — optional lazy Firebase init (graceful demo fallback).
- [x] `src/lib/catalog.js` — realtime `onSnapshot` books stream with unsubscribe + field mapping.
- [x] `App.jsx` wired to merge the live Firestore catalog with the demo dataset.
- [x] `firebase` dependency added; `npm install` + `npm run build` pass (Vite 8 / rolldown).
- [x] Fixed pre-existing truncated file (`NewIssueForm` was missing its closing braces).
- [x] `README.md` with deploy-on-Vercel instructions.

## Task 4 — Production Firestore Security Rules ✅

- [x] `firestore.rules` covering `users`, `directors`, `colleges`, `institutions/{collegeId}/`
      `books` (public read, staff write), `ebooks`/`members`/`issued_books`/`reservations`
      (college-members read, staff write), `settings` + `audit` (director-only).
- [x] `firestore.indexes.json` (composite indexes for incremental sync queries).
- [x] `firebase.json` referencing rules + indexes for `firebase deploy`.

## Build Verification ✅

- [x] `:shared:compileKotlinJvm` — BUILD SUCCESSFUL
- [x] `:desktopApp:compileKotlinJvm` — BUILD SUCCESSFUL
- [x] `:shared:compileDebugKotlinAndroid` — BUILD SUCCESSFUL
- [x] Desktop app launches (verified via `:desktopApp:run`)
- [x] Web `npm run build` — built successfully

## Notes

- Desktop build has no `google-services.json` → runs fully in offline demo mode
  (sync badge shows Offline, director dashboard renders a DEMO card seeded from the
  local SQLDelight DB). Android + web use the real Firestore project when configured.
- Firestore schema: `institutions/{collegeId}/{books,ebooks,members,issued_books,reservations}`,
  `colleges/{collegeId}`, `directors/{uid}`, `users/{uid}`.
