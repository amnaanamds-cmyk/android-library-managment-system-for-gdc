# NEXLIB Sync & Directorate Architecture

How the Android, Desktop and Web apps share one Firebase backend, and how the
directorate sees across all of them.

---

## 1. Canonical data layout

```
/users/{uid}                              global profile: role + institutionId
/institutions/{collegeId}                 tenant metadata (name, inviteCode, ownerUid, syncCode)
/institutions/{collegeId}/books/{syncId}          printed catalogue (publicly readable — OPAC)
/institutions/{collegeId}/ebooks/{syncId}         digital catalogue
/institutions/{collegeId}/members/{syncId}        patrons
/institutions/{collegeId}/issued_books/{syncId}   circulation
/institutions/{collegeId}/reservations/{syncId}   holds
/institutions/{collegeId}/settings/{docId}        fine rate etc.
/institutions/{collegeId}/audit_log/{entryId}     audit trail
/institutions/{collegeId}/<anything else>         visitor_log, purchase_orders,
                                                  book_transfers, ill_requests, …
/directorate_index/{collegeId}            aggregate-only snapshot for the directorate
/colleges/{collegeId}                     LEGACY mirror, read-only compatibility
```

`institutionId` is never hardcoded. It is resolved at login from
`users/{uid}.institutionId` and held in the local session (SQLite on desktop,
SharedPreferences / SQLDelight on Android, the auth context on web).

### The sync envelope

Every tenant record carries these fields. All three sync engines depend on them:

| Field | Purpose |
|---|---|
| `syncId` | UUID, and also the Firestore **document id** |
| `collegeId` | owning tenant |
| `lastUpdated` | epoch millis; drives last-write-wins conflict resolution |
| `deleted` | soft delete — records are never hard-deleted, or an offline peer would resurrect them |
| `syncStatus` | `"synced"` / `"pending"` |

**Canonical loan state is `status`**, valued `"Issued"` or `"Returned"`. There is
no `returned` boolean — several web pages used to test for one, and since no
platform ever wrote it, every loan ever recorded counted as active.

Shared predicates live in:
- `web-app/src/lib/schema.ts` — `isActiveIssue`, `isOverdue`
- `gdc_desktop/services/registry_service.py`
- `shared/src/commonMain/kotlin/com/college/library/data/DirectorateRegistry.kt`

Keep the three in step.

---

## 2. Auth & institution resolution

1. User signs in with Firebase Auth (email/password).
2. The app reads `users/{uid}`. No `institutionId` → onboarding screen.
3. **Create**: writes `/institutions/{cid}` with `ownerUid` set to the creator,
   sets `users/{uid}.institutionId` + `role: owner`, then registers the college
   in `/directorate_index/{cid}`.
4. **Join**: verifies `/institutions/{cid}` exists, then sets
   `users/{uid}.institutionId` + `role: staff`.
5. All runtime queries prefix `/institutions/{session.institutionId}/`.

> **Never add a fallback institution id.** A fallback is not an inert failure —
> the fallback id belongs to a real college, so the device silently attaches to
> another institution's data. Five such fallbacks to `"gdc11"` were the cause of
> the cross-platform sync problems. If no institution is resolved, do nothing.

---

## 3. Security rules — two rules that matter

`firestore.rules` is enforced for **Android and Web only**. The desktop app uses
the `firebase-admin` SDK, which bypasses rules entirely. That asymmetry is why a
rules bug presents as "the desktop app works but the others don't."

### What was actually broken

Measured by running `tests/firestore/rules.test.mjs` against the old rules
(20 passed, 10 failed):

| Denied under the old rules | Consequence |
|---|---|
| `audit_log` writes | no audit trail from Android or Web |
| `visitor_log`, `purchase_orders`, `book_transfers`, `ill_requests` | gate log, acquisitions, transfers and ILL silently did nothing |
| `_sync/heartbeat` | the Android WorkManager sync job failed every run |
| `settings` writes, **even by the institution's owner** | fine rate never synced |
| `directorate_index` reads **and** writes | the directorate dashboard could never be populated — this is why it was empty |

Books, members, loans and reservations *were* writable. Firestore tolerates an
evaluation error in one branch of a `||` when another branch allows, so the
`null.data` bug below degraded those paths without blocking them.

### Two rules to follow when editing

**Never call `.data` on a `get()` that might not exist.** `get()` returns null
for a missing document and `null.data` raises. The old `isCollegeDirector()`
did this against `/colleges/{id}`, a registry document the desktop onboarding
flow never created. Where it was the *only* condition — `settings` — the rule
denied outright. Guard with `exists()` first.

**Never index a map key that may be absent.** `x in profile.collegeIds` raises
when the profile has no `collegeIds` field. Use `profile.get('collegeIds', [])`.

