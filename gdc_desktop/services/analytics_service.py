"""
services/analytics_service.py — Usage analytics computed from the local
SQLite mirror: which titles are actually earning their shelf space, which
members are heavy/lapsed readers, and which active loans are likely to go
overdue.

Deliberately NOT machine learning. This college has no historical usage data
yet (or very little), and there is nowhere on the free Firebase Spark plan to
train or serve a model even if there were. Every function here is a plain,
explainable aggregation or threshold rule over data the app already has —
the same standard reports_screen.py's stats already meet, just extended to
answer three questions it doesn't: which BOOKS are underused (not just
popular), how members differ beyond a raw borrow count, and which ACTIVE
loans need a reminder now rather than an aggregate overdue count after the
fact.

Kept dependency-free and pure (no Qt, no Firestore, no I/O) on purpose: it
can be exercised directly, without a display, with plain synthetic
Book/Member/IssueRecord objects — see the self-test at the bottom of this
file, run with `python services/analytics_service.py`.
"""
from __future__ import annotations

import time
from collections import defaultdict
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from typing import Dict, List, Optional


# ── Work identity ────────────────────────────────────────────────────────
#
# The book catalog is per-COPY (Book.accNo is an accession number — two
# physical copies of the same title are two separate Book rows), but
# IssueRecord denormalizes only bookTitle/bookIsbn onto itself, with no
# author field. So the one key both sides can agree on is: ISBN when
# present, otherwise the title alone, normalized. This is a known, accepted
# limitation — two different books that share an exact title and have no
# ISBN on file will be treated as one work. That is rare enough in a
# college library catalog to be worth the simplicity, and far better than
# either failing to match at all or fabricating an author to disambiguate.

def work_key(isbn: Optional[str], title: Optional[str]) -> str:
    isbn = (isbn or "").strip()
    if isbn:
        return f"isbn:{isbn}"
    return f"title:{(title or '').strip().lower()}"


# ── Book popularity / underuse ──────────────────────────────────────────

DEAD_STOCK_MONTHS = 6  # no issue in this many months (or ever) = dead stock
HIDDEN_GEM_MAX_COPIES = 2  # "low copies" for the hidden-gems check


@dataclass
class WorkStats:
    key: str
    title: str
    category: str
    copies: int
    issue_count: int
    last_issue_date: Optional[str]  # "YYYY-MM-DD", None if never issued

    @property
    def issues_per_copy(self) -> float:
        return self.issue_count / max(1, self.copies)


def _months_since(date_str: Optional[str], today: Optional[datetime] = None) -> Optional[float]:
    if not date_str:
        return None
    try:
        d = datetime.strptime(date_str, "%Y-%m-%d")
    except ValueError:
        return None
    today = today or datetime.now()
    return (today - d).days / 30.437


