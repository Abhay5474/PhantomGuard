"use client";

import { Globe, ShieldBan, ShieldCheck, Users } from "lucide-react";
import type { Summary } from "@/lib/types";

export default function StatsCards({
  summary,
  profileCount,
}: {
  summary: Summary | null;
  profileCount: number;
}) {
  const total = (summary?.totalAllowed ?? 0) + (summary?.totalBlocked ?? 0);
  const cards = [
    {
      label: "Queries (24h)",
      value: total.toLocaleString(),
      icon: Globe,
      tint: "text-sky-400 bg-sky-500/10",
    },
    {
      label: "Allowed",
      value: (summary?.totalAllowed ?? 0).toLocaleString(),
      icon: ShieldCheck,
      tint: "text-emerald-400 bg-emerald-500/10",
    },
    {
      label: "Blocked",
      value: (summary?.totalBlocked ?? 0).toLocaleString(),
      icon: ShieldBan,
      tint: "text-rose-400 bg-rose-500/10",
    },
    {
      label: "Protected Devices",
      value: profileCount.toString(),
      icon: Users,
      tint: "text-violet-400 bg-violet-500/10",
    },
  ];

  return (
    <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
      {cards.map(({ label, value, icon: Icon, tint }) => (
        <div key={label} className="rounded-xl border border-edge bg-panel p-5">
          <div className={`mb-3 inline-flex rounded-lg p-2 ${tint}`}>
            <Icon className="h-5 w-5" />
          </div>
          <p className="text-2xl font-bold text-slate-100">{value}</p>
          <p className="text-xs uppercase tracking-wider text-slate-500">{label}</p>
        </div>
      ))}
    </div>
  );
}
