"use client";

import clsx from "clsx";
import { Activity, ShieldBan, ShieldCheck, TriangleAlert, Wifi, WifiOff } from "lucide-react";
import type { LiveEvent } from "@/lib/types";

const statusStyles: Record<string, { row: string; badge: string; label: string }> = {
  ALLOWED: {
    row: "border-l-emerald-500/60",
    badge: "bg-emerald-500/15 text-emerald-400",
    label: "Allowed",
  },
  BLOCKED: {
    row: "border-l-rose-500 bg-rose-500/5",
    badge: "bg-rose-500/15 text-rose-400",
    label: "Blocked",
  },
  ERROR: {
    row: "border-l-amber-500/60",
    badge: "bg-amber-500/15 text-amber-400",
    label: "Error",
  },
};

function StatusIcon({ status }: { status: string }) {
  if (status === "BLOCKED") return <ShieldBan className="h-4 w-4 text-rose-400" />;
  if (status === "ERROR") return <TriangleAlert className="h-4 w-4 text-amber-400" />;
  return <ShieldCheck className="h-4 w-4 text-emerald-400" />;
}

export default function LiveFeed({
  events,
  connected,
}: {
  events: LiveEvent[];
  connected: boolean;
}) {
  return (
    <section className="rounded-xl border border-edge bg-panel">
      <header className="flex items-center justify-between border-b border-edge px-5 py-4">
        <div className="flex items-center gap-2">
          <Activity className="h-5 w-5 text-sky-400" />
          <h2 className="text-sm font-semibold uppercase tracking-wider text-slate-300">
            Live DNS Activity
          </h2>
        </div>
        <span
          className={clsx(
            "flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-medium",
            connected ? "bg-emerald-500/15 text-emerald-400" : "bg-slate-600/20 text-slate-400",
          )}
        >
          {connected ? <Wifi className="h-3.5 w-3.5" /> : <WifiOff className="h-3.5 w-3.5" />}
          {connected ? "Live" : "Reconnecting…"}
        </span>
      </header>

      <ul className="max-h-[520px] divide-y divide-edge/60 overflow-y-auto">
        {events.length === 0 && (
          <li className="px-5 py-10 text-center text-sm text-slate-500">
            Waiting for DNS activity from protected devices…
          </li>
        )}
        {events.map((event, i) => {
          const style = statusStyles[event.status] ?? statusStyles.ALLOWED;
          return (
            <li
              key={`${event.timestamp}-${event.domain}-${i}`}
              className={clsx("flex items-center gap-3 border-l-2 px-5 py-3", style.row)}
            >
              <StatusIcon status={event.status} />
              <div className="min-w-0 flex-1">
                <p className="truncate font-mono text-sm text-slate-200">{event.domain}</p>
                <p className="text-xs text-slate-500">
                  {event.childName} · {event.queryType}
                  {event.blockReason ? ` · ${event.blockReason}` : ""}
                </p>
              </div>
              <div className="flex flex-col items-end gap-1">
                <span className={clsx("rounded-full px-2 py-0.5 text-xs font-semibold", style.badge)}>
                  {style.label}
                </span>
                <time className="text-[11px] text-slate-500">
                  {new Date(event.timestamp).toLocaleTimeString()}
                </time>
              </div>
            </li>
          );
        })}
      </ul>
    </section>
  );
}
