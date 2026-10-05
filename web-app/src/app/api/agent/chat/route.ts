// app/api/agent/chat/route.ts
//
// Server-side only — this is the one place in web-app allowed to hold
// ANTHROPIC_API_KEY. Every page in this app is a client component talking
// to Firestore directly; this route is the single exception, and exists
// only because an API key must never reach the browser.
//
// Manual agentic tool-use loop (see the claude-api skill's tool-use.md)
// over lib/agent-tools.ts's real catalogue/member/loan tools, operating on
// this college's own tenant data the client already fetched and sent in
// the body — see lib/agent-tools.ts's header for why that's the right
// trust boundary here, and lib/verify-auth.ts for what the auth check
// below does and does not prove.

import Anthropic from "@anthropic-ai/sdk";
import { verifyFirebaseIdToken } from "@/lib/verify-auth";
import { AGENT_TOOLS, executeAgentTool, TenantData } from "@/lib/agent-tools";

const MODEL = "claude-opus-5-5";
const SYSTEM_PROMPT =
  "You are the NEXLIB Library Assistant, helping this college's own library " +
  "staff with their catalogue, members, and loans. Use the provided tools " +
  "for every factual claim about books, members, loans, or fines — never " +
  "answer from memory or guess a number. If a tool returns no data, say so " +
  "plainly rather than inventing a figure. Keep answers concise; name the " +
  "specific books or members you mean.";

// Per-process rate limit — crude but real: without it this route is an
// unauthenticated-cost endpoint for anyone who can forge a plausible
// request body, bounded only by whether their Firebase ID token is valid
// (see verify-auth.ts). A real deployment behind more than one server
// process needs this moved to a shared store; noted, not solved here.
const RATE_LIMIT_WINDOW_MS = 60_000;
const RATE_LIMIT_MAX = 10;
const requestLog = new Map<string, number[]>();

function rateLimited(uid: string): boolean {
  const now = Date.now();
  const timestamps = (requestLog.get(uid) ?? []).filter((t) => now - t < RATE_LIMIT_WINDOW_MS);
  timestamps.push(now);
  requestLog.set(uid, timestamps);
  return timestamps.length > RATE_LIMIT_MAX;
}

interface ChatRequestBody extends TenantData {
  message: string;
  history?: { role: "user" | "assistant"; content: string }[];
}

export async function POST(req: Request) {
  const verified = await verifyFirebaseIdToken(req.headers.get("authorization"));
  if (!verified) {
    return Response.json({ error: "Not authenticated." }, { status: 401 });
  }
  if (rateLimited(verified.uid)) {
    return Response.json({ error: "Too many requests — wait a moment and try again." }, { status: 429 });
  }

  if (!process.env.ANTHROPIC_API_KEY) {
    return Response.json(
      { error: "The AI assistant is not configured on this deployment (ANTHROPIC_API_KEY unset)." },
      { status: 503 },
    );
  }

  let body: ChatRequestBody;
  try {
    body = await req.json();
  } catch {
    return Response.json({ error: "Invalid request body." }, { status: 400 });
  }
  if (!body.message || !Array.isArray(body.books) || !Array.isArray(body.members) || !Array.isArray(body.issues)) {
    return Response.json({ error: "Request must include 'message', 'books', 'members', and 'issues'." }, { status: 400 });
  }
  const data: TenantData = {
    books: body.books,
    members: body.members,
    issues: body.issues,
    reservations: Array.isArray(body.reservations) ? body.reservations : [],
    ebooks: Array.isArray(body.ebooks) ? body.ebooks : [],
  };

  const client = new Anthropic({ apiKey: process.env.ANTHROPIC_API_KEY });
  const messages: Anthropic.Beta.BetaMessageParam[] = [
    ...(body.history ?? []).map((h) => ({ role: h.role, content: h.content })),
    { role: "user", content: body.message },
  ];

  const MAX_ITERATIONS = 6;
  try {
    for (let i = 0; i < MAX_ITERATIONS; i++) {
      const response = await client.beta.messages.create({
        model: MODEL,
        max_tokens: 2048,
        system: SYSTEM_PROMPT,
        tools: AGENT_TOOLS,
        messages,
        output_config: { effort: "low" },
        betas: ["server-side-fallback-2026-07-01"],
        fallbacks: "default",
      });

      if (response.stop_reason === "refusal") {
        return Response.json({ reply: "I can't help with that request." });
      }

      const toolUseBlocks = response.content.filter(
        (b): b is Anthropic.Beta.BetaToolUseBlock => b.type === "tool_use",
      );
      if (toolUseBlocks.length === 0) {
        const text = response.content
          .filter((b): b is Anthropic.Beta.BetaTextBlock => b.type === "text")
          .map((b) => b.text)
          .join("");
        return Response.json({ reply: text || "(no response)" });
      }

      messages.push({ role: "assistant", content: response.content });
      const toolResults: Anthropic.Beta.BetaToolResultBlockParam[] = toolUseBlocks.map((block) => {
        let result: unknown;
        try {
          result = executeAgentTool(block.name, block.input as Record<string, unknown>, data);
        } catch (e) {
          result = { error: e instanceof Error ? e.message : String(e) };
        }
        return {
          type: "tool_result",
          tool_use_id: block.id,
          content: JSON.stringify(result),
        };
      });
      messages.push({ role: "user", content: toolResults });
    }

    return Response.json({ reply: "I wasn't able to finish that in a reasonable number of steps — try rephrasing." });
  } catch (e) {
    console.error("Library agent chat error:", e);
    return Response.json({ error: "The AI assistant hit an error. Please try again." }, { status: 502 });
  }
}
