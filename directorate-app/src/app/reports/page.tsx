"use client";

import React from "react";
import { useDirectorateNetwork } from "@/lib/directorate";
import { rollupByDistrict, computeAlerts, scoreCompliance } from "@/lib/analytics";
import { PageHeader, Card, Button, Spinner, numberFmt, currencyFmt } from "@/components/ui";
import { IconReports, IconDownload } from "@/components/icons";

/**
 * The one page in this app meant to be printed. Everything else here is a
 * screen; this is also a document — so it gets its own print stylesheet
 * (globals.css hides the sidebar/header/footer under @media print) and
 * deliberately light, high-contrast styling for that mode rather than the
 * portal's usual dark theme, which prints as a mostly-black page.
 */
export default function Reports() {
  const { colleges, totals, loading } = useDirectorateNetwork();
  const districts = rollupByDistrict(colleges);
  const alerts = computeAlerts(colleges);
  const compliance = colleges.map(scoreCompliance);
  const avgCompliance = compliance.length ? Math.round(compliance.reduce((n, c) => n + c.score, 0) / compliance.length) : 0;

  const exportWorkbook = () => {
    const sheet = (name: string, header: string[], rows: (string | number)[][]) => {
      const lines = [header, ...rows].map((r) => r.map((v) => `"${String(v).replace(/"/g, '""')}"`).join(","));
      return `# ${name}\n${lines.join("\n")}`;
    };
    const content = [
      sheet("Overview", ["Metric", "Value"], [
        ["Institutions", totals.colleges], ["Books", totals.books], ["Members", totals.members],
        ["Active Loans", totals.activeLoans], ["Overdue", totals.overdue], ["Fines Outstanding", totals.finesOutstanding],
      ]),
      sheet("Registry", ["Institution", "District", "Books", "Members", "Active Loans", "Overdue", "Compliance %"],
        colleges.map((c) => [c.name, c.district || "", c.booksCount, c.membersCount, c.activeLoans, c.overdueCount, scoreCompliance(c).score])),
      sheet("Districts", ["District", "Institutions", "Books", "Members", "Overdue"],
        districts.map((d) => [d.district, d.colleges, d.books, d.members, d.overdue])),
      sheet("Alerts", ["Institution", "Severity", "Message"], alerts.map((a) => [a.name, a.severity, a.message])),
    ].join("\n\n");
    const blob = new Blob([content], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `nexlib-mis-report-${new Date().toISOString().slice(0, 10)}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  if (loading) return <Spinner label="Compiling report…" />;

  return (
    <div>
      <PageHeader
        title="Reports"
        description="A single official summary, formatted to print, plus a multi-section export for further analysis."
        actions={
          <>
            <Button variant="secondary" size="sm" icon={<IconDownload className="h-3.5 w-3.5" />} onClick={exportWorkbook}>
              Export workbook (CSV)
            </Button>
            <Button variant="primary" size="sm" icon={<IconReports className="h-3.5 w-3.5" />} onClick={() => window.print()}>
              Print report
            </Button>
          </>
        }
      />

      <div id="printable-report" className="rounded-lg border border-slate-800 bg-white p-8 text-slate-900 print:border-0 print:p-0">
        <div className="mb-6 flex items-center justify-between border-b-2 border-slate-900 pb-4">
          <div>
            <h1 className="text-xl font-bold">NEXLIB — Network Status Report</h1>
            <p className="text-xs text-slate-600">Higher Education Department · Khyber Pakhtunkhwa</p>
          </div>
          <p className="text-xs text-slate-500">Generated {new Date().toLocaleString("en-PK")}</p>
        </div>

        <section className="mb-6">
          <h2 className="mb-2 text-sm font-bold uppercase tracking-wide text-slate-700">Network Summary</h2>
          <table className="w-full text-xs">
            <tbody>
              <ReportRow label="Approved institutions" value={numberFmt.format(totals.colleges)} />
              <ReportRow label="Reporting (synced at least once)" value={`${totals.reporting} / ${totals.colleges}`} />
              <ReportRow label="Total books" value={numberFmt.format(totals.books)} />
              <ReportRow label="Total members" value={numberFmt.format(totals.members)} />
              <ReportRow label="Active loans" value={numberFmt.format(totals.activeLoans)} />
              <ReportRow label="Overdue loans" value={numberFmt.format(totals.overdue)} />
              <ReportRow label="Fines outstanding" value={currencyFmt(totals.finesOutstanding)} />
              <ReportRow label="Average compliance score" value={`${avgCompliance}%`} />
            </tbody>
          </table>
        </section>

        <section className="mb-6">
          <h2 className="mb-2 text-sm font-bold uppercase tracking-wide text-slate-700">By District</h2>
          <table className="w-full text-xs">
            <thead>
              <tr className="border-b border-slate-300 text-left">
                <th className="py-1.5">District</th><th className="py-1.5 text-right">Institutions</th>
                <th className="py-1.5 text-right">Books</th><th className="py-1.5 text-right">Members</th>
              </tr>
            </thead>
            <tbody>
              {districts.map((d) => (
                <tr key={d.district} className="border-b border-slate-100">
                  <td className="py-1.5">{d.district}</td>
                  <td className="py-1.5 text-right">{d.colleges}</td>
                  <td className="py-1.5 text-right">{numberFmt.format(d.books)}</td>
                  <td className="py-1.5 text-right">{numberFmt.format(d.members)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        <section>
          <h2 className="mb-2 text-sm font-bold uppercase tracking-wide text-slate-700">
            Items Requiring Attention ({alerts.length})
          </h2>
          {alerts.length === 0 ? (
            <p className="text-xs text-slate-500">None at time of report.</p>
          ) : (
            <ul className="space-y-1 text-xs">
              {alerts.map((a, i) => (
                <li key={i}>
                  <span className="font-semibold uppercase text-slate-500">[{a.severity}]</span> {a.name} — {a.message}
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>

      <Card className="mt-5 border-slate-800/60 bg-transparent">
        <p className="text-[11px] text-slate-600">
          The workbook export writes each section as its own labelled block in one CSV file rather than a true
          multi-sheet .xlsx — no spreadsheet library is bundled with this portal, and a CSV opens correctly in
          Excel, Sheets, or LibreOffice without one.
        </p>
      </Card>
    </div>
  );
}

function ReportRow({ label, value }: { label: string; value: string }) {
  return (
    <tr className="border-b border-slate-100">
      <td className="py-1.5 text-slate-600">{label}</td>
      <td className="py-1.5 text-right font-semibold">{value}</td>
    </tr>
  );
}
