# NEXLIB — Go-Live Runbook

This is the exact sequence to get real-time sync working across Android,
`gdc_desktop`, and the web app, and to switch on the directorate dashboard for
10+ colleges. The code for all of this already exists and is wired correctly
— what's been missing is deployment/configuration, not features. Follow this
in order; each step says how to verify it before moving on.

Background on *why* things looked broken, and the full architecture, is in
`SYNC_ARCHITECTURE.md`. Two dead, half-built app copies (`desktopApp/` Kotlin
and `web/` Next.js — neither could ever reach Firebase) have been removed
from this repo so there's only one real app per platform now:

- **Desktop**: `gdc_desktop/` (Python/PyQt6)
- **Web**: `web-app/` (Next.js)
- **Mobile**: `app/` (Android/Kotlin, using the `shared/` KMP module)

There is also a `gdc_desktop/directorate_server/` + `gdc_desktop/directorate_dashboard/`
folder — a separate, older prototype with its own FastAPI server and SQLite
DB, unrelated to Firestore. **Ignore it and don't run `start_directorate_server.bat`.**
It is not part of the sync system described below and will only cause
confusion if started. (Worth deleting later — ask before removing since it
wasn't in today's cleanup scope.)

---

## 1. Get your Firebase credentials

You need two things from the [Firebase Console](https://console.firebase.google.com/u/0/project/nexlib-e7970):

1. **Service account key** (for `gdc_desktop`, which uses the Admin SDK):
   Project Settings → Service Accounts → *Generate new private key*. Save the
   downloaded JSON as `gdc_desktop/serviceAccountKey.json` (already gitignored
   — never commit it).
2. **Web API key** (for `gdc_desktop`'s login, which uses the Auth REST API —
   this is different from the service account): Project Settings → General →
   *Web API Key*. It's also visible at the URL you already had open
   (`.../settings/general`).

Copy `gdc_desktop/.env.example` to `gdc_desktop/.env` and fill in:

```
FIREBASE_CRED_PATH=serviceAccountKey.json
FIREBASE_WEB_API_KEY=<the web API key from step 2>
FIREBASE_STORAGE_BUCKET=nexlib-e7970.firebasestorage.app
COLLEGE_ID=<leave as-is — set automatically on first login, see step 6>
```

**Verify:** launch `gdc_desktop` (`python main.py` from the repo root, or your
packaged `.exe`) and try to log in. If you get "Firebase not configured..."
the `.env` or key file path is still wrong — double check `FIREBASE_CRED_PATH`
matches where you actually saved the JSON file.

The Android app (`app/google-services.json`) and web app
(`web-app/src/lib/firebase.ts`) already have real, matching config for this
same project committed in the repo — nothing to do there.

## 2. Deploy Firestore security rules

This project has no Cloud Functions (Spark/free plan), so `firestore.rules`
is the only thing standing between "Android/web can sync" and "permission
denied on every read." It is **not deployed automatically** — you must push
it explicitly. A `.firebaserc` has been added so this now just works:

```bash
npm install -g firebase-tools   # if you don't have it
firebase login
firebase deploy --only firestore:rules,firestore:indexes
```

**Verify:** in the Firebase Console → Firestore → Rules tab, confirm the
"last deployed" timestamp is recent (today).

Optional but recommended before deploying, if you have Node + Java available:
run the existing rules test suite against the emulator:

```bash
cd tests/firestore && npm install && npm test
```

## 3. Rebuild everything from current source

Whatever APK is on the test phone and whatever web build is live almost
certainly predates the sync fixes already in this repo. Stale builds are the
single most likely reason things "don't sync" even though the code is
correct.

- **Android**: `./gradlew :app:assembleRelease` (or `assembleDebug` for
  testing), then reinstall on the device — don't reuse the old APK.
- **Web**: `cd web-app && npm install && npm run build`, then deploy the
  output to wherever you're hosting it (Vercel is the path of least
  resistance for Next.js — `vercel --prod` from `web-app/` after `vercel
  login`; any other Node host works too since there's no special config
  required). For a quick local/LAN check first: `npm run start` and open
  `http://localhost:3000`.
- **Desktop**: already covered in step 1 — just make sure you're running
  `gdc_desktop`, not the deleted `desktopApp`.

