// components/sidebar.tsx
//
// The navigation that turns this from a single dashboard page into an
// actual management information system: fourteen sections grouped by what
// they are for, rather than one page trying to be all of them at once.

"use client";

import React from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  IconOverview, IconRegistry, IconDistrict, IconBench, IconAlert, IconTrend,
  IconSearch, IconFollowup, IconAnnouncement, IconDocument, IconInspection,
  IconAudit, IconStaff, IconReports,
} from "./icons";

interface NavItem {
  href: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  badge?: number;
}

interface NavGroup {
  label: string;
  items: NavItem[];
}

export function useNavGroups(counts: { pending: number; alerts: number; openFollowups: number }): NavGroup[] {
  return [
    {
      label: "Network",
      items: [
        { href: "/", label: "Overview", icon: IconOverview },
        { href: "/registry", label: "Registry", icon: IconRegistry, badge: counts.pending || undefined },
        { href: "/districts", label: "Districts", icon: IconDistrict },
        { href: "/benchmarking", label: "Benchmarking", icon: IconBench },
        { href: "/alerts", label: "Alerts", icon: IconAlert, badge: counts.alerts || undefined },
        { href: "/trends", label: "Trends", icon: IconTrend },
        { href: "/search", label: "Union Search", icon: IconSearch },
      ],
    },
    {
      label: "Engagement",
      items: [
        { href: "/followups", label: "Follow-ups", icon: IconFollowup, badge: counts.openFollowups || undefined },
        { href: "/announcements", label: "Announcements", icon: IconAnnouncement },
        { href: "/documents", label: "Documents", icon: IconDocument },
        { href: "/inspections", label: "Inspections", icon: IconInspection },
      ],
    },
    {
      label: "Governance",
      items: [
        { href: "/reports", label: "Reports", icon: IconReports },
        { href: "/audit", label: "Audit Log", icon: IconAudit },
        { href: "/staff", label: "Staff & Roles", icon: IconStaff },
      ],
    },
  ];
}

export default function Sidebar({ counts }: { counts: { pending: number; alerts: number; openFollowups: number } }) {
  const pathname = usePathname();
  const groups = useNavGroups(counts);

  return (
    <nav className="hidden w-60 shrink-0 flex-col gap-6 border-r border-slate-800 bg-[#050B14] px-3 py-6 lg:flex">
      {groups.map((group) => (
        <div key={group.label}>
          <p className="mb-2 px-2.5 text-[10px] font-bold uppercase tracking-widest text-slate-600">
            {group.label}
          </p>
          <div className="flex flex-col gap-0.5">
            {group.items.map((item) => {
              const active = pathname === item.href;
              const Icon = item.icon;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`flex items-center justify-between gap-2 rounded-md px-2.5 py-2 text-sm font-medium transition-colors ${
                    active
                      ? "bg-amber-500/10 text-amber-400"
                      : "text-slate-400 hover:bg-slate-800/60 hover:text-slate-200"
                  }`}
                >
                  <span className="flex items-center gap-2.5">
                    <Icon className={`h-4 w-4 ${active ? "text-amber-400" : "text-slate-500"}`} />
                    {item.label}
                  </span>
                  {!!item.badge && (
                    <span className="rounded-full bg-red-500/20 px-1.5 py-0.5 text-[10px] font-bold text-red-400">
                      {item.badge}
                    </span>
                  )}
                </Link>
              );
            })}
          </div>
        </div>
      ))}
    </nav>
  );
}
