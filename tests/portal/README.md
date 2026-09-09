# Portal end-to-end tests

Drives the **director portal** in a real browser against the Firebase
emulators. No production project, no credentials, no network.

This is the only test that proves the console works rather than merely
compiles: that the status and reporting filters filter, that a pending college
is listed in the registry table and not just queued for approval, and that a
row links through to that college's detail page.

## Run it

Four terminals, or four background jobs:

```bash
# 1. Emulators (auth + firestore, ports from firebase.json)
firebase emulators:start --only auth,firestore

# 2. Seed a directorate officer and eight colleges
node tests/portal/seed.mjs

# 3. The web app, pointed at the emulators
cd web-app
NEXT_PUBLIC_FIREBASE_EMULATOR=1 npm run build
NEXT_PUBLIC_FIREBASE_EMULATOR=1 npx next start -p 3111

# 4. The test
npm --prefix tests/portal install      # first time only
node tests/portal/directorate.test.mjs
```

Expected: `13 passed, 0 failed`.

## What the seed creates

| Login | Password |
|---|---|
| `directorate.officer@hed.gkp.pk` | `Passw0rd!` |

Eight colleges chosen to cover every state the portal has to render:

| College | Why it is there |
|---|---|
| Peshawar, Mardan, Swat, Charsadda | ordinary active colleges, recently synced |
| Kohat | synced 4 days ago → **stale**, and has 214 records with no sync envelope |
| Dera Ismail Khan | **suspended** |
| Bannu | approved but has **never** synced |
| Chitral | **pending** approval, so it has no summary row |

## Environment

- `BASE_URL` — where the web app is running (default `http://localhost:3111`)
- `EMULATOR_HOST` — emulator host for the seed (default `127.0.0.1`)
- `CHROMIUM_PATH` — an existing Chromium, instead of Playwright's download
- `SCREENSHOT_DIR` — if set, saves a capture of the college detail page
