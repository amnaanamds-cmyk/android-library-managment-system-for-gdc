"use client";

import React, { useState, useRef } from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";
import Papa from "papaparse";
import * as XLSX from "xlsx";

export default function BooksPage() {
  const { data: books, loading, error, addRecord, deleteRecord } = useTenantCollection("books");
  
  // All fields from Desktop parity
  const [isbn, setIsbn] = useState("");
  const [accNo, setAccNo] = useState("");
  const [title, setTitle] = useState("");
  const [author, setAuthor] = useState("");
  const [publisher, setPublisher] = useState("");
  const [publisherPlace, setPublisherPlace] = useState("");
  const [publishDate, setPublishDate] = useState("");
  const [edition, setEdition] = useState("");
  const [volume, setVolume] = useState("");
  const [procurement, setProcurement] = useState("");
  const [pages, setPages] = useState<number>(1);
  const [price, setPrice] = useState<number>(0);
  const [subject, setSubject] = useState(""); // Maps to category
  const [digitalUrl, setDigitalUrl] = useState("");
  const [isDigital, setIsDigital] = useState(false);
  
  const [adding, setAdding] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) return;
    setAdding(true);
    try {
      await addRecord({
        isbn: isbn.trim(),
        accNo: accNo.trim(),
        title: title.trim(),
        author: author.trim(),
        publisher: publisher.trim(),
        publisherPlace: publisherPlace.trim(),
        publishDate: publishDate.trim(),
        edition: edition.trim(),
        volume: volume.trim(),
        procurement: procurement.trim(),
        pages: pages,
        price: price,
        subject: subject.trim() || "General",
        category: subject.trim() || "General",
        digitalUrl: digitalUrl.trim(),
        isDigital: isDigital,
        status: "Available",
        createdAt: Date.now()
      });
      setIsbn(""); setAccNo(""); setTitle(""); setAuthor(""); setPublisher("");
      setPublisherPlace(""); setPublishDate(""); setEdition(""); setVolume("");
      setProcurement(""); setPages(1); setPrice(0); setSubject(""); setDigitalUrl(""); setIsDigital(false);
    } catch (err) {
      console.error(err);
    } finally {
      setAdding(false);
    }
  };

  const handleImportClick = () => {
    fileInputRef.current?.click();
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setAdding(true);
    try {
      if (file.name.endsWith(".csv")) {
        Papa.parse(file, {
          header: true,
          complete: async (results) => {
            let successCount = 0;
            for (const row of results.data as any[]) {
              if (!row.title && !row.Title) continue;
              await addRecord({
                isbn: row.isbn || row.ISBN || row.Isbn || "",
                accNo: row.accNo || row.AccNo || "",
                title: row.title || row.Title || "",
                author: row.author || row.Author || "",
                publisher: row.publisher || row.Publisher || "",
                publisherPlace: row.publisherPlace || row.PublisherPlace || "",
                publishDate: row.publishDate || row.PublishDate || "",
                edition: row.edition || row.Edition || "",
                volume: row.volume || row.Volume || "",
                procurement: row.procurement || row.Procurement || "",
                pages: parseInt(row.pages || row.Pages) || 1,
                price: parseFloat(row.price || row.Price) || 0,
                subject: row.subject || row.Subject || row.category || row.Category || "General",
                category: row.subject || row.Subject || row.category || row.Category || "General",
                digitalUrl: row.digitalUrl || row.DigitalUrl || "",
                isDigital: Boolean(row.isDigital || row.IsDigital || row.digitalUrl || row.DigitalUrl),
                status: "Available",
                createdAt: Date.now()
              });
              successCount++;
            }
            alert(`Successfully imported ${successCount} books from CSV.`);
            setAdding(false);
          },
          error: (err) => {
            console.error("CSV Parse Error", err);
            setAdding(false);
          }
        });
      } else if (file.name.match(/\.xlsx?$/)) {
        const reader = new FileReader();
        reader.onload = async (evt) => {
          try {
            const bstr = evt.target?.result;
            const wb = XLSX.read(bstr, { type: "binary" });
            const wsname = wb.SheetNames[0];
            const ws = wb.Sheets[wsname];
            const data = XLSX.utils.sheet_to_json(ws);

            let successCount = 0;
            for (const row of data as any[]) {
              if (!row.title && !row.Title) continue;
              await addRecord({
                isbn: row.isbn || row.ISBN || row.Isbn || "",
                accNo: row.accNo || row.AccNo || "",
                title: row.title || row.Title || "",
                author: row.author || row.Author || "",
                publisher: row.publisher || row.Publisher || "",
                publisherPlace: row.publisherPlace || row.PublisherPlace || "",
                publishDate: row.publishDate || row.PublishDate || "",
                edition: row.edition || row.Edition || "",
                volume: row.volume || row.Volume || "",
                procurement: row.procurement || row.Procurement || "",
                pages: parseInt(row.pages || row.Pages) || 1,
                price: parseFloat(row.price || row.Price) || 0,
                subject: row.subject || row.Subject || row.category || row.Category || "General",
                category: row.subject || row.Subject || row.category || row.Category || "General",
                digitalUrl: row.digitalUrl || row.DigitalUrl || "",
                isDigital: Boolean(row.isDigital || row.IsDigital || row.digitalUrl || row.DigitalUrl),
                status: "Available",
                createdAt: Date.now()
              });
              successCount++;
            }
            alert(`Successfully imported ${successCount} books from Excel.`);
          } catch (err) {
            console.error("Excel Parse Error", err);
          }
          setAdding(false);
        };
        reader.readAsBinaryString(file);
      } else {
        alert("Unsupported file format. Please upload a .csv or .xlsx file.");
        setAdding(false);
      }
    } catch (err) {
      console.error(err);
      setAdding(false);
    }
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const handleExportCSV = () => {
    if (!books || books.length === 0) return;
    const csv = Papa.unparse(books);
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.setAttribute("download", "Book_Catalog_Export.csv");
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const comingSoon = () => alert("This enterprise feature is coming soon!");

  return (
    <div className="space-y-6 animate-in fade-in duration-500 max-w-[1400px] mx-auto">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold text-ink">📚 Book Management</h1>
          <p className="text-sm text-muted">Add, view, and manage books in your library</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <input 
            type="file" 
            ref={fileInputRef} 
            className="hidden" 
            accept=".csv, .xlsx, .xls"
            onChange={handleFileChange}
          />
          <button onClick={handleImportClick} disabled={adding} className="bg-line text-muted border border-line rounded-md px-3 py-1.5 text-xs font-semibold hover:bg-line transition-colors shadow">
            📥 Import
          </button>
          <button onClick={handleExportCSV} className="bg-line text-muted border border-line rounded-md px-3 py-1.5 text-xs font-semibold hover:bg-line transition-colors shadow">
            📤 Export CSV
          </button>
          <button onClick={comingSoon} className="bg-line text-muted border border-line rounded-md px-3 py-1.5 text-xs font-semibold hover:bg-line transition-colors shadow">
            📤 Export MARC21
          </button>
          <button onClick={comingSoon} className="bg-line text-muted border border-line rounded-md px-3 py-1.5 text-xs font-semibold hover:bg-line transition-colors shadow">
            🏷️ Barcodes
          </button>
          <button onClick={comingSoon} className="bg-line text-muted border border-line rounded-md px-3 py-1.5 text-xs font-semibold hover:bg-line transition-colors shadow">
            🏷️ Spine Labels
          </button>
          <button onClick={comingSoon} className="bg-line text-muted border border-line rounded-md px-3 py-1.5 text-xs font-semibold hover:bg-line transition-colors shadow">
            ☁️ Upload Asset (DAM)
          </button>
        </div>
      </div>

      <div className="flex gap-4">
        <input 
          type="text"
          placeholder="🔍 Search by title, author, ISBN..."
          className="flex-1 rounded-lg border border-line bg-surface px-4 py-2 text-sm text-ink outline-none focus:border-accent"
        />
        <select className="rounded-lg border border-line bg-surface px-4 py-2 text-sm text-ink outline-none">
          <option>All Categories</option>
          <option>Fiction</option>
          <option>Non-Fiction</option>
          <option>Science</option>
          <option>Computer Science</option>
        </select>
        <select className="rounded-lg border border-line bg-surface px-4 py-2 text-sm text-ink outline-none">
          <option>All Status</option>
          <option>Available</option>
          <option>Issued</option>
        </select>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-4 gap-8">
        {/* Add Book Form */}
        <div className="xl:col-span-1 rounded-xl border border-line bg-surface-2 p-6 shadow-xl h-fit overflow-y-auto max-h-[80vh]">
          <h3 className="text-lg font-bold text-ink mb-4">Add New Book</h3>
          <form onSubmit={handleSubmit} className="space-y-4">
            
            <div className="flex gap-2">
              <div className="flex-1">
                <label className="block text-xs font-bold uppercase tracking-wider text-muted">ISBN</label>
                <input type="text" value={isbn} onChange={(e) => setIsbn(e.target.value)} className="mt-1 w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-accent" placeholder="978-..." />
              </div>
              <button type="button" onClick={comingSoon} className="mt-5 px-3 bg-accent-bg text-on-accent rounded-lg font-bold text-xs hover:bg-accent-strong transition-colors">
                ✨ Fetch
              </button>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-muted">Acc No</label>
                <input type="text" value={accNo} onChange={(e) => setAccNo(e.target.value)} className="mt-1 w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-accent" placeholder="B001" />
              </div>
              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-muted">Category</label>
                <input type="text" value={subject} onChange={(e) => setSubject(e.target.value)} className="mt-1 w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-accent" placeholder="Science" />
              </div>
            </div>

            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-muted">Book Title *</label>
              <input type="text" required value={title} onChange={(e) => setTitle(e.target.value)} className="mt-1 w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-accent" placeholder="Book Title" />
            </div>

            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-muted">Author *</label>
              <input type="text" required value={author} onChange={(e) => setAuthor(e.target.value)} className="mt-1 w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-accent" placeholder="Author Name" />
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-muted">Publisher</label>
                <input type="text" value={publisher} onChange={(e) => setPublisher(e.target.value)} className="mt-1 w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-accent" placeholder="Publisher" />
              </div>
              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-muted">Pub Date</label>
                <input type="text" value={publishDate} onChange={(e) => setPublishDate(e.target.value)} className="mt-1 w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-accent" placeholder="2024" />
              </div>
            </div>
            
            <div className="grid grid-cols-3 gap-3">
              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-muted">Ed.</label>
                <input type="text" value={edition} onChange={(e) => setEdition(e.target.value)} className="mt-1 w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-accent" placeholder="1st" />
              </div>
              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-muted">Vol.</label>
                <input type="text" value={volume} onChange={(e) => setVolume(e.target.value)} className="mt-1 w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-accent" placeholder="1" />
              </div>
              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-muted">Pages</label>
                <input type="number" min="1" value={pages} onChange={(e) => setPages(parseInt(e.target.value))} className="mt-1 w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-accent" />
              </div>
            </div>

            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-muted">Digital / E-Book URL</label>
              <input type="text" value={digitalUrl} onChange={(e) => {setDigitalUrl(e.target.value); setIsDigital(!!e.target.value)}} className="mt-1 w-full rounded-lg border border-line bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-accent" placeholder="https://drive..." />
            </div>

            <button
              type="submit"
              disabled={adding}
              className="bg-accent-bg w-full mt-2 rounded-lg py-3 text-sm font-bold text-on-accent transition-all shadow-lg disabled:opacity-50"
            >
              {adding ? "Saving..." : "💾 Save Book"}
            </button>
          </form>
        </div>

        {/* Books List Table */}
        <div className="xl:col-span-3 rounded-xl border border-line bg-surface-2 p-6 shadow-xl overflow-hidden">
          <h3 className="text-lg font-bold text-ink mb-4">Catalog List</h3>
          {loading ? (
            <div className="py-12 flex justify-center">
              <div className="h-8 w-8 animate-spin rounded-full border-4 border-accent border-t-transparent" />
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-body whitespace-nowrap">
                <thead className="text-xs uppercase bg-surface/40 text-muted">
                  <tr>
                    <th className="px-4 py-3">Title</th>
                    <th className="px-4 py-3">Author</th>
                    <th className="px-4 py-3">ISBN</th>
                    <th className="px-4 py-3">Acc No</th>
                    <th className="px-4 py-3">Publisher</th>
                    <th className="px-4 py-3">Category</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3">Digital</th>
                    <th className="px-4 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line/40">
                  {books?.map((book) => (
                    <tr key={book.id} className="hover:bg-surface-2/10">
                      <td className="px-4 py-4 font-semibold text-ink max-w-[200px] truncate" title={book.title}>{book.title}</td>
                      <td className="px-4 py-4 max-w-[150px] truncate">{book.author || "—"}</td>
                      <td className="px-4 py-4">{book.isbn || "—"}</td>
                      <td className="px-4 py-4">{book.accNo || "—"}</td>
                      <td className="px-4 py-4 max-w-[150px] truncate">{book.publisher || "—"}</td>
                      <td className="px-4 py-4">{book.category || book.subject || "—"}</td>
                      <td className="px-4 py-4">
                        <span className={`px-2 py-0.5 rounded text-xs font-semibold ${
                          book.status === "Available" ? "text-positive" : "text-warning"
                        }`}>
                          {book.status || "Available"}
                        </span>
                      </td>
                      <td className="px-4 py-4 text-center">
                        {book.isDigital ? "✅" : ""}
                      </td>
                      <td className="px-4 py-4 text-right space-x-2">
                        {book.isDigital && (
                          <button onClick={() => window.open(book.digitalUrl, "_blank")} className="px-2 py-1 rounded bg-positive/10 border border-positive/30 text-xs font-bold text-positive hover:bg-positive/20 transition-colors">
                            📖 Read
                          </button>
                        )}
                        <button
                          onClick={() => deleteRecord(book.id)}
                          className="px-2 py-1 rounded bg-danger/10 border border-danger/30 text-xs font-bold text-danger hover:bg-danger/20 transition-colors"
                        >
                          🗑️
                        </button>
                      </td>
                    </tr>
                  ))}
                  {books?.length === 0 && (
                    <tr>
                      <td colSpan={9} className="text-center py-8 text-muted">
                        No books cataloged in this institution.
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
