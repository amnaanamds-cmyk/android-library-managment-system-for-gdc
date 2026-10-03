"""
scripts/benchmark_local_db.py — how the desktop app's local SQLite DB holds up
at a large college's real data volume.

Run from the gdc_desktop directory:

    python scripts/benchmark_local_db.py
    python scripts/benchmark_local_db.py --books 100000 --members 15000 --issues 400000

WHY THIS EXISTS

scripts/nexlib_test.py's `load` mode measures Firestore read cost — the
Android/web sync path. It says nothing about the desktop app's own local
SQLite file, which every screen (BooksScreen, MembersScreen, ReportsScreen,
the fine waiver screen fixed earlier this session, the directorate-sync
snapshot) reads from directly. A few of those reads are NOT paginated at
all — compute_snapshot() and run_health_check() both load every book,
member, and issue into memory on every call — so "does this stay fast for
a college with 50,000 books after ten years of records" is a real question
this app's own tests never answered.

WHAT THIS DOES

Builds a throwaway SQLite file (never the real ~/GDCLibrary50/gdc_library.db
— config.LOCAL_DB_PATH is redirected to a temp path before DatabaseHelper
is constructed) and bulk-inserts synthetic books/members/issued_books via
the same save_*_batch() methods the app's own CSV-import feature uses, then
times the queries real screens actually run against it:

  - get_books_paginated / search_books_paginated   (BooksScreen's own calls)
  - get_members_paginated / search_members_paginated (MembersScreen's own calls)
  - get_issues()                     (unpaginated — every row, every call)
  - compute_snapshot()               (unpaginated — built before every directorate sync push)
  - run_health_check()               (unpaginated, several NOT IN subqueries)
  - get_fine_payments() + the fine-waiver balance computation fixed this
    session, run across every member — confirms that fix still holds up
    at scale, not just on the 1-member case it was unit-tested against

Flags anything over 300ms as "would be felt as lag in the UI" — the same
threshold this app's own QThread-worker fixes (issue_return_screen.py,
reports_screen.py) were written to avoid blocking on.

Deletes the temp database on exit.
"""
import argparse
import os
import random
import shutil
import statistics
import sys
import tempfile
import time

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

SLOW_MS = 300  # UI-felt-lag threshold this app's own worker-thread fixes target


def line(mark, what, detail=""):
    print(f"  [{mark}] {what}" + (f"  — {detail}" if detail else ""))


def timed(label, fn, *args, **kwargs):
    t0 = time.perf_counter()
    result = fn(*args, **kwargs)
    elapsed_ms = (time.perf_counter() - t0) * 1000
    mark = "SLOW" if elapsed_ms > SLOW_MS else "OK"
    line(mark, label, f"{elapsed_ms:.0f}ms")
    return result, elapsed_ms


def build_dataset(db, n_books, n_members, n_issues):
    from models.book import Book
    from models.member import Member
    from models.issue_record import IssueRecord

    categories = [f"Category {i}" for i in range(40)]
    statuses_books = ["Available"] * 7 + ["Issued"] * 3  # ~30% out, realistic-ish

    print(f"Generating {n_books:,} books, {n_members:,} members, {n_issues:,} issue records...")

    t0 = time.perf_counter()
    books = [
        # id=i+1 explicitly: DatabaseHelper never assigns a real row id on
        # its own (books.id is a plain, non-autoincrementing INTEGER column —
        # see the PRODUCTION BUG note at the bottom of this file). Without
        # this, every synthetic book defaults to id=0 and every issue record
        # below collapses onto one one bogus "member 0 / book 0", which is
        # what actually caused this benchmark to hang for 5+ minutes the
        # first time it was run at this scale.
        Book(
            id=i + 1,
            isbn=f"978{i:010d}", accNo=f"ACC{i:07d}", title=f"Book Title {i}",
            author=f"Author {i % 2000}", publisher=f"Publisher {i % 100}",
            category=random.choice(categories), status=random.choice(statuses_books),
            pages=100 + (i % 400), price=250.0 + (i % 50) * 10,
        )
        for i in range(n_books)
    ]
    db.save_books_batch(books, is_clean=True)
    books_elapsed = time.perf_counter() - t0
    line("OK", f"Inserted {n_books:,} books", f"{books_elapsed:.1f}s ({n_books/max(books_elapsed,0.001):.0f} rows/s)")

    t0 = time.perf_counter()
    members = [
        Member(
            id=i + 1,  # see the id=i+1 note above — same issue on members.id
            memberId=f"STU{i:06d}", name=f"Student {i}", email=f"student{i}@gdc.edu.pk",
            phone=f"03{i % 10}{i:08d}"[:11], department=f"Dept {i % 20}",
            memberType="Student" if i % 5 else "Faculty", booksIssued=i % 6,
        )
        for i in range(n_members)
    ]
    db.save_members_batch(members, is_clean=True)
    members_elapsed = time.perf_counter() - t0
    line("OK", f"Inserted {n_members:,} members", f"{members_elapsed:.1f}s ({n_members/max(members_elapsed,0.001):.0f} rows/s)")

    # Need real local row ids (not syncId) for issued_books.bookId/memberId —
    # that's what the health-check and fine-ledger joins actually key on.
    saved_books = db.get_books(include_deleted=True)
    saved_members = db.get_members(include_deleted=True)

    t0 = time.perf_counter()
    issues = []
    today_offset_days = list(range(-400, 5))  # mix of old-returned and current
    for i in range(n_issues):
        b = saved_books[i % len(saved_books)]
        m = saved_members[i % len(saved_members)]
        returned = (i % 3 != 0)  # ~2/3 returned, 1/3 still out
        fine = float((i % 15) * 10) if returned and i % 4 == 0 else 0.0
        issues.append(IssueRecord(
            bookId=b.id, bookTitle=b.title, bookIsbn=b.isbn,
            memberId=m.id, memberName=m.name, memberMemberId=m.memberId,
            issueDate="2024-01-01", dueDate="2024-01-15",
            returnDate="2024-01-20" if returned else None,
            fine=fine, status="Returned" if returned else "Issued",
        ))
    db.save_issues_batch(issues, is_clean=True)
    issues_elapsed = time.perf_counter() - t0
    line("OK", f"Inserted {n_issues:,} issue records", f"{issues_elapsed:.1f}s ({n_issues/max(issues_elapsed,0.001):.0f} rows/s)")

    # A handful of real fine_payments rows so the fine-waiver balance
    # computation (fixed this session) has something non-trivial to net off.
    for m in saved_members[:200]:
        db.save_fine_payment(m.id, 20.0, "Cash", "benchmark")