def rank_book_popularity(books: list, issues: list, today: Optional[datetime] = None) -> dict:
    """Group every book by work_key and return three ranked lists.

    `books` and `issues` are the plain lists database_helper.get_books()/
    get_issues() already return — real Book/IssueRecord dataclass instances,
    never dicts or tuples (that mismatch is what made recommendation_screen's
    old parser silently drop every real record; see its fix for the same
    reason spelled out at length).
    """
    by_key: Dict[str, WorkStats] = {}
    for b in books:
        if b.deleted:
            continue
        k = work_key(b.isbn, b.title)
        if k not in by_key:
            by_key[k] = WorkStats(key=k, title=b.title, category=b.category or "Uncategorized",
                                   copies=0, issue_count=0, last_issue_date=None)
        by_key[k].copies += 1

    for i in issues:
        if i.deleted:
            continue
        k = work_key(i.bookIsbn, i.bookTitle)
        stats = by_key.get(k)
        if stats is None:
            # Issue history for a book no longer in the catalog (weeded,
            # or deleted) — still real usage, worth keeping visible as a
            # zero-copy entry rather than discarding the history.
            stats = WorkStats(key=k, title=i.bookTitle or "(deleted book)", category="Uncategorized",
                               copies=0, issue_count=0, last_issue_date=None)
            by_key[k] = stats
        stats.issue_count += 1
        if i.issueDate and (stats.last_issue_date is None or i.issueDate > stats.last_issue_date):
            stats.last_issue_date = i.issueDate

    all_works = list(by_key.values())

    most_borrowed = sorted(all_works, key=lambda w: -w.issue_count)[:25]

    hidden_gems = sorted(
        (w for w in all_works if 0 < w.copies <= HIDDEN_GEM_MAX_COPIES and w.issue_count >= 3),
        key=lambda w: -w.issues_per_copy,
    )[:25]

    def is_dead_stock(w: WorkStats) -> bool:
        if w.copies == 0:
            return False  # not on the shelf at all — not a stocking decision
        months = _months_since(w.last_issue_date, today)
        return months is None or months >= DEAD_STOCK_MONTHS

    dead_stock = sorted(
        (w for w in all_works if is_dead_stock(w)),
        key=lambda w: (w.last_issue_date or ""),
    )[:50]

    return {"most_borrowed": most_borrowed, "hidden_gems": hidden_gems, "dead_stock": dead_stock,
            "total_works": len(all_works)}


# ── Member borrowing segmentation ───────────────────────────────────────
#
# Threshold-based (RFM-lite), not K-Means: at the scale of one college's
# membership (dozens to a few hundred), a clustering algorithm adds
# complexity without adding insight a librarian can't get from three plain
# numbers — how often, how recently, how reliably they return on time.

HEAVY_READER_MIN_ISSUES = 10
LAPSED_AFTER_DAYS = 180


@dataclass
class MemberStats:
    member_id: str
    name: str
    total_issues: int
    days_since_last_issue: Optional[int]
    late_return_rate: float  # 0.0-1.0 across their CLOSED loans only
    segment: str
    reliability: str


def segment_members(members: list, issues: list, today: Optional[datetime] = None) -> List[MemberStats]:
    today = today or datetime.now()
    by_member: Dict[str, list] = defaultdict(list)
    for i in issues:
        if i.deleted or not i.memberMemberId:
            continue
        by_member[i.memberMemberId].append(i)

    results = []
    for m in members:
        if m.deleted:
            continue
        their_issues = by_member.get(m.memberId, [])
        total = len(their_issues)

        last_date = max((i.issueDate for i in their_issues if i.issueDate), default=None)
        days_since = (today - datetime.strptime(last_date, "%Y-%m-%d")).days if last_date else None

        closed = [i for i in their_issues if i.status == "Returned" and i.returnDate]
        late = [i for i in closed if i.dueDate and i.returnDate > i.dueDate]
        late_rate = (len(late) / len(closed)) if closed else 0.0

        if total == 0:
            segment = "Never borrowed"
        elif days_since is not None and days_since > LAPSED_AFTER_DAYS:
            segment = "Lapsed"
        elif total >= HEAVY_READER_MIN_ISSUES:
            segment = "Heavy reader"
        else:
            segment = "Occasional"

        if not closed:
            reliability = "No history yet"
        elif late_rate == 0:
            reliability = "Reliable"
        elif late_rate < 0.34:
            reliability = "Occasionally late"
        else:
            reliability = "Needs reminders"

        results.append(MemberStats(
            member_id=m.memberId, name=m.name, total_issues=total,
            days_since_last_issue=days_since, late_return_rate=round(late_rate, 2),
            segment=segment, reliability=reliability,
        ))

    return sorted(results, key=lambda s: -s.total_issues)


# ── Overdue-risk flagging for ACTIVE loans ──────────────────────────────
#
# A transparent rule ladder, not a trained classifier: there is no labeled
# outcome history yet to train one against, and a librarian needs to be
# able to say WHY a loan is flagged, not just that a model said so.

