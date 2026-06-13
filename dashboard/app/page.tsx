"use client";

import { Eraser, Ghost, Trash2 } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import LiveFeed from "@/components/LiveFeed";
import OnboardingPanel from "@/components/OnboardingPanel";
import PolicyPanel from "@/components/PolicyPanel";
import StatsCards from "@/components/StatsCards";
import TrafficChart from "@/components/TrafficChart";
import { useLiveFeed } from "@/hooks/useLiveFeed";
import { api, PARENT_ID } from "@/lib/api";
import type { Category, LiveEvent, Profile, Summary } from "@/lib/types";

export default function DashboardPage() {
  const [profiles, setProfiles] = useState<Profile[]>([]);
  const [selectedClientId, setSelectedClientId] = useState<string | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [initialEvents, setInitialEvents] = useState<LiveEvent[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [busyAction, setBusyAction] = useState<"clear" | "delete" | null>(null);

  const { events, connected } = useLiveFeed(PARENT_ID, initialEvents);

  const selectedProfile = useMemo(
    () => profiles.find((p) => p.clientId === selectedClientId) ?? profiles[0] ?? null,
    [profiles, selectedClientId],
  );

  const refreshSummary = useCallback(() => {
    api.summary(PARENT_ID).then(setSummary).catch(() => undefined);
  }, []);

  useEffect(() => {
    Promise.allSettled([
      api.listProfiles(PARENT_ID),
      api.categories(),
      api.recent(PARENT_ID),
      api.summary(PARENT_ID),
    ]).then(([profilesRes, categoriesRes, recentRes, summaryRes]) => {
      if (profilesRes.status === "fulfilled") setProfiles(profilesRes.value);
      else setLoadError("Cannot reach the PhantomGuard control plane. Is the backend running?");
      if (categoriesRes.status === "fulfilled") setCategories(categoriesRes.value);
      if (recentRes.status === "fulfilled") setInitialEvents(recentRes.value);
      if (summaryRes.status === "fulfilled") setSummary(summaryRes.value);
    });
    const interval = setInterval(refreshSummary, 60_000);
    return () => clearInterval(interval);
  }, [refreshSummary]);

  async function clearAllData() {
    if (!window.confirm("Clear all telemetry data? This cannot be undone.")) return;
    setBusyAction("clear");
    try {
      await api.clearData(PARENT_ID);
      setInitialEvents([]);
      refreshSummary();
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : "Failed to clear data");
    } finally {
      setBusyAction(null);
    }
  }

  async function deleteSelectedChild() {
    if (!selectedProfile) return;
    const removed = selectedProfile.clientId;
    if (
      !window.confirm(
        `Delete ${selectedProfile.childName}? This removes the profile and its policies.`,
      )
    )
      return;
    setBusyAction("delete");
    try {
      await api.deleteProfile(removed);
      setProfiles((prev) => prev.filter((p) => p.clientId !== removed));
      setSelectedClientId(null);
      refreshSummary();
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : "Failed to delete child");
    } finally {
      setBusyAction(null);
    }
  }

  function handlePolicyChanged(updated: {
    blockedCategories: string[];
    blockedDomains: string[];
    allowedDomains: string[];
  }) {
    if (!selectedProfile) return;
    setProfiles((prev) =>
      prev.map((p) => (p.clientId === selectedProfile.clientId ? { ...p, ...updated } : p)),
    );
  }

  return (
    <main className="mx-auto max-w-7xl px-4 py-8">
      <header className="mb-8 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="rounded-xl bg-sky-500/15 p-2.5">
            <Ghost className="h-7 w-7 text-sky-400" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-slate-100">PhantomGuard</h1>
            <p className="text-xs text-slate-500">Encrypted DNS family protection</p>
          </div>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {profiles.length > 0 && (
            <select
              value={selectedProfile?.clientId ?? ""}
              onChange={(e) => setSelectedClientId(e.target.value)}
              className="rounded-lg border border-edge bg-panel px-3 py-2 text-sm text-slate-200 focus:border-sky-500 focus:outline-none"
            >
              {profiles.map((p) => (
                <option key={p.clientId} value={p.clientId}>
                  {p.childName}
                </option>
              ))}
            </select>
          )}

          <button
            onClick={clearAllData}
            disabled={busyAction !== null}
            className="inline-flex items-center gap-1.5 rounded-lg border border-edge bg-panel px-3 py-2 text-sm font-semibold text-slate-300 transition hover:border-amber-500/40 hover:text-amber-300 disabled:opacity-40"
          >
            <Eraser className="h-4 w-4" />
            {busyAction === "clear" ? "Clearing…" : "Clear all data"}
          </button>

          {selectedProfile && (
            <button
              onClick={deleteSelectedChild}
              disabled={busyAction !== null}
              className="inline-flex items-center gap-1.5 rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-sm font-semibold text-rose-300 transition hover:bg-rose-500/20 disabled:opacity-40"
            >
              <Trash2 className="h-4 w-4" />
              {busyAction === "delete" ? "Deleting…" : "Delete child"}
            </button>
          )}
        </div>
      </header>

      {loadError && (
        <p className="mb-6 rounded-lg border border-amber-500/30 bg-amber-500/10 px-4 py-3 text-sm text-amber-300">
          {loadError}
        </p>
      )}

      <div className="space-y-6">
        <StatsCards summary={summary} profileCount={profiles.length} />

        <div className="grid gap-6 lg:grid-cols-5">
          <div className="space-y-6 lg:col-span-3">
            <LiveFeed events={events} connected={connected} />
            <TrafficChart summary={summary} />
          </div>

          <div className="space-y-6 lg:col-span-2">
            <OnboardingPanel
              profile={selectedProfile}
              onProfileCreated={(created) => {
                setProfiles((prev) => [...prev, created]);
                setSelectedClientId(created.clientId);
              }}
            />
            {selectedProfile && (
              <PolicyPanel
                profile={selectedProfile}
                categories={categories}
                onPolicyChanged={handlePolicyChanged}
              />
            )}
          </div>
        </div>
      </div>
    </main>
  );
}