def benchmark_fine_waiver_logic(db, n_members_sample):
    """Exercises the exact balance computation fine_waiver_screen.py's
    load_data() now runs (fixed this session) — confirms the fix that was
    unit-tested on a single member still holds up when run across a real
    member list, not just that it's correct."""
    from datetime import datetime, date

    members = db.get_members()[:n_members_sample]
    issues = db.get_issues()
    payments = db.get_fine_payments()

    issues_by_member = {}
    for i in issues:
        issues_by_member.setdefault(i.memberId, []).append(i)
    payments_by_member = {}
    for p in payments:
        payments_by_member.setdefault(p["memberId"], []).append(p)

    results = []
    for m in members:
        m_issues = issues_by_member.get(m.id, [])
        total_books = len(m_issues)
        returned = [i for i in m_issues if i.status == "Returned"]
        fine_history = [i for i in returned if (i.fine or 0) > 0]
        total_fines = sum(i.fine or 0 for i in fine_history)
        total_paid = sum(p["amount"] for p in payments_by_member.get(m.id, []))
        balance = round(total_fines - total_paid, 2)
        if balance > 0:
            results.append(balance)
    return results


def main():
    ap = argparse.ArgumentParser(description="Benchmark the desktop app's local SQLite DB at scale")
    ap.add_argument("--books", type=int, default=50000, help="synthetic books (a large single college catalog)")
    ap.add_argument("--members", type=int, default=8000, help="synthetic members")
    ap.add_argument("--issues", type=int, default=250000, help="synthetic issue records (years of circulation history)")
    args = ap.parse_args()

    tmp_dir = tempfile.mkdtemp(prefix="nexlib_local_db_benchmark_")
    db_path = os.path.join(tmp_dir, "benchmark.db")
    try:
        import config
        config.LOCAL_DB_PATH = db_path  # never touches the real ~/GDCLibrary50 db
        from services.database_helper import DatabaseHelper
        db = DatabaseHelper()

        print(f"\n{'='*70}")
        print("SEEDING")
        print(f"{'='*70}")
        build_dataset(db, args.books, args.members, args.issues)

        print(f"\n{'='*70}")
        print(f"QUERY BENCHMARKS  (anything over {SLOW_MS}ms is flagged SLOW —")
        print(f"this app's own worker-thread fixes elsewhere target that line)")
        print(f"{'='*70}\n")

        timed("get_books_paginated(limit=50) — first page, BooksScreen's default view",
              db.get_books_paginated, 50, 0)
        timed("get_books_paginated(limit=50, offset=last page) — worst-case OFFSET scan",
              db.get_books_paginated, 50, max(0, args.books - 50))
        timed("search_books_paginated(title LIKE) — BooksScreen's search box",
              db.search_books_paginated, "Title 42", "All Categories", "All Status", False, 50, 0)
        timed("search_books_paginated(category + status filters)",
              db.search_books_paginated, "", "Category 5", "Available", False, 50, 0)

        timed("get_members_paginated(limit=50)", db.get_members_paginated, 50, 0)
        timed("search_members_paginated(name LIKE) — MembersScreen's search box",
              db.search_members_paginated, "Student 999", 50, 0)

        _, issues_ms = timed(
            "get_issues() — UNPAGINATED, every row, every call (fine waiver, reports, health check all use this)",
            db.get_issues)

        _, snapshot_ms = timed(
            "compute_snapshot() — UNPAGINATED, built before every directorate sync push",
            db.compute_snapshot)

        _, health_ms = timed(
            "run_health_check() — UNPAGINATED, several NOT IN subqueries across all tables",
            db.run_health_check)

        results, waiver_ms = timed(
            "fine_waiver_screen.py balance computation (this session's fix) across ALL members",
            benchmark_fine_waiver_logic, db, args.members)
        line("OK", f"  -> {len(results)} member(s) found with an outstanding balance")

        print(f"\n{'='*70}")
        print("SUMMARY")
        print(f"{'='*70}")
        print(f"  Paginated screens (books/members list + search) stay fast regardless")
        print(f"  of table size — they're genuinely paginated with LIMIT/OFFSET.")
        print(f"  The three UNPAGINATED whole-table calls scale with total row count:")
        print(f"    get_issues()         {issues_ms:>7.0f}ms  at {args.issues:,} issue records")
        print(f"    compute_snapshot()   {snapshot_ms:>7.0f}ms  at {args.books:,} books / {args.issues:,} issues")
        print(f"    run_health_check()   {health_ms:>7.0f}ms  at this scale")
        print(f"    fine-waiver scan     {waiver_ms:>7.0f}ms  across {args.members:,} members")
        print(f"\n  Re-run with larger --issues specifically — that is the table that grows")
        print(f"  without bound over a college's lifetime (books/members churn far less),")
        print(f"  and it is what all three unpaginated calls above actually scan.")
        print(f"  If any of the three go SLOW at a realistic multi-year issue count, the")
        print(f"  fix is pagination/incremental aggregation in that one method, not a")
        print(f"  blanket rewrite — the paginated screens already prove the schema itself")
        print(f"  (with its existing idx_issued_member/idx_issued_status indexes) scales.")

    finally:
        shutil.rmtree(tmp_dir, ignore_errors=True)


