# NEXLIB Directorate Portal

Network-wide oversight for the Higher Education Department. A **separate
application** from the college web portal (`web-app/`): it has its own login,
its own shell, and its own port, and shares only the Firebase project.

Kept separate on purpose — the college portal is a single college's workspace,
scoped to its own `institutionId`, while this reads aggregate figures across
every college. Keeping them in one app is what previously let a college's own
login reach the network-wide dashboard.

## Run it

```bash
cd directorate-app
npm install
npm run dev
```

Then open <http://localhost:3001>. The college web portal runs on port 3000, so
both can run at the same time.

## Signing in

Only accounts with role `directorate_admin` can sign in. Any other account is
signed straight back out with an explanation — this is enforced again by
`isDirectorateAdmin()` in `firestore.rules`, so the portal cannot be bypassed
by editing client code.

To create a directorate account, from `gdc_desktop/`:

```bash
python scripts/manage_directorate.py create-directorate <email> <password>
```

## What it can read

- `/directorate_index/{collegeId}` — each college's published aggregate counts
- `/institutions/{collegeId}/books` — the public catalogue (already world-readable for the OPAC)

It never reads patron records and never writes tenant data. That boundary is
enforced by the security rules, not just by this app.