@dataclass
class LoanRisk:
    sync_id: str
    member_id: str
    member_name: str
    book_title: str
    due_date: str
    days_until_due: int
    member_prior_late_count: int
    risk: str  # "High" | "Medium" | "Low"
    reason: str


def score_overdue_risk(issues: list, today: Optional[datetime] = None) -> List[LoanRisk]:
    today = today or datetime.now()
    today_str = today.strftime("%Y-%m-%d")

    # Prior late-return count per member, from their own closed loans —
    # excludes the active loan being scored, since a loan can't be evidence
    # against itself.
    prior_late: Dict[str, int] = defaultdict(int)
    for i in issues:
        if i.deleted or i.status != "Returned" or not i.returnDate or not i.dueDate:
            continue
        if i.returnDate > i.dueDate:
            prior_late[i.memberMemberId] += 1

    results = []
    for i in issues:
        if i.deleted or i.status != "Issued" or not i.dueDate:
            continue
        try:
            due = datetime.strptime(i.dueDate, "%Y-%m-%d")
        except ValueError:
            continue
        days_until_due = (due - today).days
        prior = prior_late.get(i.memberMemberId, 0)

        if days_until_due < 0:
            risk, reason = "High", f"Already {-days_until_due} day(s) overdue."
        elif days_until_due <= 3 and prior >= 2:
            risk, reason = "High", f"Due in {days_until_due}d and this member has returned {prior} loans late before."
        elif days_until_due <= 3 or prior >= 1:
            risk, reason = "Medium", (
                f"Due in {days_until_due}d." if prior == 0
                else f"This member has {prior} prior late return(s)."
            )
        else:
            risk, reason = "Low", "On track."

        results.append(LoanRisk(
            sync_id=i.syncId, member_id=i.memberMemberId, member_name=i.memberName,
            book_title=i.bookTitle, due_date=i.dueDate, days_until_due=days_until_due,
            member_prior_late_count=prior, risk=risk, reason=reason,
        ))

    order = {"High": 0, "Medium": 1, "Low": 2}
    return sorted(results, key=lambda r: (order[r.risk], r.days_until_due))


# ── Category stock, for the directorate rollup ──────────────────────────
#
# Reused by services/registry_service.py so the directorate can see which
# colleges are under-stocked in a given category WITHOUT reading a single
# book record directly — each college publishes only its own category
# counts, the same self-reporting pattern the rest of the registry uses.

def books_by_category(books: list) -> Dict[str, int]:
    counts: Dict[str, int] = {}
    for b in books:
        if b.deleted:
            continue
        cat = (b.category or "Uncategorized").strip() or "Uncategorized"
        counts[cat] = counts.get(cat, 0) + 1
    return counts


# ── Self-test ────────────────────────────────────────────────────────────
#
# No pytest dependency assumed on a librarian's machine — a plain assert
# script that exits non-zero on failure, matching scripts/nexlib_test.py's
# own house style elsewhere in this project.

