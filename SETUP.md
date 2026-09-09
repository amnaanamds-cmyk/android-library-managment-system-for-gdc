# NEXLIB — Setup From Scratch

One path, in order. Do not skip a step; each depends on the one before.

If you only want to get the code building today, **Part 1 and Part 2 are
enough**. Parts 3–6 are for putting the system into real use across the
Government Degree Colleges of Khyber Pakhtunkhwa.

- Reference for daily work: **[RUNNING.md](RUNNING.md)**
- The directorate portal, start to finish: **[DIRECTORATE.md](DIRECTORATE.md)**
- Deeper production detail: **[DEPLOYMENT.md](DEPLOYMENT.md)**

---

## What you are installing

| Surface | Stack | Used by |
|---|---|---|
| **Android app** | Kotlin + Jetpack Compose | students, staff on the move |
| **Desktop app** | Python 3.10+ + PyQt6 | the librarian at the front desk |
| **Web dashboard** | Next.js | staff without a desktop install |
| **Director portal** | Next.js, `/director` | the directorate — read-only, all colleges |
| **Backend** | Cloud Functions | onboarding, roles, province-wide rollup |

All five share **one Firebase project**. Nothing has its own database.

---

# Part 1 — Get the code

## 1.1 Install the tools

| Tool | Version | Needed for |
|---|---|---|
| Git | any | everything |
| **Java JDK 17** | exactly 17 | Android build |
| Android Studio | Ladybug+ | Android app |
| Android SDK | API 34 | Android app |
| Python | 3.10+ | desktop app |
| Node.js | 20 or 22 | web, backend, tests |

When installing Python, **tick "Add python.exe to PATH"**, or nothing below
will find it.

## 1.2 Clone

Use a **short path**. Windows has a 260-character limit and the Gradle build
creates deeply nested folders inside `app\build\`; a long path produces
"file not found" errors that look like something else entirely.

```bat
cd /d E:\
git clone -b claude/repo-contents-review-6e0ub3 https://github.com/amnaanamds-cmyk/android-library-managment-system-for-gdc.git nexlib
cd nexlib
```

> **`-b claude/repo-contents-review-6e0ub3` is required.** Without it you get
> `main`, which does not have any of this work.

## 1.3 Install dependencies

```bat
cd functions   && npm install && npm run build && cd ..
cd web-app     && npm install && cd ..
cd gdc_desktop && pip install -r requirements.txt && cd ..
```

## 1.4 Create the local config files

These are git-ignored. They never arrive with a pull — you create them once.

**`local.properties`** (repo root) — Android Studio writes `sdk.dir` itself the
first time you open the project. Add the AI key only if you use those screens:

```properties
sdk.dir=C\:\\Users\\YOU\\AppData\\Local\\Android\\Sdk
GEMINI_API_KEY="your-key-or-leave-empty"
```

**`gdc_desktop\.env`** — copy the template and edit:

```bat
cd gdc_desktop
copy .env.example .env
notepad .env
```

```properties
COLLEGE_ID=GDC-ZIAM-SHERPAO-CHARSADDA
FIREBASE_CRED_PATH=serviceAccountKey.json
FIREBASE_WEB_API_KEY=...
INACTIVITY_TIMEOUT=1800
```

> `COLLEGE_ID` must match a real `institutionId` **exactly**. A mismatch does
> not error — the desktop simply syncs into a tenant nobody is looking at, and
> it presents as "sync is broken". This is the single most common cause of two
> clients showing different data.

**`gdc_desktop\serviceAccountKey.json`** — Firebase Console → Project Settings
→ Service Accounts → *Generate new private key*.

---

# Part 2 — Run each app

## 2.1 Android

1. Android Studio → **File → Open** → select the **repository root**
   (the folder with `settings.gradle.kts`). Not `app/`.
2. **File → Settings → Build, Execution, Deployment → Build Tools → Gradle →
   Gradle JDK → 17.** A different JDK fails with a Kotlin/JVM target error that
   does not mention the JDK.
3. SDK Manager → install **Platform 34**.
4. Wait for the first Gradle sync (several minutes — it downloads AGP + Gradle).
5. Pick a device: **Tools → Device Manager → ▶**, or a phone with USB debugging.
6. Run configuration should say **`app`**. Press **▶** (`Shift+F10`).

**Or, without Android Studio:** double-click **`run-android.bat`**. It finds
Java and adb, checks a device is attached, then builds, installs and launches.

## 2.2 Desktop

```bat
cd gdc_desktop
python main.py
```

Or double-click **`run-desktop.bat`**.

Without `serviceAccountKey.json` it starts in offline mock mode: the UI works
fully, sync does not.

## 2.3 Web + Director portal

```bat
cd web-app
npm run dev
```

Or double-click **`run-web.bat`**.

- `http://localhost:3000/dashboard` — a college's own workspace
- `http://localhost:3000/director` — the directorate portal

