import type { Category, LiveEvent, Profile, Summary, TargetType, ToggleAction } from "./types";

export const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080";
export const WS_URL = process.env.NEXT_PUBLIC_WS_URL ?? `${API_BASE}/ws`;
export const PARENT_ID = process.env.NEXT_PUBLIC_PARENT_ID ?? "demo-parent";
// Dev convenience only — in production the admin key must come from an
// authenticated session, never a public env var.
const ADMIN_KEY = process.env.NEXT_PUBLIC_ADMIN_KEY ?? "change-me-in-production";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
  });
  if (!res.ok) {
    const body = await res.text().catch(() => "");
    throw new Error(`${res.status} ${res.statusText}: ${body}`);
  }
  return res.json() as Promise<T>;
}

export const api = {
  listProfiles: (parentId: string) =>
    request<Profile[]>(`/api/profiles?parentId=${encodeURIComponent(parentId)}`),

  generateProfile: (parentId: string, childName: string) =>
    request<Profile>(
      `/api/profiles/generate?parentId=${encodeURIComponent(parentId)}&childName=${encodeURIComponent(childName)}`,
    ),

  getProfile: (clientId: string) => request<Profile>(`/api/profiles/${clientId}`),

  deleteProfile: (clientId: string) =>
    request<{ deleted: string }>(`/api/profiles/${clientId}`, {
      method: "DELETE",
      headers: { "X-PG-Admin-Key": ADMIN_KEY },
    }),

  clearData: (parentId: string) =>
    request<{ deleted: number }>(
      `/api/telemetry?parentId=${encodeURIComponent(parentId)}`,
      { method: "DELETE", headers: { "X-PG-Admin-Key": ADMIN_KEY } },
    ),

  androidOnboarding: (clientId: string) =>
    request<{ privateDnsHostname: string; steps: string[] }>(`/api/profiles/${clientId}/android`),

  categories: () => request<Category[]>(`/api/policies/categories`),

  togglePolicy: (clientId: string, targetType: TargetType, value: string, action: ToggleAction) =>
    request<{ blockedCategories: string[]; blockedDomains: string[]; allowedDomains: string[] }>(
      `/api/policies/toggle`,
      {
        method: "POST",
        headers: { "X-PG-Admin-Key": ADMIN_KEY },
        body: JSON.stringify({ clientId, targetType, value, action }),
      },
    ),

  recent: (parentId: string, limit = 50) =>
    request<LiveEvent[]>(
      `/api/telemetry/recent?parentId=${encodeURIComponent(parentId)}&limit=${limit}`,
    ),

  summary: (parentId: string, hours = 24) =>
    request<Summary>(
      `/api/telemetry/summary?parentId=${encodeURIComponent(parentId)}&hours=${hours}`,
    ),

  mobileConfigUrl: (clientId: string) => `${API_BASE}/api/profiles/${clientId}/mobileconfig`,
};
