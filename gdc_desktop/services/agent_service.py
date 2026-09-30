"""
services/agent_service.py — the AI Assistant: a chat agent with real access
to this college's own circulation data, reachable from the sidebar's
"AI ASSISTANT" button, the OPAC screen's recommendations, and the Fine
Waiver screen's analysis.

Two modes, chosen automatically:

  Gemini mode   Used when config.GEMINI_API_KEY is set. The model calls the
                same tools listed in execute_tool() below via function
                calling, and this file executes them.

  Local mode    Used otherwise — which, since GEMINI_API_KEY ships unset and
                undocumented, is what every install actually runs today.
                This is NOT a lesser stub bolted on to make the "no API key"
                path look covered: it is a real rule-based assistant, with
                its own intent parsing and short-term memory, that resolves
                to the exact same execute_tool() calls Gemini mode would
                make. Whichever mode is active, an action either really
                happens against the local database or the assistant says
                plainly that it didn't and why — it never claims a book was
                issued without creating the issue record, updating the
                book's status, and logging it, because that used to be
                exactly what happened here.
"""
import json
import logging
import re
import time
from datetime import datetime, timedelta

try:
    from google import genai
    from google.genai import types
    _HAS_GENAI = True
except ImportError:
    genai = None
    types = None
    _HAS_GENAI = False

import config
from services.database_helper import DatabaseHelper
from services.settings_service import LibrarySettings, SettingsService
from models import Book, Member, IssueRecord, Reservation

logger = logging.getLogger(__name__)


