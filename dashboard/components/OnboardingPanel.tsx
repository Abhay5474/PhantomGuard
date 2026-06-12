"use client";

import { Apple, Copy, Download, Smartphone, UserPlus } from "lucide-react";
import { useState } from "react";
import { api, PARENT_ID } from "@/lib/api";
import type { Profile } from "@/lib/types";

export default function OnboardingPanel({
  profile,
  onProfileCreated,
}: {
  profile: Profile | null;
  onProfileCreated: (profile: Profile) => void;
}) {
  const [childName, setChildName] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  async function createProfile(e: React.FormEvent) {
    e.preventDefault();
    if (!childName.trim()) return;
    setBusy(true);
    setError(null);
    try {
      const created = await api.generateProfile(PARENT_ID, childName.trim());
      onProfileCreated(created);
      setChildName("");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create profile");
    } finally {
      setBusy(false);
    }
  }

  async function copyHostname() {
    if (!profile) return;
    await navigator.clipboard.writeText(profile.dotHostname);
    setCopied(true);
    setTimeout(() => setCopied(false), 1500);
  }

  return (
    <section className="rounded-xl border border-edge bg-panel">
      <header className="flex items-center gap-2 border-b border-edge px-5 py-4">
        <UserPlus className="h-5 w-5 text-sky-400" />
        <h2 className="text-sm font-semibold uppercase tracking-wider text-slate-300">
          Device Onboarding
        </h2>
      </header>

      <div className="space-y-5 p-5">
        <form onSubmit={createProfile} className="flex gap-2">
          <input
            value={childName}
            onChange={(e) => setChildName(e.target.value)}
            placeholder="Child's name"
            maxLength={64}
            className="flex-1 rounded-lg border border-edge bg-surface px-3 py-2 text-sm text-slate-200 placeholder:text-slate-600 focus:border-sky-500 focus:outline-none"
          />
          <button
            type="submit"
            disabled={busy || !childName.trim()}
            className="rounded-lg bg-sky-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-sky-500 disabled:opacity-40"
          >
            {busy ? "Creating…" : "Add Child"}
          </button>
        </form>
        {error && <p className="text-xs text-rose-400">{error}</p>}

        {profile && (
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="rounded-lg border border-edge bg-surface p-4">
              <div className="mb-2 flex items-center gap-2 text-slate-300">
                <Apple className="h-4 w-4" />
                <h3 className="text-sm font-semibold">iPhone / iPad / Mac</h3>
              </div>
              <p className="mb-3 text-xs text-slate-500">
                Open this link in Safari on the device and install the configuration profile. All
                DNS traffic is then encrypted (DoH) and protected — no app required.
              </p>
              <a
                href={api.mobileConfigUrl(profile.clientId)}
                className="inline-flex items-center gap-1.5 rounded-lg bg-slate-700 px-3 py-2 text-xs font-semibold text-white transition hover:bg-slate-600"
              >
                <Download className="h-3.5 w-3.5" /> Download .mobileconfig
              </a>
            </div>

            <div className="rounded-lg border border-edge bg-surface p-4">
              <div className="mb-2 flex items-center gap-2 text-slate-300">
                <Smartphone className="h-4 w-4" />
                <h3 className="text-sm font-semibold">Android</h3>
              </div>
              <p className="mb-3 text-xs text-slate-500">
                Settings → Network &amp; Internet → Private DNS → hostname (DoT):
              </p>
              <button
                onClick={copyHostname}
                className="flex w-full items-center justify-between gap-2 rounded-lg bg-slate-800 px-3 py-2 text-left font-mono text-[11px] text-sky-300 transition hover:bg-slate-700"
              >
                <span className="truncate">{profile.dotHostname}</span>
                <span className="flex shrink-0 items-center gap-1 text-slate-400">
                  <Copy className="h-3.5 w-3.5" />
                  {copied ? "Copied" : "Copy"}
                </span>
              </button>
            </div>
          </div>
        )}
      </div>
    </section>
  );
}
