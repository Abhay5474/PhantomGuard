"use client";

import { BarChart3 } from "lucide-react";
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { Summary } from "@/lib/types";

export default function TrafficChart({ summary }: { summary: Summary | null }) {
  const byHour = new Map<string, { hour: string; allowed: number; blocked: number }>();
  for (const point of summary?.perHour ?? []) {
    const entry = byHour.get(point.hour) ?? { hour: point.hour, allowed: 0, blocked: 0 };
    if (point.status === "BLOCKED") entry.blocked += point.count;
    else entry.allowed += point.count;
    byHour.set(point.hour, entry);
  }
  const data = Array.from(byHour.values()).map((d) => ({
    ...d,
    label: d.hour.slice(11) + ":00",
  }));

  return (
    <section className="rounded-xl border border-edge bg-panel p-5">
      <div className="mb-4 flex items-center gap-2">
        <BarChart3 className="h-5 w-5 text-sky-400" />
        <h2 className="text-sm font-semibold uppercase tracking-wider text-slate-300">
          Query Volume — Last 24h
        </h2>
      </div>
      <div className="h-56">
        {data.length === 0 ? (
          <div className="flex h-full items-center justify-center text-sm text-slate-500">
            No historical data yet
          </div>
        ) : (
          <ResponsiveContainer width="100%" height="100%">
            <AreaChart data={data}>
              <defs>
                <linearGradient id="allowedFill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#34d399" stopOpacity={0.5} />
                  <stop offset="100%" stopColor="#34d399" stopOpacity={0.05} />
                </linearGradient>
                <linearGradient id="blockedFill" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="0%" stopColor="#fb7185" stopOpacity={0.6} />
                  <stop offset="100%" stopColor="#fb7185" stopOpacity={0.05} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
              <XAxis dataKey="label" stroke="#64748b" fontSize={11} tickLine={false} />
              <YAxis stroke="#64748b" fontSize={11} tickLine={false} allowDecimals={false} />
              <Tooltip
                contentStyle={{
                  backgroundColor: "#111a2e",
                  border: "1px solid #1e293b",
                  borderRadius: 8,
                  color: "#e2e8f0",
                }}
              />
              <Area
                type="monotone"
                dataKey="allowed"
                name="Allowed"
                stroke="#34d399"
                fill="url(#allowedFill)"
                strokeWidth={2}
              />
              <Area
                type="monotone"
                dataKey="blocked"
                name="Blocked"
                stroke="#fb7185"
                fill="url(#blockedFill)"
                strokeWidth={2}
              />
            </AreaChart>
          </ResponsiveContainer>
        )}
      </div>
    </section>
  );
}