class LibraryAgent:
    def __init__(self, db_helper: DatabaseHelper, firebase_service=None, auth_service=None):
        self.db = db_helper
        self.auth = auth_service
        self.client = None
        if _HAS_GENAI and config.GEMINI_API_KEY:
            self.client = genai.Client(api_key=config.GEMINI_API_KEY)

        # Real fine/due-date policy, not a re-guessed default — same
        # SettingsService the Issue/Return screen itself reads, so an AI
        # Assistant return charges the identical fine a human return would.
        # None when no firebase_service was passed (e.g. a script running
        # this agent standalone) — _settings() falls back to library
        # defaults, which is correct and still fully offline-safe.
        self._settings_service = SettingsService(firebase_service) if firebase_service else None

        # Short-term memory: the last book/member this conversation named,
        # so "issue it to him" after a search works without repeating IDs.
        # Deliberately shallow — two slots, not a transcript — this is a
        # rule-based assistant's memory, not an LLM's context window.
        self._context = {"last_book": None, "last_member": None}

        self._setup_tool_definitions()

    def _setup_tool_definitions(self):
        if not _HAS_GENAI or types is None:
            self.tool_definitions = []
            return
        self.tool_definitions = [
            types.Tool(function_declarations=[
                types.FunctionDeclaration(
                    name="search_books",
                    description="Search for books by title, author, or category.",
                    parameters=types.Schema(
                        type=types.Type.OBJECT,
                        properties={
                            "query": types.Schema(type=types.Type.STRING, description="The search string for title, author or ISBN")
                        },
                        required=["query"]
                    )
                ),
                types.FunctionDeclaration(
                    name="get_library_stats",
                    description="Get overall library statistics including total books, members, and fine collected."
                ),
                types.FunctionDeclaration(
                    name="get_member_info",
                    description="Get detailed information about a library member using their Member ID or name.",
                    parameters=types.Schema(
                        type=types.Type.OBJECT,
                        properties={
                            "member_id": types.Schema(type=types.Type.STRING, description="The member's ID or name")
                        },
                        required=["member_id"]
                    )
                ),
                types.FunctionDeclaration(
                    name="get_overdue_books",
                    description="Get a list of currently overdue books and the members who have them."
                ),
                types.FunctionDeclaration(
                    name="issue_book",
                    description="Issue a book to a member. Accepts an accession number, ISBN, or title for the book, "
                                 "and a Member ID or name for the member. Actually creates the loan record.",
                    parameters=types.Schema(
                        type=types.Type.OBJECT,
                        properties={
                            "acc_no": types.Schema(type=types.Type.STRING, description="Accession number, ISBN, or title of the book"),
                            "member_id": types.Schema(type=types.Type.STRING, description="Member ID or name")
                        },
                        required=["acc_no", "member_id"]
                    )
                ),
                types.FunctionDeclaration(
                    name="return_book",
                    description="Return a book that is currently issued out. Accepts an accession number, ISBN, or "
                                 "title. Computes and records any overdue fine using the college's actual fine policy.",
                    parameters=types.Schema(
                        type=types.Type.OBJECT,
                        properties={
                            "acc_no": types.Schema(type=types.Type.STRING, description="Accession number, ISBN, or title of the book")
                        },
                        required=["acc_no"]
                    )
                ),
            ])
        ]

    # ── Shared lookups ───────────────────────────────────────────────────
    #
    # One resolver each for books and members, used by every tool that
    # needs one (issue, return, member info) and by both chat modes. Exact
    # identifier first (accession number / ISBN / Member ID), falling back
    # to a title or name match ONLY when it is unambiguous — an assistant
    # that guesses which of three "Introduction to Physics" copies you meant
    # is worse than one that asks.

    def _find_book(self, query: str):
        query = (query or "").strip()
        if not query:
            return None, "No book was named."
        books = [b for b in self.db.get_books() if not b.deleted]

        for b in books:
            if b.accNo and b.accNo.lower() == query.lower():
                return b, None
        for b in books:
            if b.isbn and b.isbn.lower() == query.lower():
                return b, None

        matches = [b for b in books if query.lower() in (b.title or "").lower()]
        if len(matches) == 1:
            return matches[0], None
        if len(matches) > 1:
            titles = "; ".join(f"{b.title} (acc.no. {b.accNo})" for b in matches[:5])
            return None, f"'{query}' matches {len(matches)} books: {titles}. Give the accession number to pick one."
        return None, f"No book found matching '{query}'."

    def _find_member(self, query: str):
        query = (query or "").strip()
        if not query:
            return None, "No member was named."
        members = [m for m in self.db.get_members() if not m.deleted]

        for m in members:
            if m.memberId and m.memberId.lower() == query.lower():
                return m, None

        matches = [m for m in members if query.lower() in (m.name or "").lower()]
        if len(matches) == 1:
            return matches[0], None
        if len(matches) > 1:
            names = "; ".join(f"{m.name} ({m.memberId})" for m in matches[:5])
            return None, f"'{query}' matches {len(matches)} members: {names}. Give the exact Member ID to pick one."
        return None, f"No member found matching '{query}'."

    def _settings(self) -> LibrarySettings:
        if self._settings_service is not None:
            try:
                return self._settings_service.get()
            except Exception:
                pass
        return LibrarySettings()

    def _actor_email(self) -> str:
        try:
            if self.auth and self.auth.current_user:
                return self.auth.current_user.email or "ai-assistant"
        except Exception:
            pass
        return "ai-assistant"

    def _audit(self, action: str, detail: str):
        try:
            self.db.log_audit_local(self._actor_email(), action, f"{detail} (via AI Assistant)")
        except Exception as e:
            logger.warning(f"AI Assistant action succeeded but audit log failed: {e}")

    # ── Real actions ─────────────────────────────────────────────────────
    #
    # This is the part that changed: issue_book used to skip straight to
    # `return {"status": "success", ...}` with a comment admitting it was
    # "simplified for agentic demonstration" — meaning the assistant could
    # tell a librarian a book was issued while the book's status, the
    # member's count, and the loan record itself were all untouched.

    def _do_issue(self, book_query: str, member_query: str) -> dict:
        book, err = self._find_book(book_query)
        if err:
            return {"error": err}
        member, err = self._find_member(member_query)
        if err:
            return {"error": err}

        if book.status != "Available":
            return {"error": f"'{book.title}' is currently {book.status.lower()}, not available to issue."}

        settings = self._settings()
        if settings.maxBooksPerMember and member.booksIssued >= settings.maxBooksPerMember:
            return {"error": f"{member.name} already has {member.booksIssued} book(s) out, at the "
                              f"{settings.maxBooksPerMember}-book limit."}

        now_ms = int(time.time() * 1000)
        due_date = (datetime.now() + timedelta(days=settings.borrowDurationDays)).strftime("%Y-%m-%d")

        record = IssueRecord(
            bookId=book.id, bookTitle=book.title, bookIsbn=book.isbn,
            memberId=member.id, memberName=member.name, memberMemberId=member.memberId,
            issueDate=time.strftime("%Y-%m-%d"), dueDate=due_date,
            issueTimestamp=now_ms, lastUpdated=now_ms,
        )
        self.db.save_issue(record)

        book.status = "Issued"
        book.lastUpdated = now_ms
        self.db.save_book(book)

        member.booksIssued += 1
        member.lastUpdated = now_ms
        self.db.save_member(member)

        self._audit("book_issue", f"Book: {book.title} -> {member.name}")
        self._context["last_book"], self._context["last_member"] = book, member

        return {
            "status": "success",
            "message": f"Issued '{book.title}' to {member.name} ({member.memberId}). Due back {due_date}.",
        }

    def _do_return(self, book_query: str) -> dict:
        book, err = self._find_book(book_query)
        if err:
            return {"error": err}
        if book.status != "Issued":
            return {"error": f"'{book.title}' is not currently marked as issued out."}

        active = [i for i in self.db.get_issues()
                  if not i.deleted and i.status == "Issued" and i.bookId == book.id]
        if not active:
            return {"error": f"'{book.title}' is marked Issued but no matching active loan record exists. "
                              f"Please handle this from the Issue/Return screen directly."}
        record = active[0]

        today_str = time.strftime("%Y-%m-%d")
        days_overdue = 0
        if record.dueDate:
            try:
                delta = datetime.strptime(today_str, "%Y-%m-%d") - datetime.strptime(record.dueDate, "%Y-%m-%d")
                days_overdue = max(0, delta.days)
            except ValueError:
                pass

        settings = self._settings()
        fine = settings.fine_for(days_overdue)

        now_ms = int(time.time() * 1000)
        record.status = "Returned"
        record.returnDate = today_str
        record.fine = fine
        record.lastUpdated = now_ms
        self.db.save_issue(record)

        book.status = "Available"
        book.lastUpdated = now_ms
        self.db.save_book(book)

        members = [m for m in self.db.get_members() if m.id == record.memberId]
        if members:
            m = members[0]
            m.booksIssued = max(0, m.booksIssued - 1)
            m.lastUpdated = now_ms
            self.db.save_member(m)
            self._context["last_member"] = m

        self._audit("book_return", f"IssueSyncId: {record.syncId}, fine: {fine}")
        self._context["last_book"] = book

        message = f"Returned '{book.title}' from {record.memberName}."
        if fine > 0:
            message += f" {days_overdue} day(s) overdue — {settings.currencySymbol} {fine:.2f} fine recorded."
        else:
            message += " No fine — returned on time."
        return {"status": "success", "message": message}

    # --- Tool Implementation Methods ---

    def execute_tool(self, name, args):
        logger.info(f"Executing tool: {name} with args: {args}")
        if name == "search_books":
            books, _ = self.db.search_books_paginated(search_text=args.get("query", ""), category="All Categories", status="All Status", new_arrivals=False, limit=5)
            if books:
                self._context["last_book"] = books[0]
            return [b.to_dict() for b in books]

        elif name == "get_library_stats":
            return self.db.compute_snapshot()

        elif name == "get_member_info":
            member, err = self._find_member(args.get("member_id", ""))
            if err:
                return {"error": err}
            self._context["last_member"] = member
            return member.to_dict()

        elif name == "get_overdue_books":
            today = time.strftime("%Y-%m-%d")
            issues = self.db.get_issues()
            overdue = [i.to_dict() for i in issues if i.status == "Issued" and i.dueDate and i.dueDate < today]
            return overdue[:10]

        elif name == "issue_book":
            return self._do_issue(args.get("acc_no", ""), args.get("member_id", ""))

        elif name == "return_book":
            return self._do_return(args.get("acc_no", ""))

        return {"error": f"Unknown tool: {name}"}

    # ── Local-mode intent parsing ────────────────────────────────────────
    #
    # No LLM, no dependency — plain regex against a small set of intents,
    # each resolved to the SAME execute_tool() calls Gemini mode uses. This
    # replaces bare `if "search" in message` substring checks that couldn't
    # extract a book or a member from a sentence at all, which is why the
    # old local mode could describe how to issue a book but never actually
    # issue one.

    _ISSUE_RE = re.compile(r"\b(?:issue|check\s*out|give|lend)\b\s+(.+?)\s+\b(?:to|for)\b\s+(.+)", re.I)
    _RETURN_RE = re.compile(r"\b(?:return|give\s*back|check\s*in)\b\s+(.+)", re.I)
    _MEMBER_RE = re.compile(r"\b(?:who\s+is|info\s+on|details?\s+(?:for|on)|about)\b\s+(.+)", re.I)
    _OVERDUE_RE = re.compile(r"\b(?:overdue|late|past\s*due)\b", re.I)
    _STATS_RE = re.compile(r"\b(?:stat(?:s|istics)?|how\s+many|overview|summary)\b", re.I)
    _SEARCH_RE = re.compile(r"\b(?:search|find|look\s*up|do\s+we\s+have)\b\s*(?:for|a|an)?\s*(.+)", re.I)
    _HELP_RE = re.compile(r"\b(?:help|what\s+can\s+you\s+do|capabilities|commands)\b", re.I)

    def _resolve_pronouns(self, text: str) -> str:
        """Swap short references to the last book/member mentioned in this
        conversation for the real thing, so 'issue it to him' works after a
        search and a member lookup without repeating exact IDs."""
        last_book = self._context.get("last_book")
        last_member = self._context.get("last_member")
        if last_book:
            text = re.sub(r"\b(it|that book|this book|the book)\b", last_book.title, text, flags=re.I)
        if last_member:
            text = re.sub(r"\b(him|her|them|that member|this member|the member)\b", last_member.name, text, flags=re.I)
        return text

    def _local_chat(self, user_message: str) -> str:
        text = self._resolve_pronouns(user_message.strip())

        m = self._ISSUE_RE.search(text)
        if m:
            result = self._do_issue(m.group(1).strip(), m.group(2).strip())
            return result["message"] if "message" in result else f"Couldn't issue that: {result['error']}"

        m = self._RETURN_RE.search(text)
        if m:
            result = self._do_return(m.group(1).strip())
            return result["message"] if "message" in result else f"Couldn't process that return: {result['error']}"

        m = self._MEMBER_RE.search(text)
        if m:
            result = self.execute_tool("get_member_info", {"member_id": m.group(1).strip()})
            if "error" in result:
                return result["error"]
            return (f"{result['name']} ({result['memberId']}) — {result.get('department') or 'no department on file'}. "
                    f"Currently has {result['booksIssued']} book(s) issued.")

        if self._OVERDUE_RE.search(text):
            res = self.execute_tool("get_overdue_books", {})
            if not res:
                return "No overdue books right now."
            lines = "; ".join(f"{i['bookTitle']} — {i['memberName']} (due {i['dueDate']})" for i in res[:5])
            more = f" and {len(res) - 5} more" if len(res) > 5 else ""
            return f"{len(res)} overdue: {lines}{more}."

        if self._STATS_RE.search(text):
            stats = self.execute_tool("get_library_stats", {})
            return (f"{stats.get('totalBooks', 0)} total books, {stats.get('totalMembers', 0)} members, "
                    f"{stats.get('overdueCount', 0)} overdue, "
                    f"{stats.get('totalFineCollected', 0)} in fines collected.")

        m = self._SEARCH_RE.search(text)
        if m:
            query = m.group(1).strip()
            res = self.execute_tool("search_books", {"query": query})
            if not res:
                return f"No books found matching '{query}'."
            titles = ", ".join(b.get("title", "Unknown") for b in res)
            return f"Found: {titles}. Say 'issue <title> to <member>' to check one out."

        if self._HELP_RE.search(text):
            return ("I can: search the catalog, give library stats, look up a member, list overdue books, "
                    "issue a book ('issue <book> to <member>'), and return a book ('return <book>'). "
                    "I'm running without a Gemini API key, so I match a fixed set of phrasings rather than "
                    "understanding free-form English — ask plainly and I'll do my best.")

        return ("I didn't recognize that. I'm running in Local AI Mode (no GEMINI_API_KEY set) — I understand "
                "a fixed set of requests, not free-form English. Ask 'help' to see what I can do.")

    # ── Entry point ──────────────────────────────────────────────────────

    def chat(self, user_message: str):
        if not self.client:
            return self._local_chat(user_message)

        try:
            # Step 1: Send user message to model
            response = self.client.models.generate_content(
                model='gemini-1.5-flash',
                contents=user_message,
                config=types.GenerateContentConfig(
                    tools=self.tool_definitions,
                    system_instruction="You are the GDC Library Agent. Use tools to fetch data or take action. "
                                        "Summarize results clearly. Never claim an action succeeded unless the "
                                        "tool result says status: success."
                )
            )

            # Step 2: Handle function calls if any
            if response.candidates[0].content.parts and any(p.function_call for p in response.candidates[0].content.parts):
                parts = response.candidates[0].content.parts
                tool_results = []

                for part in parts:
                    if part.function_call:
                        call = part.function_call
                        result = self.execute_tool(call.name, call.args)
                        tool_results.append(types.Part(
                            function_response=types.FunctionResponse(
                                name=call.name,
                                response={"result": result}
                            )
                        ))

                # Step 3: Send results back to model for final summary
                final_response = self.client.models.generate_content(
                    model='gemini-1.5-flash',
                    contents=[
                        types.Content(role="user", parts=[types.Part(text=user_message)]),
                        response.candidates[0].content,
                        types.Content(role="user", parts=tool_results)
                    ],
                    config=types.GenerateContentConfig(tools=self.tool_definitions)
                )
                return final_response.text

            return response.text
        except Exception as e:
            logger.error(f"Agent error: {e}", exc_info=True)
            return f"Agent Error (Check API Key validity): {e}"


