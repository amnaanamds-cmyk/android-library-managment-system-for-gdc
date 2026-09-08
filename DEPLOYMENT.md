# NEXLIB — Deployment Runbook (KPK province-wide)

Four surfaces, one Firebase project:

| Surface | Stack | Access | Deployed to |
|---|---|---|---|
| Mobile | Kotlin, Compose, SQLDelight | Full CRUD, own institution | Signed APK/AAB |
| Desktop | Python 3.10+, PyQt6, SQLite | Full CRUD, own institution | PyInstaller folder build |
| Web | Next.js 16, React 19 | Full CRUD, own institution | Vercel |
| Director portal | Next.js 16 (`/director`) | Read-only, cross-institution | Vercel |

> **All four must point at the same Firebase project.** A mix of dev and prod
> configs fails silently — each app works perfectly on its own and simply never
> sees the others' data.

---

## 0. Read this before deploying anything

Three items are **blocking** for a real rollout. None of them stop a demo, and
all three get materially worse the more colleges are onboarded.

### 0.1 The release signing key is compromised

`nexlib-release.jks` was committed to this repository, and its password was in
plaintext in `app/build.gradle.kts`. Both are still in git history.

Anyone with repository access — past or present — can sign an APK that Android
will accept as an update to the real app.

**Required before publishing anywhere:**

1. Generate a new keystore, and keep it out of the repository:
   ```bash
   keytool -genkeypair -v -keystore nexlib-release-v2.jks \
     -keyalg RSA -keysize 4096 -validity 10000 -alias nexlib-key
   ```
2. Purge the old key from history (`git filter-repo --path nexlib-release.jks
   --invert-paths`), then force-push and have every clone re-cloned.
3. Rotate the Gemini API key and any Firebase credential that has been through
   this repository.

If the app has **already** been distributed with the old key, you cannot simply
swap keys — Android will refuse the update. Either continue with the compromised
key (and accept the risk), or ship a new `applicationId` and migrate users.

### 0.2 The desktop client ships an Admin SDK credential

`gdc_desktop` talks to Firestore through `firebase-admin`, which **bypasses
`firestore.rules` completely**. That is why a rules bug used to look like "the
desktop works, the others don't".

At one college this is merely untidy. At three hundred it means every college's
front desk holds a credential that can read and write *every other college's*
patron records — extractable from the machine by anyone who can copy a file.
Tenant isolation is enforced by rules the desktop does not go through.

Options, in order of preference:

1. **Move the desktop to the client SDK path.** It already signs in over the
   Auth REST API (`FIREBASE_WEB_API_KEY`) and holds an ID token; routing
   Firestore access through that token puts the desktop under the same rules as
   Android and web. This is the correct fix and is *not* done in this change —
   it is a rewrite of `firebase_service.py`'s data layer.
2. **Per-college service accounts**, each restricted to its own tenant path.
   Reduces the blast radius to one college.
3. **Interim:** treat every desktop install as trusted infrastructure — full
   disk encryption, no shared logins, and rotate the key if a machine is lost.

Do not ship one shared `serviceAccountKey.json` to many colleges and consider
the tenant boundary enforced.

### 0.3 Cloud Functions require the Blaze plan

The scheduled rollup, institution registration and claim minting are all Cloud
Functions. **Firebase Spark cannot run them.**

Until the project is on Blaze:

- Registration and approval are unavailable; institutions must be created by
  hand.
- Claims are never minted, so every client falls back to reading
  `users/{uid}` — which still works, because the rules keep that fallback.
- The director dashboard falls back to `directorate_index`, the figures each
  college publishes about itself, and labels every row **self-reported**.

The system runs on Spark. It just runs with a weaker directorate story. Section
8 of the architecture spec already flags Blaze as required before the pilot
grows.

---

## 1. Firebase project setup

```bash
npm install -g firebase-tools
firebase login
firebase use --add            # select the production project
```

Firestore must be in **`asia-south1`** (Mumbai), the closest region to KPK, and
it must match `REGION` in `functions/src/config.ts`. The database location
cannot be changed after creation — get this right the first time.

Enable **Email/Password** under Authentication → Sign-in method.

### Deploy rules and indexes

```bash
firebase deploy --only firestore:rules,firestore:indexes
```

Index builds take minutes to hours on a populated database. Until they finish,
the queries behind them fail — the rollup degrades those counts to zero and
logs a warning rather than aborting, so a partially-built index shows as
suspiciously low numbers, not an error.

