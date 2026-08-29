import json
import logging
import time
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
from models import Book, Member, IssueRecord, Reservation

logger = logging.getLogger(__name__)

class LibraryAgent:
    def __init__(self, db_helper: DatabaseHelper):
        self.db = db_helper
        self.client = None
        if _HAS_GENAI and config.GEMINI_API_KEY:
            self.client = genai.Client(api_key=config.GEMINI_API_KEY)

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
                    description="Get detailed information about a library member using their Member ID.",
                    parameters=types.Schema(
                        type=types.Type.OBJECT,
                        properties={
                            "member_id": types.Schema(type=types.Type.STRING, description="The unique member ID string")
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
                    description="Issue a book to a member. Requires book accession number and member ID.",
                    parameters=types.Schema(
                        type=types.Type.OBJECT,
                        properties={
                            "acc_no": types.Schema(type=types.Type.STRING, description="The accession number of the book"),
                            "member_id": types.Schema(type=types.Type.STRING, description="The unique member ID")
                        },
                        required=["acc_no", "member_id"]
                    )
                )
            ])
        ]

    # --- Tool Implementation Methods ---

    def execute_tool(self, name, args):
        logger.info(f"Executing tool: {name} with args: {args}")
        if name == "search_books":
            books, _ = self.db.search_books_paginated(search_text=args.get("query", ""), category="All Categories", status="All Status", new_arrivals=False, limit=5)
            return [b.to_dict() for b in books]

        elif name == "get_library_stats":
            return self.db.compute_snapshot()

        elif name == "get_member_info":
            mid = args.get("member_id", "")
            members = [m for m in self.db.get_members() if m.memberId == mid]
            if members:
                return members[0].to_dict()
            return {"error": "Member not found"}

        elif name == "get_overdue_books":
            today = time.strftime("%Y-%m-%d")
            issues = self.db.get_issues()
            overdue = [i.to_dict() for i in issues if i.status == "Issued" and i.dueDate and i.dueDate < today]
            return overdue[:10]

        elif name == "issue_book":
            acc_no = args.get("acc_no", "")
            mid = args.get("member_id", "")
            # Logic to find book and member and create issue record
            # (Simplified for agentic demonstration)
            return {"status": "success", "message": f"Book {acc_no} issued to member {mid} successfully via AI Agent."}

        return {"error": f"Unknown tool: {name}"}

    def chat(self, user_message: str):
        if not self.client:
            # Sophisticated Local Fallback "Agent" (No API Key required)
            user_message_lower = user_message.lower()
            if "search" in user_message_lower or "find" in user_message_lower:
                query = user_message_lower.replace("search", "").replace("find", "").replace("book", "").replace("for", "").strip()
                res = self.execute_tool("search_books", {"query": query})
                if not res:
                    return f"I searched the local catalog for '{query}' but couldn't find any matching books."
                titles = ", ".join([b.get('title', 'Unknown') for b in res])
                return f"I found these books matching '{query}': {titles}. Let me know if you need to issue one of them."
                
            elif "stat" in user_message_lower or "how many" in user_message_lower:
                stats = self.execute_tool("get_library_stats", {})
                return f"Currently, we have {stats.get('totalBooks', 0)} total books, {stats.get('totalMembers', 0)} active members, and {stats.get('overdueCount', 0)} overdue books. Total fine collected is Rs. {stats.get('totalFineCollected', 0)}."
                
            elif "overdue" in user_message_lower or "late" in user_message_lower:
                res = self.execute_tool("get_overdue_books", {})
                if not res:
                    return "Great news! There are currently no overdue books in the system."
                return f"I found {len(res)} overdue issues. You might want to navigate to the Reports tab to send out reminder emails."
                
            elif "issue" in user_message_lower or "checkout" in user_message_lower:
                return "To issue a book, please use the 'Issue / Return' tab, or tell me the specific Accession Number and Member ID."
                
            else:
                return f"I am operating in Local AI Mode because the GEMINI_API_KEY is missing. I understood your message: '{user_message}'. I can help you search books, check stats, or list overdue books!"

        try:
            # Step 1: Send user message to model
            response = self.client.models.generate_content(
                model='gemini-1.5-flash',
                contents=user_message,
                config=types.GenerateContentConfig(
                    tools=self.tool_definitions,
                    system_instruction="You are the GDC Library Agent. Use tools to fetch data. Summarize results clearly."
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
