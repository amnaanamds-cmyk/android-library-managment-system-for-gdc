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