if __name__ == "__main__":
    from types import SimpleNamespace as NS

    def book(isbn="", title="", category="Science", deleted=False):
        return NS(isbn=isbn, title=title, category=category, deleted=deleted)

    def issue(bookIsbn="", bookTitle="", memberMemberId="", memberName="", issueDate="",
              dueDate="", returnDate=None, status="Issued", deleted=False, syncId="x"):
        return NS(bookIsbn=bookIsbn, bookTitle=bookTitle, memberMemberId=memberMemberId,
                   memberName=memberName, issueDate=issueDate, dueDate=dueDate,
                   returnDate=returnDate, status=status, deleted=deleted, syncId=syncId)

    def member(memberId="", name="", deleted=False):
        return NS(memberId=memberId, name=name, deleted=deleted)

    today = datetime(2026, 9, 30)

    # ── work_key ──
    assert work_key("978-1", "Physics") == work_key("978-1", "Different Title")
    assert work_key("", "Same Title") == work_key(None, "same title")  # case-insensitive
    print("ok   work_key groups by ISBN, falls back to normalized title")

    # ── popularity / dead stock / hidden gems ──
    books = [
        book(isbn="A", title="Popular Book"),         # 1 copy, borrowed a lot
        book(isbn="B", title="Ignored Book"),          # 1 copy, never borrowed
        book(isbn="C", title="Old Favorite"),           # 1 copy, last borrowed 9 months ago
    ]
    issues = (
        [issue(bookIsbn="A", bookTitle="Popular Book", issueDate="2026-09-01") for _ in range(5)]
        + [issue(bookIsbn="C", bookTitle="Old Favorite", issueDate="2025-12-01")]
    )
    ranked = rank_book_popularity(books, issues, today=today)
    assert ranked["most_borrowed"][0].key == work_key("A", "Popular Book")
    assert ranked["hidden_gems"][0].key == work_key("A", "Popular Book")  # 1 copy, 5 issues
    dead_keys = {w.key for w in ranked["dead_stock"]}
    assert work_key("B", "Ignored Book") in dead_keys       # never issued
    assert work_key("C", "Old Favorite") in dead_keys       # 9 months since last issue
    assert work_key("A", "Popular Book") not in dead_keys   # issued this month
    print("ok   popularity ranking: most-borrowed, hidden gems, dead stock (never-issued AND stale)")

    # ── member segmentation ──
    members = [
        member(memberId="M1", name="Heavy Reader"),
        member(memberId="M2", name="Reliable Occasional"),
        member(memberId="M3", name="Chronically Late"),
        member(memberId="M4", name="Never Borrowed"),
    ]
    issues2 = (
        [issue(memberMemberId="M1", issueDate="2026-09-20", status="Issued", dueDate="2026-10-05")
         for _ in range(12)]
        + [issue(memberMemberId="M2", issueDate="2026-09-01", dueDate="2026-09-10",
                  returnDate="2026-09-09", status="Returned")]
        + [issue(memberMemberId="M3", issueDate="2026-08-01", dueDate="2026-08-10",
                  returnDate="2026-08-20", status="Returned")
           for _ in range(3)]
    )
    segs = {s.member_id: s for s in segment_members(members, issues2, today=today)}
    assert segs["M1"].segment == "Heavy reader"
    assert segs["M2"].reliability == "Reliable"
    assert segs["M3"].reliability == "Needs reminders"
    assert segs["M4"].segment == "Never borrowed"
    print("ok   member segmentation: heavy reader, reliable, needs-reminders, never-borrowed")

    # ── overdue risk ──
    issues3 = [
        issue(syncId="L1", memberMemberId="M3", memberName="Chronically Late", bookTitle="X",
              dueDate="2026-09-25", status="Issued"),  # already 5 days overdue -> High
        issue(syncId="L2", memberMemberId="M2", memberName="Reliable Occasional", bookTitle="Y",
              dueDate="2026-10-20", status="Issued"),  # far out, reliable history -> Low
        issue(syncId="L3", memberMemberId="M3", memberName="Chronically Late", bookTitle="Z",
              dueDate="2026-08-10", returnDate="2026-08-20", status="Returned"),  # prior-late evidence
    ]
    risks = {r.sync_id: r for r in score_overdue_risk(issues3, today=today)}
    assert risks["L1"].risk == "High" and "overdue" in risks["L1"].reason
    assert risks["L2"].risk == "Low"
    assert "L3" not in risks  # closed loans are evidence, not themselves scored
    print("ok   overdue risk: already-late flagged High, on-track flagged Low, closed loans excluded")

    # ── category rollup ──
    cats = books_by_category([book(category="Science"), book(category="Science"), book(category="Art")])
    assert cats == {"Science": 2, "Art": 1}
    print("ok   books_by_category rollup")

    print("\nAll analytics_service self-tests passed.")
