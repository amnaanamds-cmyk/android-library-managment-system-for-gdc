// lib/agent-tools.ts
//
// Tool definitions + execution for this college's AI library assistant.
// Every tool reads from the same raw book/member/issue/reservation arrays
// every dashboard page already gets from useTenantCollection() — no
// separate "AI version" of the catalogue or loan ledger to drift from the
// real one. Tools operate on arrays the caller already fetched client-side
// (Firestore's own security rules already scoped those reads to this
// signed-in user's institution) and sent in the request body; the API
// route never reads Firestore itself — see verify-auth.ts for why.

import Anthropic from "@anthropic-ai/sdk";
import { BookRecord, MemberRecord, IssueRecord, isActiveIssue, isOverdue, overdueDays } from "./schema";

export const AGENT_TOOLS: Anthropic.Beta.BetaTool[] = [
  {
    name: "get_library_stats",
    description:
      "Totals for this college's library: book count, e-book count, member count, active loans, overdue loans, reservations, and outstanding fines.",
    input_schema: { type: "object", properties: {}, additionalProperties: false },
  },
  {
    name: "search_catalog",
    description:
      "Search this college's book catalogue by title, author, ISBN, accession number, or category. Optionally filter to only Available or only Issued copies.",
    input_schema: {
      type: "object",
      properties: {
        query: { type: "string", description: "Text to search for in title, author, isbn, accNo, or category." },
        status: {
          type: "string",
          enum: ["Available", "Issued"],
          description: "Optional: restrict to copies with this status.",
        },
        limit: { type: "integer", description: "Max results to return (default 20, max 50)." },
      },
      required: ["query"],
      additionalProperties: false,
    },
  },
  {
    name: "get_overdue_loans",
    description: "Currently overdue loans, sorted by how many days overdue, worst first.",
    input_schema: {
      type: "object",
      properties: {
        limit: { type: "integer", description: "Max results to return (default 20, max 100)." },
      },
      additionalProperties: false,
    },
  },
  {
    name: "find_member",
    description:
      "Find a member by name, member ID, email, or department, and summarize their current and past loans.",
    input_schema: {
      type: "object",
      properties: {
        query: { type: "string", description: "Text to search for in name, memberId, email, or department." },
      },
      required: ["query"],
      additionalProperties: false,
    },
  },
  {
    name: "get_most_borrowed_books",
    description: "The books with the most loans recorded (active + returned), most-borrowed first.",
    input_schema: {
      type: "object",
      properties: {
        limit: { type: "integer", description: "Max results to return (default 10, max 50)." },
      },
      additionalProperties: false,
    },
  },
];

export interface TenantData {
  books: BookRecord[];
  members: MemberRecord[];
  issues: IssueRecord[];
  reservations: unknown[];
  ebooks: unknown[];
}

function clampLimit(raw: unknown, fallback: number, max: number): number {
  const n = Number(raw);
  if (!Number.isFinite(n) || n <= 0) return fallback;
  return Math.min(Math.floor(n), max);
}

export function executeAgentTool(name: string, input: Record<string, unknown>, data: TenantData): unknown {
  const liveBooks = data.books.filter((b) => !b.deleted);
  const liveMembers = data.members.filter((m) => !m.deleted);
  const liveIssues = data.issues.filter((i) => !i.deleted);

  switch (name) {
    case "get_library_stats": {
      const activeIssues = liveIssues.filter(isActiveIssue);
      const overdueIssues = liveIssues.filter((i) => isOverdue(i));
      // Same basis as dashboard/page.tsx: fines are only ever assessed at
      // return time, so the real "outstanding" figure sums returned loans
      // with a fine, not active ones (which are always ~0 for that reason).
      const finesOutstanding = liveIssues
        .filter((i) => i.status === "Returned" && (Number(i.fine) || 0) > 0)
        .reduce((sum, i) => sum + (Number(i.fine) || 0), 0);
      return {
        totalBooks: liveBooks.length,
        totalEbooks: data.ebooks.length,
        totalMembers: liveMembers.length,
        activeLoans: activeIssues.length,
        overdueLoans: overdueIssues.length,
        reservations: data.reservations.length,
        finesOutstanding,
      };
    }
    case "search_catalog": {
      const query = String(input.query ?? "").trim().toLowerCase();
      if (!query) return { error: "query is required." };
      const status = typeof input.status === "string" ? input.status : null;
      const limit = clampLimit(input.limit, 20, 50);
      const matches = liveBooks.filter((b) => {
        if (status && b.status !== status) return false;
        const haystack = [b.title, b.author, b.isbn, b.accNo, b.category].filter(Boolean).join(" ").toLowerCase();
        return haystack.includes(query);
      });
      return {
        total: matches.length,
        results: matches.slice(0, limit).map((b) => ({
          title: b.title,
          author: b.author,
          isbn: b.isbn,
          accNo: b.accNo,
          category: b.category,
          callNumber: b.callNumber,
          status: b.status,
        })),
      };
    }
    case "get_overdue_loans": {
      const limit = clampLimit(input.limit, 20, 100);
      const overdue = liveIssues
        .filter((i) => isOverdue(i))
        .map((i) => ({
          bookTitle: i.bookTitle,
          memberName: i.memberName,
          memberMemberId: i.memberMemberId,
          dueDate: i.dueDate,
          daysOverdue: overdueDays(i),
        }))
        .sort((a, b) => b.daysOverdue - a.daysOverdue);
      return { total: overdue.length, results: overdue.slice(0, limit) };
    }
    case "find_member": {
      const query = String(input.query ?? "").trim().toLowerCase();
      if (!query) return { error: "query is required." };
      const matches = liveMembers.filter((m) => {
        const haystack = [m.name, m.memberId, m.email, m.department].filter(Boolean).join(" ").toLowerCase();
        return haystack.includes(query);
      });
      return {
        total: matches.length,
        results: matches.slice(0, 10).map((m) => {
          const theirIssues = liveIssues.filter(
            (i) => i.memberId === m.id || (m.memberId && i.memberMemberId === m.memberId),
          );
          return {
            name: m.name,
            memberId: m.memberId,
            department: m.department,
            status: m.status,
            activeLoans: theirIssues.filter(isActiveIssue).length,
            totalLoansEver: theirIssues.length,
            currentlyHeld: theirIssues.filter(isActiveIssue).map((i) => i.bookTitle),
          };
        }),
      };
    }
    case "get_most_borrowed_books": {
      const limit = clampLimit(input.limit, 10, 50);
      const counts = new Map<string, { title: string; author?: string; count: number }>();
      for (const i of liveIssues) {
        const key = i.bookIsbn || i.bookTitle || String(i.bookId ?? "");
        if (!key) continue;
        const existing = counts.get(key);
        if (existing) {
          existing.count += 1;
        } else {
          counts.set(key, { title: i.bookTitle || "(untitled)", count: 1 });
        }
      }
      const ranked = [...counts.values()].sort((a, b) => b.count - a.count).slice(0, limit);
      return { results: ranked };
    }
    default:
      return { error: `Unknown tool: ${name}` };
  }
}