## 2.4 Check it works

```bat
python tests\desktop\test_sync_engine.py     :: offline sync, 33 cases
python tools\check_kotlin_trailing_lambda.py  :: Kotlin parameter-order bugs

cd tests\firestore && npm test                :: security rules, 67 + 5 cases
cd ..\..\functions && npm test               :: rollup, 17 cases
npm run test:e2e                              :: two institutions, 28 cases
python tests\tools\test_repair_tenant.py     :: tenant consolidation, 21 cases
```

The full Kotlin check (syntax + duplicate declarations) needs bash — run it
from Git Bash, or from WSL:

```bash
./tools/kotlin-syntax-check.sh
```

All run against local emulators. No Firebase project, no credentials.

### Run the web app without a Firebase project

The dashboards and the director portal need someone to be signed in, which
normally means a real project. They can be pointed at the emulators instead:

```bash
firebase emulators:start --only auth,firestore    # terminal 1
node tests/portal/seed.mjs                        # terminal 2 — a directorate
                                                  # officer and 8 colleges
cd web-app                                        # terminal 3
set NEXT_PUBLIC_FIREBASE_EMULATOR=1
npm run dev
```

Sign in as `directorate.officer@hed.gkp.pk` / `Passw0rd!` and open
`/director`. The browser test that drives that console is
`tests/portal/directorate.test.mjs` — see
[tests/portal/README.md](tests/portal/README.md).

---

# Part 3 — The Firebase project

Skip if you already have `nexlib-e7970` and only want to run locally.

```bash
npm install -g firebase-tools
firebase login
firebase use --add          # pick the production project
```

**Firestore location must be `asia-south1` (Mumbai)** — closest to KPK, and it
must match `REGION` in `functions/src/config.ts`. **This cannot be changed
after the database is created.**

Authentication → Sign-in method → enable **Email/Password**.

Deploy the security rules and indexes:

```bash
firebase deploy --only firestore:rules,firestore:indexes
```

Index builds take minutes to hours on a populated database. Until they finish
the queries behind them fail, and the rollup reports those counts as zero — so
a half-built index looks like suspiciously low numbers, not an error.

### Cloud Functions need the Blaze plan

Registration, role claims and the province-wide rollup are Cloud Functions.
**Spark cannot run them.** The system still works on Spark, with a weaker
directorate story:

| On Spark | On Blaze |
|---|---|
| Institutions created by hand | Onboarding form |
| Roles set by hand in Firestore | Claims minted automatically |
| Dashboard shows **self-reported** figures | Server-computed figures |

To deploy them once on Blaze:

```bash
cd functions && npm ci && npm run build && cd ..
firebase deploy --only functions
```

---

# Part 4 — The first directorate account

**This one step cannot be done from inside the app**, and it has to be done
before anyone can use the director portal.

`setUserRole` requires a caller who is already `directorate`, and registration
only ever creates college admins. So the first one is created by hand.

1. Firebase Console → Authentication → **Add user** (email + password).
2. Then, once, from a machine holding the service account key:

```bash
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

That account can then promote everyone else from the app.

> **`directorate` is not the same as `director`.** A college's `director` is
> that college's own administrator. Only `directorate` sees all 300 colleges,
> and it deliberately has **no** institutionId.

---

# Part 5 — Onboard a college

1. Sign in to `/director` as the directorate account.
2. **Onboard College** → name, district (from the list — free text fragments
   the district rollups), administrator name and email.
3. Submit. This creates, in one batch: the institution, its meta profile, the
   admin's auth account with claims, and the registry entry as `pending`.
4. **Write down the temporary password.** It is shown once and stored nowhere.
   If lost, use a Firebase password reset — do not re-register the college.
5. Approve it on the network overview. Approval summarises it immediately.
6. Give the college:
   - its **institutionId** → their desktop `.env` `COLLEGE_ID`
   - its **invite code** → the Android/desktop join flow

A **pending** college can sign in and run its library. It just does not report
to the directorate until approved. That gate is what keeps growth deliberate.

---

# Part 6 — Distribute

| Surface | Build | To |
|---|---|---|
| Android | `gradlew :app:bundleRelease` | APK link, then Play Store |
| Desktop | `python -m PyInstaller --clean gdc_desktop.spec` | zip `dist\NEXLIB\` |
| Web | `next build` | Vercel, root `web-app`, region `bom1` |
| Director | same build | Vercel, `/director` |

Android release signing needs `keystore.properties` at the repo root:

```properties
storeFile=C:\\keys\\nexlib-release.jks
storePassword=...
keyAlias=nexlib-key
keyPassword=...
```

Without it the release build produces an **unsigned** APK and says so, rather
than silently falling back to the debug key — a debug-signed APK installs fine
and can then never be upgraded by a properly signed one.

---

# Before real province-wide use

Three things are genuinely blocking. None stop a demo; all get worse with every
college added. Full detail in [DEPLOYMENT.md §0](DEPLOYMENT.md).

**1. The signing key is compromised.** `nexlib-release.jks` was committed to
this repository with its password in plaintext, and both are still in git
history. Anyone with repo access can sign an APK Android accepts as an update
to yours. Generate a new key, purge the old one from history, rotate the Gemini
and Firebase credentials.

**2. The desktop ships an Admin SDK credential.** `gdc_desktop` uses
`firebase-admin`, which **bypasses `firestore.rules` entirely**. At one college
that is untidy. At 300 it means every front desk holds a key that reads and
writes *every other college's* patron records, extractable by anyone who can
copy a file. The fix is to route it through the ID token it already obtains at
login — a rewrite of `firebase_service.py`'s data layer, not yet done.

**3. Cloud Functions need Blaze.** See Part 3.

---

# When something is wrong

| Symptom | Cause |
|---|---|
| Two clients show different book counts | Different `institutionId`. Run `python tools\diagnose_sync.py` from `gdc_desktop\`. |
| Desktop says "Offline (Retrying)" | Missing or invalid `serviceAccountKey.json`. |
| Director portal loads, every query fails | Account is `director`/`admin`, not `directorate`. |
| Android and web can't write, desktop can | A rules regression — the desktop bypasses rules. Run the rules tests. |
| Kotlin/JVM target mismatch | Gradle JDK is not 17. |
| "file not found" during Gradle build | Path too long. Re-clone to a shorter path. |
| A college shows zero everywhere | Never synced, or `COLLEGE_ID` does not match a real institution. |

The sync diagnostic is the one to reach for first, because a tenant mismatch is
indistinguishable from a broken sync engine when you only look at the apps:

```bat
cd gdc_desktop
python ..\tools\diagnose_sync.py
```

It only reads. It reports every institution, every account's effective
`institutionId`, data stranded under ids with no institution document, and what
the local `.env` is set to.

If it reports a split — accounts on different institutionIds, or records under
an id with no institution document — `repair_tenant.py` consolidates them:

```bat
cd gdc_desktop
python ..\tools\repair_tenant.py --to GDCZIAM112233           :: dry run
python ..\tools\repair_tenant.py --to GDCZIAM112233 --apply
```

It is a dry run unless you pass `--apply`. It copies stranded records into the
canonical institution keeping each document's id (so nothing duplicates),
repoints every account's profile **and its custom claim** — claims win at
runtime, so leaving those behind would undo the whole exercise — and leaves the
old tenants in place until you pass `--delete-source`. Directorate accounts are
never touched: they are province-wide and deliberately have no institutionId.

Everyone must sign out and back in afterwards. A custom claim is baked into the
ID token and does not change until the token is refreshed.

Rehearse it against a copy first if you like — with the emulators running it
needs no service account:

```bash
firebase emulators:start --only auth,firestore
python tests/tools/test_repair_tenant.py     # 21 cases
```