### Deploy functions (Blaze only)

```bash
cd functions && npm ci && npm run build && cd ..
firebase deploy --only functions
```

---

## 2. Bootstrapping the first directorate account

**There is a chicken-and-egg problem here, and it has to be solved by hand.**

`setUserRole` requires a caller who is already `directorate`. Registration only
ever mints college `admin`. So the first directorate account cannot be created
through the app at all.

Once, from a trusted machine holding the service account key:

```bash
# scripts/bootstrap-directorate.mjs
node --input-type=module -e '
import { initializeApp, cert } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";

const EMAIL = "directorate.officer@hed.gkp.pk";   // change me

initializeApp({ credential: cert(process.env.GOOGLE_APPLICATION_CREDENTIALS) });
const user = await getAuth().getUserByEmail(EMAIL);
await getAuth().setCustomUserClaims(user.uid, { role: "directorate", institutionId: null });
await getFirestore().doc(`users/${user.uid}`).set(
  { uid: user.uid, email: EMAIL, role: "directorate", institutionId: "" },
  { merge: true },
);
console.log("Directorate role granted to", user.uid);
'
```

Create the account in the Firebase console first, then run this against it.
Afterwards that account can promote others from the app via `setUserRole`.

### Migrating existing directorate staff

The portal is now `directorate`-only. It previously also admitted `director`,
`owner`, `admin` and `college_admin` — every college administrator in the
province could open the network-wide dashboard.

A college's **`director` is that college's own administrator**, not a
directorate official. Anyone who genuinely needs province-wide oversight must be
moved to `directorate`:

```js
await httpsCallable(functions, "setUserRole")({ uid: "<uid>", role: "directorate" });
```

Leaving them as `director` no longer half-works: the rules draw the same line,
so the portal would load and then fail every query.

---

## 3. Web and director portal (Vercel)

Both surfaces are the same Next.js app. The college workspace is `/dashboard`;
the directorate portal is `/director`.

```
Root directory:    web-app
Framework:         Next.js
Install command:   npm ci
Build command:     next build
Region:            bom1  (Mumbai — matches Firestore)
```

Set the `NEXT_PUBLIC_FIREBASE_*` variables from `web-app/.env.example`.

**On a "separate project/URL" for the directorate:** the spec asks for one. It
is fine to create a second Vercel project from the same repository with the same
settings and point a different domain at it — but be clear that the URL is not
the access boundary. The boundary is the `directorate` claim plus
`firestore.rules`, and it holds identically on both URLs. A separate URL buys
operational tidiness, not security; skipping it costs nothing.

---

## 4. Desktop (PyInstaller)

```bash
cd gdc_desktop
pip install -r requirements.txt pyinstaller
python -m PyInstaller --noconfirm --clean gdc_desktop.spec
# -> dist/NEXLIB/NEXLIB.exe
```

`build_exe.bat` runs exactly this. It referenced `gdc_desktop.spec` before that
file existed, so the desktop had no repeatable build; the spec is now in the
repository.

**Per-installation, delivered separately from the binary — never bundled:**

- `serviceAccountKey.json` (see §0.2)
- `.env`, from `gdc_desktop/.env.example`, with at minimum:
  ```
  COLLEGE_ID=GDC-ZIAM-SHERPAO-CHARSADDA   # the institutionId from registration
  FIREBASE_WEB_API_KEY=...
  FIREBASE_CRED_PATH=serviceAccountKey.json
  INACTIVITY_TIMEOUT=1800
  ```

`COLLEGE_ID` must match the registered `institutionId` exactly. A mismatch does
not error — the desktop simply syncs into a tenant nobody is looking at.

---

## 5. Android

```bash
# keystore.properties at the repo root — git-ignored, never committed
storeFile=/absolute/path/to/nexlib-release-v2.jks
storePassword=...
keyAlias=nexlib-key
keyPassword=...
```

or the equivalent `NEXLIB_STORE_FILE` / `NEXLIB_STORE_PASSWORD` /
`NEXLIB_KEY_ALIAS` / `NEXLIB_KEY_PASSWORD` environment variables for CI.

```bash
./gradlew :app:assembleRelease      # APK for direct distribution
./gradlew :app:bundleRelease        # AAB for Play Store
```

With no credentials present the release build produces an **unsigned** APK and
warns. That is deliberate: falling back to the debug key produces an APK that
installs fine and then can never be upgraded by a properly signed one.

