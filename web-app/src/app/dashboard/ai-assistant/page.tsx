"use client";

import React, { useRef, useState } from "react";
import { useAuth } from "@/lib/auth-context";
import { useTenantCollection } from "@/lib/firestore-hooks";
import { COLLECTIONS } from "@/lib/schema";

interface ChatTurn {
  role: "user" | "assistant";
  content: string;
}

const SUGGESTIONS = [
  "How many books are overdue right now, and who holds them?",
  "What are our top 5 most-borrowed books?",
  "Search the catalogue for anything by Chemistry.",
  "What are our outstanding fines?",
];

export default function AiAssistantPage() {
  const { user } = useAuth();
  const { data: books, loading: loadingBooks } = useTenantCollection(COLLECTIONS.books);
  const { data: members, loading: loadingMembers } = useTenantCollection(COLLECTIONS.members);
  const { data: issues, loading: loadingIssues } = useTenantCollection(COLLECTIONS.issuedBooks);
  const { data: reservations } = useTenantCollection(COLLECTIONS.reservations);
  const { data: ebooks } = useTenantCollection(COLLECTIONS.ebooks);

  const [turns, setTurns] = useState<ChatTurn[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  const loading = loadingBooks || loadingMembers || loadingIssues;

  async function send(message: string) {
    if (!message.trim() || sending || !user || loading) return;
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
        body: JSON.stringify({ message, history, books, members, issues, reservations, ebooks }),
      });
      const data = await res.json();
      if (!res.ok) {
        setError(data.error || `Request failed (${res.status}).`);
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

  return (
    <div className="space-y-6 animate-in fade-in duration-500 max-w-5xl mx-auto flex h-[calc(100vh-8rem)] flex-col">
      <div>
        <h1 className="text-3xl font-extrabold text-[#E8EEF8]">🤖 AI Library Assistant</h1>
        <p className="text-sm text-slate-400">
          Ask about this library&apos;s own catalogue, members, loans, and fines — the assistant calls the same
          search and ranking tools this dashboard uses, over the data currently loaded.
        </p>
      </div>

      <div className="flex-1 min-h-[320px] overflow-y-auto rounded-xl border border-blue-950 bg-[#070F1E] p-6 shadow-xl">
        {loading ? (
          <div className="flex h-full items-center justify-center text-sm text-slate-500">Loading library data…</div>
        ) : turns.length === 0 ? (
          <div className="flex h-full flex-col items-center justify-center gap-4 py-10 text-center">
            <span className="text-3xl">💬</span>
            <p className="text-sm text-slate-500">Ask anything about your library below, or try:</p>
            <div className="flex flex-wrap justify-center gap-2 px-4">
              {SUGGESTIONS.map((s) => (
                <button
                  key={s}
                  onClick={() => send(s)}
                  className="rounded-full border border-[#1E3050] px-3 py-1.5 text-xs text-slate-400 hover:border-[#C8A84B]/50 hover:text-[#C8A84B]"
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
                      ? "bg-[#C8A84B]/10 text-[#E8D9A8]"
                      : "border border-[#1E3050] bg-[#0D1F38] text-slate-200"
                  }`}
                >
                  {t.content}
                </div>
              </div>
            ))}
            {sending && <div className="text-xs text-slate-500">Thinking…</div>}
            <div ref={bottomRef} />
          </div>
        )}
      </div>

      {error && <p className="text-xs font-semibold text-red-400">{error}</p>}

      <form
        className="flex gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          send(input);
        }}
      >
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Ask about your library…"
          disabled={sending || loading}
          className="flex-1 rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2.5 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]"
        />
        <button
          type="submit"
          disabled={sending || loading || !input.trim()}
          className="rounded-lg bg-gradient-to-r from-purple-600 to-indigo-600 px-5 py-2.5 text-sm font-bold text-white transition-all hover:from-purple-500 hover:to-indigo-500 shadow-lg disabled:opacity-50"
        >
          Send
        </button>
      </form>
    </div>
  );
}