## 4. Prove sync actually works, end to end

Before onboarding real colleges, do one manual round-trip:

1. Log into `gdc_desktop` as a college user, add one test book.
2. Open the web app (`web-app`, logged in as the same college), go to
   Dashboard → Books. The test book should appear within a couple of
   seconds without refreshing.
3. Open the Android app on the phone, logged into the same college. The
   green "sync active" indicator should show (not the red "Offline" dot from
   your screenshot), and the test book should be there too.

If Android still shows Offline after a fresh install + rules deploy, check
that the phone actually has network access and that you're logged in with an
account that has a `users/{uid}` document with `institutionId` set (see
step 6 — a brand-new sign-up with no institution yet is a different,
expected state, not a bug).

## 5. Create your directorate admin account

Nobody can self-promote to directorate level (enforced by `firestore.rules`
on purpose). Sign up normally first (in `gdc_desktop` or the web app) with
whatever account should have directorate-wide access, then run:

```bash
cd gdc_desktop
python scripts/manage_directorate.py promote director@yourdomain.com
```

This flips that account's Firestore role to `directorate_admin`. Sign out
and back in (or restart the app) so it picks up the new role.

**Verify:** in the web app, visit `/director` — you should land on the
directorate dashboard instead of the "Directorate access required" screen.
In `gdc_desktop`, the Directorate Dashboard screen becomes available in the
sidebar for that account.

## 6. Onboard your 10+ colleges

You don't need to hand-create Firestore documents per college — `gdc_desktop`
already has a self-serve onboarding flow for this. For each college:

1. Install `gdc_desktop` on that college's machine, with the *same*
   `serviceAccountKey.json` / `.env` (all colleges share the one Firebase
   project — they're separated by `institutionId`, not by separate Firebase
   projects).
2. Have their librarian/admin sign up (Firebase Auth) and log in.
3. On first login with no institution yet, the app shows **"Create New
   Institution"** — they enter a college name and a unique ID (e.g.
   `GDC-MARDAN-01`). This automatically:
   - Creates `institutions/{collegeId}`
   - Sets that user's role to `owner` for their college
   - Registers the college in `directorate_index`, so it shows up in the
     directorate dashboard immediately (with zeroed stats until their first
     real sync)
4. Additional staff at that same college use **"Join Existing Institution"**
   with the same College Unique ID.

Repeat for all 10+ colleges — no code changes or manual Firestore edits are
needed per college, just repeating this signup flow.

**Verify:** run `python scripts/manage_directorate.py list-colleges` from
`gdc_desktop/` — every onboarded college should be listed with a name and
(after their first real sync) non-zero book/member counts. The same list
appears live in the web app's `/director` dashboard and in `gdc_desktop`'s
own Directorate Dashboard screen.

## 7. What "directorate sees other colleges" actually looks like

Once steps 5–6 are done:

- **Web**: directorate admin signs in → `/director` shows every college's
  live book/member/loan/overdue counts and a staleness warning if a college
  hasn't synced recently, with drill-down per college
  (`/director/[collegeId]`) and CSV export.
- **Desktop**: same data, in `gdc_desktop`'s Directorate Dashboard screen,
  for anyone logged in with `directorate_admin` (or the legacy `director`
  role).
- Directorate accounts only ever see the aggregate counts published to
  `directorate_index` — never a college's actual book/patron records. That
  isolation is intentional (see `SYNC_ARCHITECTURE.md`), not a limitation to
  work around.

---

## Quick troubleshooting reference

| Symptom | Likely cause | Fix |
|---|---|---|
| Desktop: "Firebase not configured..." | Missing/misnamed `serviceAccountKey.json` or `.env` | Step 1 |
| Android: red "Offline" dot | Stale APK, or rules not deployed, or account has no `institutionId` yet | Steps 2–3, or complete onboarding |
| Web: sync badge looks fake/stuck | Old deployed build predating the real `useSyncHealth` fix | Step 3 |
| `/director` shows "access required" | Account isn't `directorate_admin`/`director` yet | Step 5 |
| Directorate dashboard is empty | No colleges onboarded yet, or they haven't synced once | Step 6 |
| A college's counts look stuck/old | That college's app hasn't run/synced recently — check their machine's network | n/a (expected, not a bug) |