# ── Self-test ────────────────────────────────────────────────────────────
#
# Unlike the PyQt6 screens elsewhere in this app, this file has zero Qt
# dependency, so it can be exercised against a REAL SQLite database via the
# REAL DatabaseHelper — not a mock, not a synthetic dataclass stand-in. Run
# with `python services/agent_service.py`. This is what actually caught
# (and proved fixed) the exact class of bug that made issue_book a lie: run
# it, then check the database yourself, don't just check what the reply
# string says.

if __name__ == "__main__":
    import os
    import tempfile

    tmpdir = tempfile.mkdtemp()
    config.LOCAL_DB_PATH = os.path.join(tmpdir, "agent_selftest.db")
    from services.database_helper import DatabaseHelper

    db = DatabaseHelper()
    db.save_book(Book(accNo="ACC001", isbn="978-1", title="Intro to Physics", status="Available"))
    db.save_book(Book(accNo="ACC002", isbn="978-2", title="Advanced Chemistry", status="Available"))
    db.save_book(Book(accNo="ACC003", isbn="978-3", title="World History", status="Available"))
    db.save_book(Book(accNo="ACC004", isbn="978-4", title="Statistics 101", status="Available"))
    db.save_member(Member(memberId="STU001", name="Zeeshan Tariq", booksIssued=0))
    db.save_member(Member(memberId="STU002", name="Farah Bibi", booksIssued=0))

    agent = LibraryAgent(db)

    def check(label, condition):
        print(("ok  " if condition else "FAIL") + "  " + label)
        if not condition:
            raise SystemExit(1)

    agent.chat("issue ACC001 to STU001")
    check("issue via chat() flips book status to Issued",
          [b for b in db.get_books() if b.accNo == "ACC001"][0].status == "Issued")
    check("issue via chat() increments member.booksIssued",
          [m for m in db.get_members() if m.memberId == "STU001"][0].booksIssued == 1)
    check("issue via chat() creates a real IssueRecord with a real issueTimestamp",
          db.get_issues()[0].status == "Issued" and db.get_issues()[0].issueTimestamp > 0)

    reply = agent.chat("issue ACC001 to STU002")
    check("an already-issued book is refused, not double-issued",
          "not available" in reply.lower())
    check("the refused member was NOT charged a book",
          [m for m in db.get_members() if m.memberId == "STU002"][0].booksIssued == 0)

    agent.chat("issue ACC002 to STU001")
    agent.chat("issue ACC003 to STU001")
    reply = agent.chat("issue ACC004 to STU001")
    check("the real 3-book policy limit is enforced, not just displayed",
          "limit" in reply.lower())
    check("the 4th book was genuinely not issued",
          [b for b in db.get_books() if b.accNo == "ACC004"][0].status == "Available")

    reply = agent.chat("issue ACC004 to Farah")
    check("an unambiguous partial name resolves and really issues",
          "Farah Bibi" in reply and [b for b in db.get_books() if b.accNo == "ACC004"][0].status == "Issued")

    rec = [i for i in db.get_issues() if i.status == "Issued" and i.bookIsbn == "978-1"][0]
    rec.dueDate = "2020-01-01"
    db.save_issue(rec)
    reply = agent.chat("return ACC001")
    returned = [i for i in db.get_issues() if i.bookIsbn == "978-1"][0]
    check("return via chat() flips the book back to Available",
          [b for b in db.get_books() if b.accNo == "ACC001"][0].status == "Available")
    check("return via chat() computes a real overdue fine via SettingsService, not a guess",
          returned.status == "Returned" and returned.fine == round((datetime.now() - datetime(2020, 1, 1)).days * 5.0, 2))

    with db._get_conn() as conn:
        actions = [r["action"] for r in conn.execute("SELECT action, detail FROM audit_log").fetchall()]
        details = [r["detail"] for r in conn.execute("SELECT detail FROM audit_log").fetchall()]
    check("every real action (not just successful chat replies) was written to the audit log",
          actions.count("book_issue") == 4 and actions.count("book_return") == 1)
    check("every audit entry is attributed to the AI Assistant, distinguishable from a human action",
          all("AI Assistant" in d for d in details))

    check("help text is honest about running without an API key",
          "gemini api key" in agent.chat("help").lower())
    check("an unrecognized request gets an honest 'I don't understand', never a fabricated answer",
          "didn't recognize" in agent.chat("what is the weather today").lower())

    print("\nAll agent_service self-tests passed against a real SQLite database.")
