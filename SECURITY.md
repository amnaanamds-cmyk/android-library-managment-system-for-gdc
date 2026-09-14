# Security notes — NEXLIB

## 1. Release signing keystore is committed to this repository

**Status: open, accepted for now. Decide before wider distribution.**

### What is exposed

| Item | Location | In git? |
|---|---|---|
| Release keystore | `nexlib-release.jks` (repo root, 2.7 KB) | **Yes**, since commit `29e2cbd` |
| Keystore password | `app/build.gradle.kts` line 52 | **Yes**, plaintext |
| Key alias | `app/build.gradle.kts` line 53 | **Yes**, plaintext |
| Key password | `app/build.gradle.kts` line 54 | **Yes**, plaintext |

Both halves of the secret are in the same repository. Anyone who can clone it
has everything needed to sign an APK as this application.

### Why it matters

Android identifies an app by its signing key, not by its name or origin. An APK
signed with this keystore and declaring `applicationId com.college.library` is,
as far as any Android device is concerned, an authentic NEXLIB build.

Concretely, someone with this repository can:

1. Build a modified NEXLIB — one that copies student records, credentials or
   issue history to a server they control, with the real UI intact.
2. Sign it with `nexlib-release.jks`.
3. Distribute it (WhatsApp, a college notice board QR, a lookalike site). The
   app already encourages APK sharing: Settings → "Share App (.apk)".
4. Devices with the legitimate app installed will accept it as an **update**,
   not a separate app — no warning, no uninstall prompt, data carried over.

For a Higher Education Department deployment across many colleges, the blast
radius is every device that installs the forged update.

This is independent of Firestore rules. The forged app authenticates as a real
user, so the rules grant it exactly what that user is allowed — which for a
librarian account is their college's full patron and circulation data.

### What does NOT reduce the risk

- A PIN or biometric lock on the app. The attacker ships their own build.
- Firestore security rules. They are working as designed; the forged app is a
  legitimate client.
- Removing the file in a new commit **alone**. Git keeps history: anyone can
  recover it with `git show 29e2cbd:nexlib-release.jks`.

### Remediation when you are ready

Ordered from most to least effective.

1. **Generate a new keystore and treat the current one as compromised.**
   Only viable while the app is not yet widely installed — Android will not
   install an update signed with a different key, so existing installs would
   need a manual uninstall and reinstall.

2. **Enable Play App Signing** if the app goes to the Play Store. Google holds
   the real signing key and you upload with a separate upload key, so an
   exposed upload key can be reset without breaking installed apps. This is
   the only option that recovers cleanly from exposure after distribution.

3. **Move the credentials out of the build file** regardless of the above:
   ```kotlin
   storePassword = localProperties.getProperty("KEYSTORE_PASSWORD") ?: ""
   keyPassword  = localProperties.getProperty("KEY_PASSWORD") ?: ""
   ```
   `local.properties` is already gitignored. Also add `*.jks` to `.gitignore`
   and `git rm --cached nexlib-release.jks`.

4. **Purge from history** (`git filter-repo`, or BFG). This rewrites commits
   and requires a force-push, so coordinate with anyone holding a clone. Note
   that if the repository was ever public or shared, treat the key as already
   copied — purging history does not un-copy it.

### Also worth fixing

- The root `.env` is **not** in `.gitignore`. Nothing is committed there today,
  but one `git add -A` would do it. Add it.
- `storeFile` hardcodes an absolute Windows path
  (`E:/android library managment system/...`), so a release build only works on
  one machine. Move it to `local.properties` alongside the passwords.

## 2. Things that are correctly handled

Worth recording so they are not "fixed" into a worse state later:

- `gdc_desktop/serviceAccountKey.json` is gitignored. This grants **full admin
  access to the whole Firebase project** and bypasses all security rules — it
  is the single most dangerous file in the system and it is correctly kept out
  of git. Never commit it.
- `local.properties` (Gemini API key, and ideally the keystore passwords) is
  gitignored.
- `web-app/.env` is gitignored.
- `app/google-services.json` is committed, which is **fine** — it holds client
  configuration, not secrets. Firebase access is controlled by security rules
  and Auth, not by hiding this file.
- Cross-tenant access is enforced in `firestore.rules` and covered by
  behavioural tests in `tests/firestore/`. Run them before deploying rules:
  ```bash
  cd tests/firestore && npm install && npm test
  ```