**Rules are a union, not first-match.** Access is granted when *any* matching
rule allows it. The catch-all for tenant sub-collections must therefore
explicitly exclude `settings` and `audit_log`, or it re-grants exactly what
their stricter rules restrict.

Deploy with:

```bash
firebase deploy --only firestore:rules,firestore:indexes
```

Test first — the desktop app bypasses rules entirely, so a rules regression is
invisible from it:

```bash
cd tests/firestore && npm install && npm test
```

---

## 4. Sync engines

All three follow the same offline-first shape:

| | Android | Desktop | Web |
|---|---|---|---|
| Local store | SQLDelight | SQLite | Firestore browser cache |
| Live updates | `snapshots()` flows | `on_snapshot` listeners | `onSnapshot` |
| Push | unsynced-record queue | `sync_queue` table | direct write |
| Conflict | last-write-wins on `lastUpdated` | same, with a conflict log | same |
| Status | `SyncService.status` | `sync_status` signal | `useSyncHealth()` |

Ebooks push to their own collection so a large digital upload can never block
catalogue or circulation sync.

The web status indicator reads Firestore's `metadata.fromCache` — when true the
browser is serving cache rather than the server, which is the honest offline
signal. It was previously a hardcoded green dot, so a session whose writes were
all being rejected still looked healthy.

---

## 5. The directorate registry

The directorate needs network-wide totals, but tenant data is readable only by
each college's own staff, Firestore has no cross-tenant aggregate query, and
**Cloud Functions are not available on the Spark plan**. So the rollup is
published by the clients.

Each college's app writes one small document to `/directorate_index/{collegeId}`:

```jsonc
{
  "institutionId": "GDC-ZIAM-01",
  "name": "Govt Degree College Ziam",
  "location": "Sherpao, KP",
  "booksCount": 4210, "ebooksCount": 118, "membersCount": 963,
  "activeLoans": 212, "overdueCount": 17,
  "reservationsCount": 8, "finesOutstanding": 3450.0,
  "lastSyncAt": 1756814400000,
  "lastSyncPlatform": "desktop",   // web | desktop | android
  "schemaVersion": 2
}
```

Counts only — no book records, no patron records. That is what makes it safe to
expose network-wide while tenant data stays isolated.

**Who publishes, and when**

| Platform | Trigger | Throttle |
|---|---|---|
| Desktop | sync loop, and on manual reconnect | 10 min |
| Android | after each real-time batch; forced on login and manual sync | 10 min |
| Web | dashboard load once collections have settled | 5 min |

Publishers:
- `gdc_desktop/services/registry_service.py`
- `shared/src/commonMain/kotlin/com/college/library/data/DirectorateRegistry.kt`
- `web-app/src/lib/directorate.ts`

**Why one registry.** Three had grown up in parallel — `/colleges` (web
onboarding), `/directorate_index` (Android + the desktop profile screen), and
profile fields on `/institutions` — with different field names and different
writers. The web director page read `/colleges` filtered on a `directorUid`
nothing wrote, and rendered `collegeName` where onboarding had written `name`,
so it showed blank rows. `/directorate_index` is now authoritative; `/colleges`
is still written as a legacy mirror, and the reader normalises every historical
field spelling so colleges on older builds still appear.

---

## 6. Director portal

Route: **`/director`** in `web-app` (separate from `/dashboard`, which is scoped
to a single institution and writes tenant data).

- `/director` — network KPIs, sortable college registry, search, CSV export
- `/director/[collegeId]` — per-college drill-down

Access requires a role in `DIRECTOR_ROLES` (`web-app/src/lib/directorate.ts`).
Grant it by setting `role: "director"` on the user's `users/{uid}` document.

The portal is read-only and its account is deliberately **not** a member of any
college, so patron-level data stays closed to it by the security rules, not just
by the UI. What it can read is each college's published snapshot plus the public
catalogue (`/institutions/{id}/books`, readable by anyone, since the OPAC is
public).

If no college has published yet, the portal falls back to enumerating
`/institutions` and marks those colleges "not reporting" — so a freshly upgraded
deployment shows its network rather than an empty screen.

---

## 7. Deployment checklist

1. `firebase deploy --only firestore:rules,firestore:indexes` — **do this first**;
   without the new rules, Android and Web writes stay denied.
2. Grant directorate access: set `role: "director"` on the relevant
   `users/{uid}` documents.
3. Deploy `web-app` to Vercel (`npm run build` must pass).
4. Ship the desktop and Android builds. Each college's first sync after upgrade
   publishes its snapshot and the portal fills in.
5. Existing institutions created before this change have no `ownerUid`. Staff
   access still works through profile membership; to give an owner the
   privileged path, add `ownerUid: "<uid>"` to their `/institutions/{id}`
   document.