if __name__ == "__main__":
    main()


# ── PRODUCTION BUG, found by this script, not fully fixed by it ─────────────
#
# Two separate bugs stacked on top of each other, both around books.id /
# members.id (plain INTEGER columns — the real primary key is syncId TEXT):
#
# 1. READ-SIDE (fixed this session, in models/book.py, models/member.py,
#    models/issue_record.py, models/reservation.py): from_dict() on all four
#    models built a fresh dataclass without ever passing id=d.get("id"), so
#    EVERY object built from a database row — via get_books(), get_members(),
#    their paginated/search variants, everything — got id=0 in memory no
#    matter what was actually stored in that row. A pure round-trip bug:
#    save something with a real id, read it back, the id is gone.
#
# 2. WRITE-SIDE (NOT fixed here — see below): nothing ever gives a new
#    desktop-created Book()/Member() a real id to begin with. Confirmed
#    directly, with #1 already fixed:
#
#      b1, b2 = Book(title="One"), Book(title="Two")
#      db.save_book(b1); db.save_book(b2)
#      [b.id for b in db.get_books()]   # -> [0, 0], not [1, 2]
#
#    and it is worse than "desktop-only colleges are affected": Android
#    never uploads its own numeric id to Firestore at all (confirmed against
#    FirestoreService.kt — no "id" field in the book/member document shape),
#    so fixing #1 alone does not give a synced-down record a real id either.
#    Right now, #2 means EVERY college's books/members have id=0, full stop
#    — #1 was a real bug worth fixing regardless, but on its own it changes
#    nothing observable yet, because nothing today ever writes a non-zero id
#    for either fix to actually preserve.
#
# This script hit the combination the hard way: building synthetic Book/
# Member objects with no explicit id (matching exactly what BooksScreen's
# and MembersScreen's own "Add" dialogs do) put everything on "book 0 /
# member 0", and the fine-waiver scan below went quadratic (8,000 members
# each re-scanning the same 250,000-issue bucket instead of its own ~31) —
# a 300+ second hang, not a slow query. Explicitly passing id=i+1 when
# building the synthetic dataset below sidesteps #2 so this script measures
# real per-member cost rather than the worst case of everyone colliding.
#
# Real code already keys on `.id` for exactly this kind of join:
# members_screen.py's fines ledger (`i.memberId == m.id`), issue_return_
# screen.py's several `m.id == record.memberId` lookups, run_health_check()'s
# orphan/ghost/stuck-returns joins, and fine_waiver_screen.py's balance
# computation (fixed earlier this session). With #2 unfixed, every one of
# those currently matches every member/book against every other one, not
# just the right one, as soon as a second record of either kind exists in a
# college's database — which is every real database, not an edge case. It
# doesn't need "large data" to trigger, just a second Add Member click; it
# took a large synthetic run to actually *notice*, because manual QA
# naturally tends to test with one member, or never scrutinizes which
# member a stray fine landed on.
#
# Not fixed here: #2 needs a schema migration backfilling a real unique id
# for every existing zero-id row in every already-deployed college's
# database (the data is already affected today, not just future writes),
# touches every save path (save_book/save_member and their batch variants),
# and needs auditing every consumer of `.id` for an assumption that breaks
# once ids stop being uniformly 0 — a larger, separate change from "write a
# load test," flagged for a decision rather than made unilaterally.
