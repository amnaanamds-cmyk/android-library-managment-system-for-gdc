# Running the Directorate Dashboard

The province-wide portal. One page listing every Government Degree College in
Khyber Pakhtunkhwa, with the ability to onboard, approve and suspend them.

> **It is a page in the Next.js web app, at `/director`.** There is no separate
> program to launch, no server to start, and nothing to open from the file
> system. If you are opening an `.html` file by double-clicking it, that is the
> wrong thing — the old static mock-up that did that has been deleted.

---

## The two things that stop it working

Nearly every "the directorate dashboard doesn't work" report is one of these.

**1. Your account is not `directorate`.**

`directorate` is province-wide and deliberately has **no** institutionId. A
college's own `director` is that college's administrator — a different thing
with a confusingly similar name. `owner` and `admin` are also college-scoped.
Only `directorate` opens this portal, and the security rules draw the same
line, so there is no way to widen it from the client.

If your role is wrong you get a lock icon and "Directorate access required",
naming the role you actually have.

**2. The security rules in your Firebase project are stale.**

The rules in this repository are correct — verified against your exact account
shape — but they are not in your project until you deploy them:

```bash
firebase deploy --only firestore:rules,firestore:indexes
```

Without this the portal loads and every query fails with a permission error.

---

## Route A — try it locally, no Firebase project needed

The fastest way to see the portal working, with realistic data. Nothing here
touches your live project.

**Terminal 1 — the emulators**

```bash
cd functions && npm install && npm run build && cd ..
firebase emulators:start --only auth,firestore,functions
```

**Terminal 2 — sample data**

```bash
node tests/portal/seed.mjs
```

Creates a directorate officer and eight colleges covering every state the
portal renders — active, stale, suspended, never-reported, pending approval,
and one with records that predate the sync envelope.

**Terminal 3 — the web app**

```bash
cd web-app
npm install
set NEXT_PUBLIC_FIREBASE_EMULATOR=1        # Windows: set,  macOS/Linux: export
npm run dev
```

Open **http://localhost:3000/director** and sign in:

| | |
|---|---|
| Email | `directorate.officer@hed.gkp.pk` |
| Password | `Passw0rd!` |

Everything works here, onboarding and suspension included — the functions
emulator runs the Cloud Functions regardless of your billing plan.

---

## Route B — against your real Firebase project

### B1. Start the web app

```bash
cd web-app
npm install
npm run dev
```

Or double-click **`run-web.bat`**. Then open **http://localhost:3000/director**.

### B2. Make one account `directorate`

There is no way to do this from inside the app: granting the role requires a
caller who already has it, and registration only ever creates college admins.
So the first one is created by hand, once.

**On the Spark (free) plan** — edit the profile document directly:

1. Firebase Console → **Authentication** → find the account → copy its **UID**.
   (Or **Add user** to create a dedicated one.)
2. Firebase Console → **Firestore Database** → `users` → the document whose id
   is that UID.
3. Set two fields:
   - `role` → `directorate`  (a string, exactly that, lowercase)
   - `institutionId` → `""`  (empty — this is deliberate)
4. Sign out and back in.

This works because `firestore.rules` falls back to the profile document when
an account carries no custom claim, which on Spark is every account. Verified
by `tests/firestore/spark.test.mjs`.

**On the Blaze plan** — mint a real custom claim instead, which is faster at
runtime and does not cost a document read on every rule evaluation. The
one-off bootstrap script is in [SETUP.md](SETUP.md) Part 4. After that, the
first directorate account can promote everyone else from inside the app.

### B3. Deploy the rules

```bash
firebase deploy --only firestore:rules,firestore:indexes
```

---

## What works on which plan

The portal **displays** on either plan. The management actions are Cloud
Functions, and Spark cannot run those.

| | Spark (free) | Blaze |
|---|---|---|
| Sign in, see the network | yes | yes |
| Figures | **self-reported** by each college's own app, and labelled as such on the page | server-computed by the scheduled rollup |
| District rollups, filters, search, CSV export | yes | yes |
| Drill into one college | yes | yes |
| **+ Add College** (onboarding) | no | yes |
| **Approve / Reject** a pending college | no | yes |
| **Suspend / Reactivate** | no | yes |
| **Recompute figures** | no | yes |

On Spark those four buttons are present but fail when pressed — they call
callables that do not exist. To enable them:

```bash
cd functions && npm ci && npm run build && cd ..
firebase deploy --only functions
```

A college's figures reach a Spark portal simply because someone opened that
college's dashboard: the client publishes its own aggregate row. That is why
they are labelled self-reported — a college reports its own numbers, and
nothing verifies them.

---

## Reading the page

**The banner across the top of the table** tells you how much to trust the
figures: server-computed, self-reported, or registry-only (nobody has reported
at all yet).

**Reporting `6 / 8`** counts colleges that have actually pushed data. It is a
different question from whether a college is administratively active — a
college can be approved and still be dark.

**Stale** and **Never** are also different. Stale means it was reporting and
has gone quiet for over 48 hours. Never means no client there has ever synced,
which usually means its `COLLEGE_ID` does not match a real institution id.
Both banners link to the filter that shows you which colleges.

**Suspend is what "remove" means here.** A college is never hard-deleted: its
books, members and loan history are a real library catalogue, and a suspended
college keeps running its own library. It simply stops reporting into the
province-wide figures, and can be brought back.

---

## When it still doesn't work

| What you see | Cause |
|---|---|
| Lock icon, "Directorate access required" | Your role is not `directorate`. It names the role you have. |
| Page loads, every query fails | Rules not deployed. Run the deploy command above. |
| Every college shows zero | Nobody has reported. Check each college's `COLLEGE_ID` against a real institution id. |
| A college is missing entirely | It is not in `institution_registry`. On Blaze, onboard it; on Spark, add the document by hand. |
| Two clients disagree about a college's figures | A tenant split. Run `python tools/diagnose_sync.py`, then `python tools/repair_tenant.py --to <id>`. |
| Buttons do nothing / "internal" error | Cloud Functions are not deployed. See the plan table above. |

---

## Tests

```bash
cd tests/firestore && npm test        # 67 rules cases + 5 Spark-access cases
node tests/portal/directorate.test.mjs # 13 cases, drives the real portal in a browser
```

The portal test needs the emulators and the seed from Route A. See
[tests/portal/README.md](tests/portal/README.md).
