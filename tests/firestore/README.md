# Firestore rules tests

Behavioural tests for `firestore.rules`, run against the Firestore emulator.

```bash
cd tests/firestore
npm install
npm test
```

These exist because the rules are enforced for **Android and Web only** — the
desktop app uses `firebase-admin`, which bypasses them. A rules regression is
therefore invisible from the desktop app and shows up only as "sync doesn't
work" on the other two platforms. Run these before deploying rules.

To check a proposed change against the current behaviour, point the test at any
rules file:

```bash
firebase emulators:exec --only firestore --project demo-nexlib \
  "node rules.test.mjs /path/to/other.rules"
```

## Scale-testing the directorate MIS (300 colleges)

`seed-load-test.mjs` fills a **local** Firestore + Auth emulator pair with a
synthetic 300-college network — never the real `nexlib-e7970` project, so
this costs nothing and cannot touch real data. It seeds realistic irregular
data (stale colleges, zero-book colleges, missing contact info, a duplicate
institution ID, three staff accounts at three tiers) so the portal's actual
features are exercised, not just its ability to render 300 identical rows.

```bash
# Terminal 1 — from the repo root, leave running
firebase emulators:start --only firestore,auth --project nexlib-e7970

# Terminal 2
cd tests/firestore
npm install
npm run seed:load

# Terminal 3 — from directorate-app/, point the client at the emulator
cd ../../directorate-app
NEXT_PUBLIC_USE_EMULATOR=true npm run dev
```

Then open http://localhost:3001/login and sign in as one of the accounts the
seed script prints (all use password `LoadTest123!`):

| Account | Tier | What it proves |
|---|---|---|
| `director@nexlib.com` | super_admin (no staff record) | the bootstrap default still works exactly as it does for the real director account |
| `regional@nexlib.com` | regional | can approve/write, cannot manage staff |
| `analyst@nexlib.com` | analyst | every write control is hidden/disabled |
| `owner@loadtest.edu` | ordinary college account | is bounced straight back to `/login`, never sees the portal |

`NEXT_PUBLIC_USE_EMULATOR` defaults to unset/false — a normal `npm run dev`
still talks to the real project. Never set it in a deployed build.
