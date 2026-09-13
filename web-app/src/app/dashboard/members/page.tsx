"use client";

import React, { useState, useRef } from "react";
import { useTenantCollection } from "@/lib/firestore-hooks";
import Papa from "papaparse";
import * as XLSX from "xlsx";

export default function MembersPage() {
  const { data: members, loading, addRecord, deleteRecord } = useTenantCollection("members");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [role, setRole] = useState("Student");
  const [adding, setAdding] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) return;
    setAdding(true);
    try {
      await addRecord({
        name: name.trim(),
        email: email.trim(),
        phone: phone.trim(),
        role: role,
        joinedAt: Date.now(),
      });
      setName("");
      setEmail("");
      setPhone("");
      setRole("Student");
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
              if (!row.name && !row.Name && !row.MemberName) continue;
              await addRecord({
                name: row.name || row.Name || row.MemberName || "",
                email: row.email || row.Email || "",
                phone: row.phone || row.Phone || row.Contact || "",
                role: row.role || row.Role || "Student",
                joinedAt: Date.now(),
              });
              successCount++;
            }
            alert(`Successfully imported ${successCount} members from CSV.`);
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
              if (!row.name && !row.Name && !row.MemberName) continue;
              await addRecord({
                name: row.name || row.Name || row.MemberName || "",
                email: row.email || row.Email || "",
                phone: row.phone || row.Phone || row.Contact || "",
                role: row.role || row.Role || "Student",
                joinedAt: Date.now(),
              });
              successCount++;
            }
            alert(`Successfully imported ${successCount} members from Excel.`);
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
    e.target.value = '';
  };

  const handleExportCSV = () => {
    if (!members || members.length === 0) return;
    const csv = Papa.unparse(members);
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.setAttribute("download", "Members_Directory_Export.csv");
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const comingSoon = () => alert("This feature is coming soon!");

  return (
    <div className="space-y-6 animate-in fade-in duration-500 max-w-7xl mx-auto">
      <div className="flex flex-col md:flex-row md:items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-extrabold text-[#E8EEF8]">👥 Members Directory</h1>
          <p className="text-sm text-slate-400">Manage students, faculty, and librarian roles</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <input 
            type="file" 
            ref={fileInputRef} 
            className="hidden" 
            accept=".csv, .xlsx, .xls"
            onChange={handleFileChange}
          />
          <button onClick={handleImportClick} disabled={adding} className="bg-[#1E3050] text-[#A0B4CC] border border-[#1E3050] rounded-md px-3 py-1.5 text-xs font-semibold hover:bg-[#2A4166] transition-colors shadow">
            📥 Import
          </button>
          <button onClick={handleExportCSV} className="bg-[#1E3050] text-[#A0B4CC] border border-[#1E3050] rounded-md px-3 py-1.5 text-xs font-semibold hover:bg-[#2A4166] transition-colors shadow">
            📤 Export CSV
          </button>
          <button onClick={comingSoon} className="bg-[#1E3050] text-[#A0B4CC] border border-[#1E3050] rounded-md px-3 py-1.5 text-xs font-semibold hover:bg-[#2A4166] transition-colors shadow">
            🖨️ Batch ID Cards
          </button>
          <button onClick={comingSoon} className="bg-[#1E3050] text-[#A0B4CC] border border-[#1E3050] rounded-md px-3 py-1.5 text-xs font-semibold hover:bg-[#2A4166] transition-colors shadow">
            ✉️ Bulk Email
          </button>
        </div>
      </div>

      <div className="flex gap-4">
        <input 
          type="text"
          placeholder="🔍 Search by name, email, or phone..."
          className="flex-1 rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]"
        />
        <select className="rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2 text-sm text-[#E8EEF8] outline-none">
          <option>All Roles</option>
          <option>Student</option>
          <option>Faculty</option>
          <option>Librarian</option>
        </select>
        <select className="rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2 text-sm text-[#E8EEF8] outline-none">
          <option>All Status</option>
          <option>Active</option>
          <option>Suspended</option>
        </select>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Add Member Form */}
        <div className="rounded-xl border border-blue-950 bg-[#070F1E] p-6 shadow-xl h-fit">
          <h3 className="text-lg font-bold text-white mb-4">Register New Member</h3>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-slate-400">Full Name</label>
              <input
                type="text"
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
                className="mt-2 w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2.5 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]"
                placeholder="e.g. Ahmad Khan"
              />
            </div>
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-slate-400">Email Address</label>
              <input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                className="mt-2 w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2.5 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]"
                placeholder="e.g. ahmad@gmail.com"
              />
            </div>
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-slate-400">Phone</label>
              <input
                type="text"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                className="mt-2 w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2.5 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]"
                placeholder="e.g. +923001234567"
              />
            </div>
            <div>
              <label className="block text-xs font-bold uppercase tracking-wider text-slate-400">Library Role</label>
              <select
                value={role}
                onChange={(e) => setRole(e.target.value)}
                className="mt-2 w-full rounded-lg border border-[#1E3050] bg-[#0D1F38] px-4 py-2.5 text-sm text-[#E8EEF8] outline-none focus:border-[#C8A84B]"
              >
                <option value="Student">Student</option>
                <option value="Faculty">Faculty</option>
                <option value="Librarian">Librarian</option>
              </select>
            </div>
            <button
              type="submit"
              disabled={adding}
              className="w-full rounded-lg bg-gradient-to-r from-[#1E5FD4] to-[#2872F0] py-3 text-sm font-bold text-white transition-all hover:from-[#2872F0] hover:to-[#3D8EFF] shadow-lg disabled:opacity-50"
            >
              {adding ? "Registering..." : "➕ Register Member"}
            </button>
          </form>
        </div>

        {/* Members List Table */}
        <div className="lg:col-span-2 rounded-xl border border-blue-950 bg-[#070F1E] p-6 shadow-xl overflow-hidden">
          <div className="flex justify-between items-center mb-4">
            <h3 className="text-lg font-bold text-white">Registered Members</h3>
            <button className="px-3 py-1.5 bg-blue-900 hover:bg-blue-800 text-blue-200 text-xs font-bold rounded-lg transition-colors">
              🖨️ Bulk Print All IDs
            </button>
          </div>
          {loading ? (
            <div className="py-12 flex justify-center">
              <div className="h-8 w-8 animate-spin rounded-full border-4 border-[#C8A84B] border-t-transparent" />
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-300">
                <thead className="text-xs uppercase bg-[#0D1F38]/40 text-slate-400">
                  <tr>
                    <th className="px-4 py-3">Name</th>
                    <th className="px-4 py-3">Email</th>
                    <th className="px-4 py-3">Phone</th>
                    <th className="px-4 py-3">Role</th>
                    <th className="px-4 py-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-blue-950/40">
                  {members?.map((member) => (
                    <tr key={member.id} className="hover:bg-blue-950/10">
                      <td className="px-4 py-4 font-semibold text-white">{member.name}</td>
                      <td className="px-4 py-4">{member.email || "—"}</td>
                      <td className="px-4 py-4">{member.phone || "—"}</td>
                      <td className="px-4 py-4">
                        <span className={`px-2 py-0.5 rounded text-xs font-semibold ${
                          member.role === "Librarian" ? "text-amber-400" : "text-blue-400"
                        }`}>
                          {member.role || "Student"}
                        </span>
                      </td>
                      <td className="px-4 py-4 text-right space-x-3">
                        <button
                          onClick={() => alert(`Printing Digital ID for ${member.name}...`)}
                          className="px-3 py-1 rounded bg-emerald-500/10 border border-emerald-500/30 text-xs font-bold text-emerald-400 hover:bg-emerald-500/20 transition-colors"
                        >
                          🖨️ ID
                        </button>
                        <button
                          onClick={() => {
                            // A member holding books still has open loan records
                            // pointing at them; deleting would orphan those.
                            const onLoan = Number(member.booksIssued ?? 0);
                            if (onLoan > 0) {
                              alert(
                                `${member.name} still has ${onLoan} book(s) on loan. Return them first, then delete this member.`,
                              );
                              return;
                            }
                            if (
                              confirm(
                                `Delete ${member.name}?\n\nThey will be removed from this library on every synced device. This cannot be undone.`,
                              )
                            ) {
                              deleteRecord(member.id);
                            }
                          }}
                          className="px-3 py-1 rounded bg-red-500/10 border border-red-500/30 text-xs font-bold text-red-400 hover:bg-red-500/20 transition-colors"
                        >
                          🗑️ Delete
                        </button>
                      </td>
                    </tr>
                  ))}
                  {members?.length === 0 && (
                    <tr>
                      <td colSpan={5} className="text-center py-8 text-slate-500">
                        No registered members in this institution.
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
