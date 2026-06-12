"use client";

import clsx from "clsx";
import { Loader2, Plus, Settings2, ShieldBan, ShieldCheck, X } from "lucide-react";
import { useState } from "react";
import { api } from "@/lib/api";
import type { Category, Profile } from "@/lib/types";

export default function PolicyPanel({
  profile,
  categories,
  onPolicyChanged,
}: {
  profile: Profile;
  categories: Category[];
  onPolicyChanged: (updated: {
    blockedCategories: string[];
    blockedDomains: string[];
    allowedDomains: string[];
  }) => void;
}) {
  const [pending, setPending] = useState<string | null>(null);
  const [domainInput, setDomainInput] = useState("");
  const [error, setError] = useState<string | null>(null);

  async function run(key: string, fn: () => Promise<Parameters<typeof onPolicyChanged>[0]>) {
    setPending(key);
    setError(null);
    try {
      onPolicyChanged(await fn());
    } catch (e) {
      setError(e instanceof Error ? e.message : "Policy update failed");
    } finally {
      setPending(null);
    }
  }

  const blockedCategories = new Set(profile.blockedCategories);

  return (
    <section className="rounded-xl border border-edge bg-panel">
      <header className="flex items-center gap-2 border-b border-edge px-5 py-4">
        <Settings2 className="h-5 w-5 text-sky-400" />
        <h2 className="text-sm font-semibold uppercase tracking-wider text-slate-300">
          Protection Rules — {profile.childName}
        </h2>
      </header>

      <div className="space-y-6 p-5">
        <div>
          <h3 className="mb-3 text-xs font-semibold uppercase tracking-wider text-slate-500">
            Content Categories
          </h3>
          <div className="space-y-2">
            {categories.map((category) => {
              const blocked = blockedCategories.has(category.id);
              const key = `cat-${category.id}`;
              return (
                <button
                  key={category.id}
                  disabled={pending !== null}
                  onClick={() =>
                    run(key, () =>
                      api.togglePolicy(
                        profile.clientId,
                        "CATEGORY",
                        category.id,
                        blocked ? "UNBLOCK" : "BLOCK",
                      ),
                    )
                  }
                  className={clsx(
                    "flex w-full items-center justify-between rounded-lg border px-4 py-3 text-left transition",
                    blocked
                      ? "border-rose-500/40 bg-rose-500/10"
                      : "border-edge bg-surface hover:border-slate-600",
                  )}
                >
                  <div>
                    <p className="text-sm font-medium text-slate-200">{category.displayName}</p>
                    <p className="text-xs text-slate-500">
                      e.g. {category.sampleDomains.slice(0, 3).join(", ")}
                    </p>
                  </div>
                  {pending === key ? (
                    <Loader2 className="h-5 w-5 animate-spin text-slate-400" />
                  ) : blocked ? (
                    <span className="flex items-center gap-1 text-xs font-semibold text-rose-400">
                      <ShieldBan className="h-4 w-4" /> Blocked
                    </span>
                  ) : (
                    <span className="flex items-center gap-1 text-xs font-semibold text-emerald-400">
                      <ShieldCheck className="h-4 w-4" /> Allowed
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        </div>

        <div>
          <h3 className="mb-3 text-xs font-semibold uppercase tracking-wider text-slate-500">
            Custom Domain Rules
          </h3>
          <form
            className="mb-3 flex gap-2"
            onSubmit={(e) => {
              e.preventDefault();
              const domain = domainInput.trim().toLowerCase();
              if (!domain) return;
              run(`block-${domain}`, async () => {
                const result = await api.togglePolicy(profile.clientId, "DOMAIN", domain, "BLOCK");
                setDomainInput("");
                return result;
              });
            }}
          >
            <input
              value={domainInput}
              onChange={(e) => setDomainInput(e.target.value)}
              placeholder="e.g. tiktok.com"
              className="flex-1 rounded-lg border border-edge bg-surface px-3 py-2 text-sm text-slate-200 placeholder:text-slate-600 focus:border-sky-500 focus:outline-none"
            />
            <button
              type="submit"
              disabled={pending !== null || !domainInput.trim()}
              className="flex items-center gap-1 rounded-lg bg-rose-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-rose-500 disabled:opacity-40"
            >
              <Plus className="h-4 w-4" /> Block
            </button>
          </form>

          <div className="flex flex-wrap gap-2">
            {profile.blockedDomains.map((domain) => (
              <span
                key={domain}
                className="flex items-center gap-1.5 rounded-full bg-rose-500/15 px-3 py-1 font-mono text-xs text-rose-300"
              >
                {domain}
                <button
                  aria-label={`Unblock ${domain}`}
                  disabled={pending !== null}
                  onClick={() =>
                    run(`unblock-${domain}`, () =>
                      api.togglePolicy(profile.clientId, "DOMAIN", domain, "UNBLOCK"),
                    )
                  }
                  className="rounded-full p-0.5 hover:bg-rose-500/30"
                >
                  <X className="h-3 w-3" />
                </button>
              </span>
            ))}
            {profile.allowedDomains.map((domain) => (
              <span
                key={domain}
                className="flex items-center gap-1.5 rounded-full bg-emerald-500/15 px-3 py-1 font-mono text-xs text-emerald-300"
              >
                {domain} (always allowed)
                <button
                  aria-label={`Remove allow override for ${domain}`}
                  disabled={pending !== null}
                  onClick={() =>
                    run(`unallow-${domain}`, () =>
                      api.togglePolicy(profile.clientId, "DOMAIN", domain, "UNALLOW"),
                    )
                  }
                  className="rounded-full p-0.5 hover:bg-emerald-500/30"
                >
                  <X className="h-3 w-3" />
                </button>
              </span>
            ))}
            {profile.blockedDomains.length === 0 && profile.allowedDomains.length === 0 && (
              <p className="text-xs text-slate-600">No custom rules yet.</p>
            )}
          </div>
        </div>

        {error && (
          <p className="rounded-lg border border-rose-500/30 bg-rose-500/10 px-3 py-2 text-xs text-rose-300">
            {error}
          </p>
        )}
      </div>
    </section>
  );
}
