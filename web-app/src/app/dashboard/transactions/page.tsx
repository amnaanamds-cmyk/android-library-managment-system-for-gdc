"use client";

import React, { useState } from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";

export default function TransactionsPage() {
  const { data: issues, loading: loadingIssues, addRecord: addIssue, updateRecord: updateIssue } = useTenantCollection("issued_books");
  const { data: books, loading: loadingBooks, updateRecord: updateBook } = useTenantCollection("books");
  const { data: members, loading: loadingMembers, updateRecord: updateMember } = useTenantCollection("members");

  const [bookId, setBookId] = useState("");
  const [memberId, setMemberId] = useState("");
  const [adding, setAdding] = useState(false);

  const handleIssue = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!bookId || !memberId) return;
    setAdding(true);
    try {
      const selectedBook = books?.find(b => b.id === bookId);
      const selectedMember = members?.find(m => m.id === memberId);

      await addIssue({
        bookId: selectedBook?.id || bookId,
        bookTitle: selectedBook?.title || "Unknown Book",
        bookIsbn: selectedBook?.isbn || selectedBook?.accNo || "N/A",
        memberId: selectedMember?.id || memberId,
        memberName: selectedMember?.name || "Unknown Member",
        memberMemberId: selectedMember?.memberId || "Unknown",
        issueDate: new Date().toISOString().split('T')[0],
        dueDate: new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString().split('T')[0], // 14 days later
        status: "Issued",
        fine: 0,
        deleted: false,
        lastUpdated: Date.now(),
      });

      // Update book status to Issued
      if (selectedBook) {
        await updateBook(selectedBook.id, {
          status: "Issued",
          lastUpdated: Date.now()
        });
      }

      // Update member issued count
      if (selectedMember) {
        await updateMember(selectedMember.id, {
          booksIssued: (selectedMember.booksIssued || 0) + 1,
          lastUpdated: Date.now()
        });
      }

      setBookId("");
      setMemberId("");
    } catch (err) {
      console.error(err);
    } finally {
      setAdding(false);
    }
  };

  const handleReturn = async (issueId: string) => {
    try {
      const issue = issues?.find((i: any) => i.id === issueId);
      if (!issue) return;

      const today = new Date();
      const dueDate = new Date(issue.dueDate);
      const diffTime = today.getTime() - dueDate.getTime();
      const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));
      const fine = diffDays > 0 ? diffDays * 5 : 0; // Assuming Rs. 5 per day

      await updateIssue(issueId, {
        status: "Returned",
        returnDate: today.toISOString().split('T')[0],
        fine: fine,
        lastUpdated: Date.now(),
      });

      // Update book status to Available
      const book = books?.find((b: any) => b.id === issue.bookId || String(b.id) === String(issue.bookId));
      if (book) {
        await updateBook(book.id, {
          status: "Available",
          lastUpdated: Date.now()
        });
      }

      // Decrement member's issued count
      const member = members?.find((m: any) => m.id === issue.memberId || String(m.id) === String(issue.memberId));
      if (member) {
        await updateMember(member.id, {
          booksIssued: Math.max(0, (member.booksIssued || 1) - 1),
          lastUpdated: Date.now()
        });
      }
    } catch (err) {
      console.error(err);
    }
  };

  const activeBooks = books?.filter(b => b.status !== "Issued") || [];
  const activeMembers = members || [];

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-extrabold text-ink">Issue & Return Books</h1>
        <p className="text-sm text-muted">Record book loan transactions and return events</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Issue Book Form */}
        <div className="rounded-xl border border-line bg-surface-2 p-6 shadow-xl h-fit">
          <h3 className="text-lg font-bold text-ink mb-4">Issue a Book</h3>
          <form onSubmit={handleIssue} className="space-y-4">
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-muted">Select Book</label>
              <select
                required
                value={bookId}
                onChange={(e) => setBookId(e.target.value)}
                className="mt-2 w-full rounded-lg border border-line bg-surface px-4 py-2.5 text-sm text-ink outline-none focus:border-accent"
              >
                <option value="">-- Choose Book --</option>
                {activeBooks.map(b => (
                  <option key={b.id} value={b.id}>{b.title} ({b.author})</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-muted">Select Member</label>
              <select
                required
                value={memberId}
                onChange={(e) => setMemberId(e.target.value)}
                className="mt-2 w-full rounded-lg border border-line bg-surface px-4 py-2.5 text-sm text-ink outline-none focus:border-accent"
              >
                <option value="">-- Choose Member --</option>
                {activeMembers.map(m => (
                  <option key={m.id} value={m.id}>{m.name} ({m.role})</option>
                ))}
              </select>
            </div>
            <button
              type="submit"
              disabled={adding || !bookId || !memberId}
              className="bg-accent-bg w-full rounded-lg py-3 text-sm font-bold text-on-accent transition-all disabled:opacity-50"
            >
              {adding ? "Processing..." : "Issue Book"}
            </button>
          </form>
        </div>

        {/* Transactions list */}
        <div className="lg:col-span-2 rounded-xl border border-line bg-surface-2 p-6 shadow-xl overflow-hidden">
          <div className="flex justify-between items-center mb-4">
            <h3 className="text-lg font-bold text-ink">Transaction History & Fines</h3>
            <button className="px-3 py-1.5 bg-warning-soft/50 border border-warning hover:bg-warning-soft text-warning text-xs font-bold rounded-lg transition-colors">
              ⚖️ Fine Waivers
            </button>
          </div>
          {loadingIssues ? (
            <div className="py-12 flex justify-center">
              <div className="h-8 w-8 animate-spin rounded-full border-4 border-accent border-t-transparent" />
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-body">
                <thead className="text-xs uppercase bg-surface/40 text-muted">
                  <tr>
                    <th className="px-4 py-3">Book Title</th>
                    <th className="px-4 py-3">Borrower</th>
                    <th className="px-4 py-3">Issue Date</th>
                    <th className="px-4 py-3">Due Date</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line/40">
                  {issues?.map((issue) => (
                    <tr key={issue.id} className="hover:bg-surface-2/10">
                      <td className="px-4 py-4 font-semibold text-ink">{issue.bookTitle}</td>
                      <td className="px-4 py-4">{issue.memberName}</td>
                      <td className="px-4 py-4 text-xs">{issue.issueDate}</td>
                      <td className="px-4 py-4 text-xs">{issue.dueDate}</td>
                      <td className="px-4 py-4">
                        <span className={`px-2 py-0.5 rounded text-xs font-semibold ${
                          issue.status === "Returned" ? "bg-positive-soft text-positive border border-positive" : "bg-warning-soft text-warning border border-warning"
                        }`}>
                          {issue.status === "Returned" ? "Returned" : "Active Loan"}
                        </span>
                      </td>
                      <td className="px-4 py-4 text-right space-x-3">
                        {issue.status !== "Returned" && (
                          <>
                            <button
                              onClick={() => alert(`Waiving fine for ${issue.memberName}...`)}
                              className="text-xs font-bold text-warning hover:text-warning transition-colors"
                            >
                              Waive Fine
                            </button>
                            <button
                              onClick={() => handleReturn(issue.id)}
                              className="text-xs font-bold text-accent hover:text-accent transition-colors"
                            >
                              Mark Returned
                            </button>
                          </>
                        )}
                      </td>
                    </tr>
                  ))}
                  {issues?.length === 0 && (
                    <tr>
                      <td colSpan={6} className="text-center py-8 text-muted">
                        No library transactions recorded.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
