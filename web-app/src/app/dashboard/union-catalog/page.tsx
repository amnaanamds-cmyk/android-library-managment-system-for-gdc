"use client";
import React, { useState, useEffect } from "react";
import { useAuth } from "@/lib/auth-context";
import { db } from "@/lib/firebase";
import { collection, getDocs, setDoc, doc } from "firebase/firestore";
import { useTenantCollection } from "@/lib/firestore-hooks";

export default function UnionCatalogPage() {
  const { profile } = useAuth();
  const [search, setSearch] = useState("");
  const [field, setField] = useState("All");
  const [loading, setLoading] = useState(false);
  const [results, setResults] = useState<any[]>([]);
  const [activeTab, setActiveTab] = useState("Search");
  
  const { data: illRequests, loading: illLoading } = useTenantCollection("ill_requests");

  const performSearch = async () => {
    if (!search.trim()) return alert("Enter a search term");
    setLoading(true);
    setResults([]);
    
    try {
      // 1. Get all institutions
      const colSnapshot = await getDocs(collection(db, "colleges"));
      const institutions = colSnapshot.docs.map(d => ({ id: d.id, ...d.data() }));
      
      const q = search.toLowerCase();
      let foundItems: any[] = [];
      
      // 2. Query books from each institution
      for (const inst of institutions) {
        const instId = inst.id;
        const instName = (inst as any).name || instId;
        
        try {
          const booksSnap = await getDocs(collection(db, "institutions", instId, "books"));
          booksSnap.forEach(docSnap => {
            const book = docSnap.data();
            const title = (book.title || "").toLowerCase();
            const author = (book.author || "").toLowerCase();
            const isbn = (book.isbn || "").toLowerCase();
            
            let match = false;
            if (field === "Title") match = title.includes(q);
            else if (field === "Author") match = author.includes(q);
            else if (field === "ISBN") match = isbn.includes(q);
            else match = title.includes(q) || author.includes(q) || isbn.includes(q);
            
            if (match) {
              foundItems.push({
                id: docSnap.id,
                institutionId: instId,
                institution: instName,
                title: book.title,
                author: book.author,
                isbn: book.isbn,
                status: book.status || "Unknown",
                accNo: book.accNo,
                callNo: book.callNumber
              });
            }
          });
        } catch (e) {
          console.warn("Could not fetch from", instId);
        }
      }
      setResults(foundItems);
    } catch (e) {
      console.error(e);
      alert("Search failed.");
    }
    setLoading(false);
  };

  const requestILL = async (book: any) => {
    const requesterName = prompt(`Requesting '${book.title}' from ${book.institution}.\nEnter Requester Name:`);
    if (!requesterName) return;
    
    const requesterId = prompt("Enter Member ID:");
    if (!requesterId) return;
    
    const purpose = prompt("Purpose (Optional):");
    
    const syncId = Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15);
    
    try {
      await setDoc(doc(db, "institutions", profile?.institutionId || "gdc11", "ill_requests", syncId), {
        syncId,
        bookTitle: book.title,
        fromInstitution: book.institution,
        requesterName,
        requesterId,
        purpose,
        duration: "14 days",
        requestDate: new Date().toISOString().split('T')[0],
        status: "Pending",
        lastUpdated: Date.now()
      });
      alert("ILL Request submitted!");
      setActiveTab("Requests");
    } catch (e) {
      console.error(e);
      alert("Failed to submit request.");
    }
  };

  const markFulfilled = async (req: any) => {
    if (!profile?.institutionId) return;
    try {
      await setDoc(doc(db, "institutions", profile.institutionId, "ill_requests", req.id || req.syncId), {
        ...req,
        status: "Fulfilled",
        lastUpdated: Date.now()
      });
      alert("Marked as fulfilled");
    } catch(e) {
      console.error(e);
    }
  };

  return (
    <div className="space-y-6 animate-in fade-in duration-500 max-w-7xl mx-auto">
      <div>
        <h1 className="text-3xl font-extrabold text-ink">🌐 Union Catalogue</h1>
        <p className="text-sm text-muted">Search resources across all registered institutions & request ILLs.</p>
      </div>

      <div className="flex border-b border-line mb-6">
        {["Search", "Requests", "About"].map(tab => (
          <button key={tab} onClick={() => setActiveTab(tab)}
            className={`px-6 py-3 font-bold text-sm border-b-2 transition-colors ${
              activeTab === tab ? "border-accent text-accent" : "border-transparent text-muted hover:text-body"
            }`}>
            {tab}
          </button>
        ))}
      </div>

      {activeTab === "Search" && (
        <div className="space-y-4">
          <div className="rounded-xl border border-line bg-surface-2 p-4 flex gap-3">
            <input value={search} onChange={e => setSearch(e.target.value)} onKeyDown={e => e.key === 'Enter' && performSearch()}
              placeholder="🔍 Search title, author, ISBN..."
              className="flex-1 rounded-lg border border-line bg-surface px-4 py-2 text-sm text-ink outline-none focus:border-accent" />
            <select value={field} onChange={e => setField(e.target.value)}
              className="rounded-lg border border-line bg-surface px-3 py-2 text-sm text-ink outline-none">
              <option>All</option><option>Title</option><option>Author</option><option>ISBN</option>
            </select>
            <button onClick={performSearch} disabled={loading}
              className="px-6 py-2 rounded-lg bg-accent-bg text-on-accent font-bold text-sm hover:bg-accent-bg disabled:opacity-50 transition-colors">
              {loading ? "Searching..." : "🔍 Search"}
            </button>
          </div>

          <div className="rounded-xl border border-line bg-surface-2 overflow-hidden shadow-xl">
            {loading ? (
              <div className="py-12 flex justify-center"><div className="h-8 w-8 animate-spin rounded-full border-4 border-accent border-t-transparent" /></div>
            ) : (
              <table className="w-full text-left text-sm text-body">
                <thead className="text-xs uppercase bg-surface/40 text-muted">
                  <tr>
                    <th className="px-4 py-3">Institution</th>
                    <th className="px-4 py-3">Title</th>
                    <th className="px-4 py-3">Author</th>
                    <th className="px-4 py-3">ISBN</th>
                    <th className="px-4 py-3">Call No</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line/40">
                  {results.map((r, i) => (
                    <tr key={i} className="hover:bg-surface-2/10">
                      <td className="px-4 py-3 font-semibold text-accent">{r.institution}</td>
                      <td className="px-4 py-3 text-ink">{r.title}</td>
                      <td className="px-4 py-3">{r.author || "—"}</td>
                      <td className="px-4 py-3">{r.isbn || "—"}</td>
                      <td className="px-4 py-3 font-mono text-muted">{r.callNo || "—"}</td>
                      <td className="px-4 py-3">
                        <span className={`text-xs font-bold ${r.status === 'Available' ? 'text-positive' : 'text-warning'}`}>
                          {r.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <button onClick={() => requestILL(r)}
                          className="px-3 py-1 rounded bg-accent-bg/10 border border-accent/30 text-xs font-bold text-accent hover:bg-accent-bg/20">
                          🤝 Request ILL
                        </button>
                      </td>
                    </tr>
                  ))}
                  {results.length === 0 && !loading && <tr><td colSpan={7} className="text-center py-8 text-muted">Run a search to find cross-library resources.</td></tr>}
                </tbody>
              </table>
            )}
          </div>
        </div>
      )}

      {activeTab === "Requests" && (
        <div className="rounded-xl border border-line bg-surface-2 overflow-hidden shadow-xl">
          {illLoading ? (
            <div className="py-12 flex justify-center"><div className="h-8 w-8 animate-spin rounded-full border-4 border-accent border-t-transparent" /></div>
          ) : (
            <table className="w-full text-left text-sm text-body">
              <thead className="text-xs uppercase bg-surface/40 text-muted">
                <tr>
                  <th className="px-4 py-3">Requester</th>
                  <th className="px-4 py-3">Book Title</th>
                  <th className="px-4 py-3">From Institution</th>
                  <th className="px-4 py-3">Date</th>
                  <th className="px-4 py-3">Duration</th>
                  <th className="px-4 py-3">Status</th>
                  <th className="px-4 py-3 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-line/40">
                {(illRequests || []).map((req: any) => (
                  <tr key={req.id} className="hover:bg-surface-2/10">
                    <td className="px-4 py-3 font-semibold text-ink">{req.requesterName} <span className="text-xs text-muted">({req.requesterId})</span></td>
                    <td className="px-4 py-3 text-ink">{req.bookTitle}</td>
                    <td className="px-4 py-3 text-accent">{req.fromInstitution}</td>
                    <td className="px-4 py-3">{req.requestDate}</td>
                    <td className="px-4 py-3">{req.duration}</td>
                    <td className="px-4 py-3">
                      <span className={`text-xs font-bold ${req.status === 'Fulfilled' ? 'text-positive' : 'text-warning'}`}>
                        {req.status}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-right">
                      {req.status !== 'Fulfilled' && (
                        <button onClick={() => markFulfilled(req)}
                          className="px-3 py-1 rounded bg-positive/10 border border-positive/30 text-xs font-bold text-positive hover:bg-positive/20">
                          ✅ Fulfill
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
                {(illRequests || []).length === 0 && <tr><td colSpan={7} className="text-center py-8 text-muted">No ILL requests found.</td></tr>}
              </tbody>
            </table>
          )}
        </div>
      )}

      {activeTab === "About" && (
        <div className="rounded-xl border border-line bg-surface-2 p-8 prose prose-invert max-w-none">
          <h2 className="text-accent">Union Catalogue — NEXLIB</h2>
          <p>The <b>Union Catalogue</b> provides a consolidated view of library resources across all registered institutions under the Directorate.</p>
          <ul>
            <li>🔍 <b>Cross-library search</b> for books by title, author, ISBN</li>
            <li>📍 <b>Location awareness</b> — know which institution holds a copy</li>
            <li>🤝 <b>Inter-Library Loan (ILL)</b> — request unavailable books from other libraries</li>
            <li>📊 <b>Resource sharing statistics</b> across the network</li>
          </ul>
          <p className="text-muted text-sm mt-8">Based on Z39.50 / SRU standard concepts. Data is pulled in real-time from Firestore across all linked institutions.</p>
        </div>
      )}

    </div>
  );
}
