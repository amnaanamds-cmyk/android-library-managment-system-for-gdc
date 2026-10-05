// lib/agent-tools.ts
//
// Tool definitions + execution for the directorate AI agent (chat and the
// nightly briefing both use these). Every tool runs the exact same pure
// analytics functions the dashboard pages themselves already use — no
// separate "AI version" of district rollups or compliance scoring to drift
// from the real one. Tools operate on a DirectorateSnapshot[] the caller
// already fetched client-side (via useDirectorateNetwork(), same as every
// dashboard page) and sent in the request body; this route never reads
// Firestore itself. See verify-auth.ts for why: a server-side Firestore
// read would need a real Admin SDK service account this deployment does
// not have configured, and operating only on data the caller legitimately
// already has means there is nothing more sensitive to leak here than the
// dashboard pages already show the same signed-in user.

import Anthropic from "@anthropic-ai/sdk";
import {
  rankBy,
  networkAverage,
  scoreCompliance,
  computeAlerts,
  rollupByDistrict,
  categoryNetworkTotals,
  rankByCategory,
  booksAvailablePct,
  RankAccessor,
} from "./analytics";
import { DirectorateSnapshot } from "./directorate";

export const AGENT_TOOLS: Anthropic.Beta.BetaTool[] = [
  {
    name: "get_network_totals",
    description:
      "Total books, members, active loans, overdue loans, reservations, and outstanding fines across every college in the network.",
    input_schema: { type: "object", properties: {}, additionalProperties: false },
  },
  {
    name: "rank_colleges",
    description:
      "Rank every college by one metric: raw book count, active loans, member count, or the percentage of books currently available (not out on loan).",
    input_schema: {
      type: "object",
      properties: {
        metric: {
          type: "string",
          enum: ["booksCount", "activeLoans", "membersCount", "availablePct"],
          description: "Which metric to rank by.",
        },
      },
      required: ["metric"],
      additionalProperties: false,
    },
  },
  {
    name: "get_alerts",
    description:
      "Data-quality and operational alerts across the network — colleges that have never synced, gone stale, report zero books, have no contact info, or have a high overdue rate.",
    input_schema: { type: "object", properties: {}, additionalProperties: false },
  },
  {
    name: "get_compliance_scores",
    description:
      "Per-college compliance scorecard (0-100): has ever synced, synced within 48h, contact details on file, overdue ratio under 20%.",
    input_schema: { type: "object", properties: {}, additionalProperties: false },
  },
  {
    name: "get_district_rollup",
    description: "Aggregate counts (colleges, books, members, active loans, overdue, reporting) grouped by district.",
    input_schema: { type: "object", properties: {}, additionalProperties: false },
  },
  {
    name: "get_category_stock",
    description:
      "Network-wide book stock by subject category (self-reported per college), or — when a category is given — which colleges have the most/fewest copies of it.",
    input_schema: {
      type: "object",
      properties: {
        category: {
          type: "string",
          description: "Optional: a specific category to rank colleges by. Omit to get totals across all categories.",
        },
      },
      additionalProperties: false,
    },
  },
];

const RANK_ACCESSORS: Record<string, RankAccessor> = {
  booksCount: "booksCount",
  activeLoans: "activeLoans",
  membersCount: "membersCount",
  availablePct: booksAvailablePct,
};

export function executeAgentTool(
  name: string,
  input: Record<string, unknown>,
  colleges: DirectorateSnapshot[],
): unknown {
  switch (name) {
    case "get_network_totals": {
      const approved = colleges; // caller already filtered to approved colleges, same as useDirectorateNetwork()
      return {
        colleges: approved.length,
        books: approved.reduce((n, c) => n + c.booksCount, 0),
        ebooks: approved.reduce((n, c) => n + c.ebooksCount, 0),
        members: approved.reduce((n, c) => n + c.membersCount, 0),
        activeLoans: approved.reduce((n, c) => n + c.activeLoans, 0),
        overdue: approved.reduce((n, c) => n + c.overdueCount, 0),
        reservations: approved.reduce((n, c) => n + c.reservationsCount, 0),
        finesOutstanding: approved.reduce((n, c) => n + c.finesOutstanding, 0),
      };
    }
    case "rank_colleges": {
      const metric = String(input.metric ?? "booksCount");
      const accessor = RANK_ACCESSORS[metric];
      if (!accessor) return { error: `Unknown metric '${metric}'.` };
      return {
        networkAverage: networkAverage(colleges, accessor),
        ranked: rankBy(colleges, accessor),
      };
    }
    case "get_alerts":
      return computeAlerts(colleges);
    case "get_compliance_scores":
      return colleges.map(scoreCompliance);
    case "get_district_rollup":
      return rollupByDistrict(colleges);
    case "get_category_stock": {
      const category = typeof input.category === "string" ? input.category.trim() : "";
      if (category) return rankByCategory(colleges, category);
      return categoryNetworkTotals(colleges);
    }
    default:
      return { error: `Unknown tool: ${name}` };
  }
}
