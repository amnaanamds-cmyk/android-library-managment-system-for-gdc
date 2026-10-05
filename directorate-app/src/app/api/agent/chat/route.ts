// app/api/agent/chat/route.ts
//
// Server-side only — this is the one place in directorate-app allowed to
// hold ANTHROPIC_API_KEY. Every page in this app is a client component
// talking to Firestore directly; this route is the single exception,
// and exists only because an API key must never reach the browser.
//
// Manual agentic tool-use loop (see python/claude-api/tool-use.md /
// typescript/claude-api/tool-use.md in the claude-api skill for the
// pattern) over lib/agent-tools.ts's real analytics tools, operating on
// network data the client already fetched and sent in the body — see
// lib/agent-tools.ts's header for why that's the right trust boundary
// here, and lib/verify-auth.ts for what the auth check below does and
// does not prove.

import Anthropic from "@anthropic-ai/sdk";
import { verifyFirebaseIdToken } from "@/lib/verify-auth";
import { AGENT_TOOLS, executeAgentTool } from "@/lib/agent-tools";
import { DirectorateSnapshot } from "@/lib/directorate";

const MODEL = "claude-opus-5-5";
const SYSTEM_PROMPT =
  "You are the NEXLIB Directorate Network Assistant, helping Higher Education " +
  "Department staff understand aggregate figures across every college in the " +
  "network. Use the provided tools for every factual claim about the network " +
  "— colleges, books, loans, alerts, compliance, district or category stock " +
  "— never answer from memory or guess a number. If a tool returns no data, " +
  "say so plainly rather than inventing a figure. Keep answers concise and " +
  "specific; cite which colleges or districts you mean by name.";

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

interface ChatRequestBody {
  message: string;
  history?: { role: "user" | "assistant"; content: string }[];
  colleges: DirectorateSnapshot[];
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
  if (!body.message || !Array.isArray(body.colleges)) {
    return Response.json({ error: "Request must include 'message' and 'colleges'." }, { status: 400 });
  }

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
          result = executeAgentTool(block.name, block.input as Record<string, unknown>, body.colleges);
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
    console.error("Directorate agent chat error:", e);
    return Response.json({ error: "The AI assistant hit an error. Please try again." }, { status: 502 });
  }
}
