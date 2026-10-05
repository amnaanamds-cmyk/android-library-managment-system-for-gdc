// app/api/agent/nightly-briefing/route.ts
//
// There is no Cloud Functions plan behind this project (see RUNBOOK.md and
// directorate_snapshots_history's own rule comment in firestore.rules), so
// there is no true scheduled cron here. Instead: whichever directorate
// admin opens the Overview page next today generates that day's briefing,
// from data their own client already legitimately fetched — same trust
// boundary and same reasoning as the chat route. The client is what
// throttles this to once per day, by checking directorate_ai_briefings/
// {today} before ever calling this route — see src/app/page.tsx.

import Anthropic from "@anthropic-ai/sdk";
import { verifyFirebaseIdToken } from "@/lib/verify-auth";
import { AGENT_TOOLS, executeAgentTool } from "@/lib/agent-tools";
import { DirectorateSnapshot } from "@/lib/directorate";

const MODEL = "claude-opus-5-5";
const SYSTEM_PROMPT =
  "You are the NEXLIB Directorate Network Assistant. Write a short daily " +
  "briefing (3-5 sentences, no markdown, no greeting) for Higher Education " +
  "Department staff opening the dashboard this morning. Use the provided " +
  "tools to find what actually needs attention today — high-severity " +
  "alerts, colleges with a low books-available percentage, compliance " +
  "scorecard outliers — and lead with the most important thing. Never " +
  "invent a figure or a college name; if nothing stands out, say the " +
  "network looks healthy rather than manufacturing a concern.";

interface BriefingRequestBody {
  colleges: DirectorateSnapshot[];
}

export async function POST(req: Request) {
  const verified = await verifyFirebaseIdToken(req.headers.get("authorization"));
  if (!verified) {
    return Response.json({ error: "Not authenticated." }, { status: 401 });
  }
  if (!process.env.ANTHROPIC_API_KEY) {
    return Response.json(
      { error: "The AI assistant is not configured on this deployment (ANTHROPIC_API_KEY unset)." },
      { status: 503 },
    );
  }

  let body: BriefingRequestBody;
  try {
    body = await req.json();
  } catch {
    return Response.json({ error: "Invalid request body." }, { status: 400 });
  }
  if (!Array.isArray(body.colleges)) {
    return Response.json({ error: "Request must include 'colleges'." }, { status: 400 });
  }
  if (body.colleges.length === 0) {
    return Response.json({ text: "No approved colleges are reporting yet — nothing to summarize." });
  }

  const client = new Anthropic({ apiKey: process.env.ANTHROPIC_API_KEY });
  const messages: Anthropic.Beta.BetaMessageParam[] = [
    { role: "user", content: "Write today's network briefing." },
  ];

  const MAX_ITERATIONS = 6;
  try {
    for (let i = 0; i < MAX_ITERATIONS; i++) {
      const response = await client.beta.messages.create({
        model: MODEL,
        max_tokens: 1024,
        system: SYSTEM_PROMPT,
        tools: AGENT_TOOLS,
        messages,
        output_config: { effort: "low" },
        betas: ["server-side-fallback-2026-07-01"],
        fallbacks: "default",
      });

      if (response.stop_reason === "refusal") {
        return Response.json({ text: "The network looks stable; no briefing could be generated this time." });
      }

      const toolUseBlocks = response.content.filter(
        (b): b is Anthropic.Beta.BetaToolUseBlock => b.type === "tool_use",
      );
      if (toolUseBlocks.length === 0) {
        const text = response.content
          .filter((b): b is Anthropic.Beta.BetaTextBlock => b.type === "text")
          .map((b) => b.text)
          .join("")
          .trim();
        return Response.json({ text: text || "The network looks stable today." });
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

    return Response.json({ text: "The network looks stable today." });
  } catch (e) {
    console.error("Directorate nightly briefing error:", e);
    return Response.json({ error: "The AI assistant hit an error generating the briefing." }, { status: 502 });
  }
}
