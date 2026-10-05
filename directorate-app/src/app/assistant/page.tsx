"use client";

import React, { useRef, useState } from "react";
import { useDirectorateNetwork } from "@/lib/directorate";
import { useAuth } from "@/lib/auth-context";
import { PageHeader, Card, Spinner, Button, Textarea } from "@/components/ui";
import { IconAssistant } from "@/components/icons";

interface ChatTurn {
  role: "user" | "assistant";
  content: string;
}

const SUGGESTIONS = [
  "Which colleges have the lowest percentage of books available right now?",
  "What are the highest-severity alerts across the network?",
  "Which district has the most overdue loans?",
  "Show me the compliance scorecard for the three lowest-scoring colleges.",
];

export default function AssistantPage() {
  const { user } = useAuth();
  const { colleges, loading } = useDirectorateNetwork();
  const [turns, setTurns] = useState<ChatTurn[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  async function send(message: string) {
    if (!message.trim() || sending || !user) return;
    setError(null);
    const history = turns.map((t) => ({ role: t.role, content: t.content }));
    const nextTurns: ChatTurn[] = [...turns, { role: "user", content: message }];
    setTurns(nextTurns);
    setInput("");
    setSending(true);
    try {
      const token = await user.getIdToken();
      const res = await fetch("/api/agent/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
        body: JSON.stringify({ message, history, colleges }),
      });
      const data = await res.json();
      if (!res.ok) {
        setError(data.error || `Request failed (${res.status}).`);
        setTurns(nextTurns); // drop nothing already shown; just surface the error below
        return;
      }
      setTurns([...nextTurns, { role: "assistant", content: data.reply }]);
    } catch {
      setError("Could not reach the assistant. Check your connection and try again.");
    } finally {
      setSending(false);
      setTimeout(() => bottomRef.current?.scrollIntoView({ behavior: "smooth" }), 50);
    }
  }

  if (loading) return <Spinner label="Loading network data…" />;

  return (
    <div className="flex h-full flex-col">
      <PageHeader
        title="Ask the Network"
        description="Ask a question about aggregate figures across every college — the assistant calls the same ranking, alert, and compliance tools this dashboard uses, over the colleges currently loaded."
      />

      <Card className="mb-4 flex-1 overflow-y-auto min-h-[320px] max-h-[55vh]">
        {turns.length === 0 ? (
          <div className="flex h-full flex-col items-center justify-center gap-4 py-10 text-center">
            <IconAssistant className="h-8 w-8 text-slate-600" />
            <p className="text-sm text-slate-500">Ask anything about the network below, or try:</p>
            <div className="flex flex-wrap justify-center gap-2 px-4">
              {SUGGESTIONS.map((s) => (
                <button
                  key={s}
                  onClick={() => send(s)}
                  className="rounded-full border border-slate-800 px-3 py-1.5 text-xs text-slate-400 hover:border-amber-500/50 hover:text-amber-300"
                >
                  {s}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <div className="space-y-4">
            {turns.map((t, i) => (
              <div key={i} className={t.role === "user" ? "text-right" : ""}>
                <div
                  className={`inline-block max-w-[85%] rounded-lg px-3.5 py-2.5 text-left text-sm ${
                    t.role === "user"
                      ? "bg-amber-500/10 text-amber-100"
                      : "border border-slate-800 bg-slate-900/60 text-slate-200"
                  }`}
                >
                  {t.content}
                </div>
              </div>
            ))}
            {sending && (
              <div className="text-xs text-slate-500">Thinking…</div>
            )}
            <div ref={bottomRef} />
          </div>
        )}
      </Card>

      {error && (
        <p className="mb-2 text-xs font-semibold text-red-400">{error}</p>
      )}

      <form
        className="flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          send(input);
        }}
      >
        <Textarea
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && !e.shiftKey) {
              e.preventDefault();
              send(input);
            }
          }}
          placeholder="Ask about the network…"
          rows={1}
          className="flex-1"
          disabled={sending}
        />
        <Button type="submit" disabled={sending || !input.trim()}>
          Send
        </Button>
      </form>
    </div>
  );
}