`app/google-services.json` must belong to the same project as every other
surface.

---

## 6. Onboarding a college

1. A `directorate` account opens **/director → Onboard College**.
2. Fill in college name, district (from the KPK list — free text fragments
   district rollups), and the administrator's name and email.
3. Submit. This creates, in one batch: the institution, its `meta/profile`, the
   admin auth account with `{ role: "admin", institutionId }` claims, and the
   registry entry with `status: "pending"`.
4. **Record the temporary password.** It is shown once and stored nowhere. If it
   is lost, use a Firebase password reset — do not re-register the college.
5. Approve it from the network overview. Approval flips `status` to `active` and
   immediately summarises the college, so it appears without waiting for the
   schedule.
6. Give the college its `institutionId` for the desktop `.env`, and its invite
   code for the Android/desktop join flow.

A **pending** college can sign in and run its library. It just does not reach
the directorate. That gate is the control point that keeps growth deliberate.

---

## 7. End-to-end verification

Run this against a staging project before the first real rollout.

### 7.1 Automated

```bash
# Firestore rules — 67 cases: tenant isolation, claims, student least
# privilege, the approval gate, profile escalation
cd tests/firestore && npm ci && npm test

# Directorate rollup — 17 cases against the Firestore emulator
cd functions && npm ci && npm test

# End-to-end, section 9.7 — 28 cases: two institutions registered, a loan
# recorded, another client reading it, the rollup, and tenant isolation
npm run test:e2e        # (also: npm run test:all)

# Desktop offline sync — 29 cases, including the four scenarios in spec 5.1
python3 tests/desktop/test_sync_engine.py
```

All four must be green. They run against the emulators, with no real Firebase
project and no credentials.

`functions/test/e2e.test.mjs` automates most of section 7.2 below: it
registers two institutions, records loans at each, and asserts that the
director dashboard's figures for one college never absorb the other's. What it
cannot cover is the offline behaviour of the real clients, so still walk
through 7.2 by hand before a rollout.

### 7.2 Manual, two institutions (spec section 9.7)

1. Register **GDC-A** and **GDC-B** through the onboarding form. Approve both.
2. Sign in to the desktop as GDC-A's admin. Add a book and a member.
3. Sign in to Android as the same account. **The book appears.**
4. Issue that book on the desktop **with Wi-Fi off**:
   - it appears in the UI immediately;
   - `sync_queue` holds one row (`SELECT * FROM sync_queue`).
5. Reconnect. Within seconds the row leaves the queue and the loan appears on
   Android and on the web dashboard.
6. Open the director portal. Both colleges are listed, with GDC-A showing one
   active loan. Press **Recompute figures** if the schedule has not run.
7. **Tenant isolation:** sign in as GDC-B's admin and confirm GDC-A's members
   are not readable. The rules tests assert this, but confirm it live.
8. **Conflict:** edit the same member on two machines with both offline, then
   reconnect both. A `sync_conflicts` document appears and **neither edit is
   silently lost**.

### 7.3 What "working" looks like on the dashboard

- Rows sourced from **summary** — the server rollup ran.
- Rows sourced from **self-reported** — functions are not deployed (Spark), and
  the figures are whatever each college published about itself.
- **Unsynced Records** above zero — that college has records with no `deleted`
  field, predating the sync envelope. They are invisible to every client query,
  so its real figures are higher than shown. Backfill the field.

---

## 8. Known gaps

Carried deliberately, and worth tracking:

- **Collection naming.** The KPK spec names the tenant collections `students`,
  `staff` and `issues`. This deployment has written `members` and
  `issued_books` from all four clients since before that spec, so the deployed
  names remain canonical and the spec names are read as aliases everywhere
  data is aggregated. Renaming would orphan every existing record; it is a data
  migration, not a rename.
- **Soft-delete field.** The envelope uses `deleted`; the spec writes
  `isDeleted`. Both are indexed and both are read. `deleted` is canonical.
- **Desktop conflict review UI.** Conflicts are detected, preserved in both
  Firestore and SQLite, and flagged in `sync_review`. There is no screen yet for
  a librarian to resolve one — they are visible but must be resolved manually in
  the data.
- **Android build unverified.** The claims change in `AuthViewModel` was
  reviewed but not compiled; no Android SDK was available. Run
  `./gradlew :app:compileDebugKotlin` before shipping.
- **`web/`** is an abandoned Next.js scaffold, separate from `web-app/`. It is
  not deployed and can be deleted.
