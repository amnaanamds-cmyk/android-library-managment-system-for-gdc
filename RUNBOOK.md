# NEXLIB — Install & Go-Live Runbook

How to build, install and start all four apps, and how a college joins the
network. Follow in order; each step says how to verify before moving on.

Architecture background is in `SYNC_ARCHITECTURE.md`. Known security issues
are in `SECURITY.md` — **read that before distributing anything.**

## The four apps

| App | Folder | Who runs it | How it ships |
|---|---|---|---|
| **Android** | `app/` (+ `shared/`) | College librarians | `.apk` |
| **Desktop** | `gdc_desktop/` | College library office | `.exe` (Windows) |
| **College web portal** | `web-app/` | College staff, in a browser | Web host, port 3000 |
| **Directorate portal** | `directorate-app/` | Higher Education Dept only | Web host, port 3001 |

The directorate portal is a **separate application** from the college portal.
It has its own login and admits only `directorate_admin` accounts. Keeping
them separate is deliberate: when they shared one app, a college's own login
could reach the network-wide dashboard.

Ignore `gdc_desktop/directorate_server/` and `start_directorate_server.bat` —
an older FastAPI prototype, unrelated to Firestore. Don't run it.

---

## 1. One-time Firebase setup

From the [Firebase Console](https://console.firebase.google.com/u/0/project/nexlib-e7970):

1. **Service account key** — Project Settings → Service Accounts → *Generate
   new private key*. Save as `gdc_desktop/serviceAccountKey.json` (gitignored;
   never commit it). This is a full-admin credential — see `SECURITY.md`.
2. **Web API key** — Project Settings → General → *Web API Key*.

Copy `gdc_desktop/.env.example` to `gdc_desktop/.env`:

```
FIREBASE_CRED_PATH=serviceAccountKey.json
FIREBASE_WEB_API_KEY=<the web API key>
FIREBASE_STORAGE_BUCKET=nexlib-e7970.firebasestorage.app
```

Android (`app/google-services.json`) and both web apps already carry matching
config in the repo — nothing to do there.

## 2. Deploy the security rules

Nothing is enforced until these are deployed, and they are **not** deployed
automatically. Android and web are rule-constrained; desktop uses the Admin
SDK and bypasses rules entirely, which is why a rules problem can look like
"only desktop works".

```bash
cd tests/firestore && npm install --legacy-peer-deps && npm test   # optional but advised
cd ../.. && firebase deploy --only firestore:rules
```

**Verify:** Firebase Console → Firestore → Rules — the timestamp should be
today, and `isDirectorateAdmin` should list only `DirectorateAdmin` and
`directorate_admin`.

## 3. Create the directorate account

Nothing can be approved without one. From `gdc_desktop/`:

```bash
python scripts/manage_directorate.py create-directorate director@nexlib.com YourPassword123
```

Creates the Auth account and sets the role in one step — no signup needed.
A directorate account belongs to **no college** by design.

**Verify:** `python scripts/manage_directorate.py list-users` shows it as
`directorate_admin` with a blank institution.

Do **not** promote a college's own owner account: a `directorate_admin` can
only open the Directorate, Reports and Transfers screens, so that account
would lose Books, Members and Issue/Return.

## 4. Build the Android APK

```bash
gradlew clean
gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

Signing is configured in `app/build.gradle.kts`. To install as an *update*
over an existing install rather than a reinstall, bump `versionCode` first.

**Install:** uninstall any previous build first — reinstalling over the top
keeps app data, including a cached institution id that will stop sync working.

```bash
adb uninstall com.college.library
adb install app/build/outputs/apk/release/app-release.apk
```

## 5. Build the Windows .exe

```bash
cd gdc_desktop
build_exe.bat
```

Output: `gdc_desktop/dist/NEXLIB/NEXLIB.exe`

Then **copy two files next to the .exe**, into `dist/NEXLIB/`:

- `serviceAccountKey.json`
- `.env`

They are deliberately not bundled — see `SECURITY.md`. `config.APP_DIR`
resolves them beside the executable when frozen.

**Local database:** lives at `%USERPROFILE%\GDCLibrary50\gdc_library.db` and
survives every rebuild. For a genuinely fresh start, close the app and move
all three files aside (SQLite keeps `-shm` and `-wal` alongside the `.db`; a
stale journal beside a new database can corrupt it):

```powershell
mkdir "$env:USERPROFILE\GDCLibrary50\old_data"
move "$env:USERPROFILE\GDCLibrary50\gdc_library.db*" "$env:USERPROFILE\GDCLibrary50\old_data\"
```

## 6. Run the college web portal

```bash
cd web-app
npm install
npm run dev      # http://localhost:3000
```

For production: `npm run build && npm run start`, or deploy to any Node host
(`vercel --prod` from `web-app/` is the least friction).

## 7. Run the directorate portal

```bash
cd directorate-app
npm install
npm run dev      # http://localhost:3001
```

Port 3001 so it runs alongside the college portal. Sign in with the
`directorate_admin` account from step 3. Any other account is signed straight
back out — enforced again by `isDirectorateAdmin()` in the rules, so it
cannot be bypassed by editing client code.

---

## 8. How a college joins

This is the flow every college follows. No directorate action is needed to
*start*; approval only controls what the directorate sees.

1. The college installs the Android app or the `.exe`.
2. A librarian creates an account and, on the onboarding screen, chooses
   **Create Institution** with a College Unique ID of their own (e.g.
   `GDC-MARDAN-01`). That account becomes `owner`.
3. Additional staff and devices use **Join Institution** with the same ID.
   Joining preserves whatever role the account already has.
4. The college can catalogue books, register members and issue loans
   immediately. It does not wait for anyone.
5. In the directorate portal the college appears under **Awaiting approval**,
   contributing **nothing** to network totals.
6. The directorate presses **Approve**. Only then does the college's data
   appear in the network view and count toward the totals.

### Removing a college

**Remove** on the directorate dashboard hides a college and stops it counting.
It does **not** delete anything: the college's books, members and loans stay
intact and its own apps keep working. Reversible via **Restore** in the
"Removed from dashboard" section.

Approval state lives in `/directorate_approvals`, writable only by
`directorate_admin`. It is not a field on the registry document a college
publishes for itself — otherwise a college could approve itself.

---

## 9. Verify the whole chain

```bash
cd gdc_desktop
python scripts/manage_directorate.py list-users      # who exists, what role, which college
python scripts/manage_directorate.py list-colleges   # registry counts per college
```

End-to-end test worth doing before delivering anything, on your own machine:
register a college as if you were one, confirm it shows as pending in the
directorate portal, approve it, then add a book on one device and watch the
count change on the others.

## Starting over — one command

```bash
cd gdc_desktop
python scripts/manage_directorate.py fresh-start director@nexlib.com YourPassword123 --confirm nexlib-e7970
```

Does the whole reset in the order that makes it stick:

1. Archives this machine's desktop database to `GDCLibrary50/old_data_<timestamp>/`
2. Deletes every Firestore document, recursing into subcollections
3. Deletes every Firebase Auth login (`--keep-logins` to keep them)
4. Recreates the directorate account

Local goes first on purpose. Windows locks an open SQLite file, so a failure
at step 1 *is* the "an app is still running" check — and the script stops
before touching Firestore rather than wiping a cloud that a running desktop
app would refill seconds later. That app syncs through the Admin SDK, so it
bypasses the security rules and can recreate an institution that was deleted
from the console.

Flags: `--keep-logins` keeps Auth accounts (their profiles are still wiped, so
they land on onboarding next sign-in); `--keep-local` leaves the desktop
database alone.

Android is the only manual step — its data is on the device:

```bash
adb uninstall com.college.library
```

Uninstall, not reinstall: installing over the top keeps the cached institution
id, and sync then targets a college that no longer exists.

To archive just the local database without touching the cloud (no service
account key needed):

```bash
python scripts/manage_directorate.py clear-local
```

The older, narrower commands still exist: `wipe-all` (Firestore only) and
`wipe-all --include-auth`.

## Troubleshooting

| Symptom | Cause |
|---|---|
| Android badge reads **"Not linked — sign in again"** | No institution cached. Sign out and back in; the id is written only at login. |
| Android badge reads **"Offline"** | Firebase itself did not initialise — a build problem (`google-services.json`), not a network one. |
| **PERMISSION_DENIED** on any app | Usually a missing `/users/{uid}` profile: rules read the caller's role from it. Check `list-users`. |
| Sync works on desktop, not Android/web | Desktop uses the Admin SDK and bypasses rules. A rules or profile problem shows up everywhere else first. |
| Books reappear after deleting | An old local database pushed them back. Clear local data before connecting to a new institution. |
| College missing from directorate portal | Not approved yet — check **Awaiting approval**. |
