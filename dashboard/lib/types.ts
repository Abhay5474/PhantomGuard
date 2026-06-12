export type QueryStatus = "ALLOWED" | "BLOCKED" | "ERROR";

export interface LiveEvent {
  clientId: string;
  childName: string;
  domain: string;
  queryType: string;
  status: QueryStatus;
  blockReason?: string | null;
  latencyMicros: number;
  timestamp: string;
}

export interface Profile {
  clientId: string;
  parentId: string;
  childName: string;
  dohUrl: string;
  dotHostname: string;
  mobileConfigPath: string;
  blockedCategories: string[];
  blockedDomains: string[];
  allowedDomains: string[];
  createdAt: string;
}

export interface Category {
  id: string;
  displayName: string;
  sampleDomains: string[];
}

export interface Summary {
  totalAllowed: number;
  totalBlocked: number;
  topBlockedDomains: { domain: string; count: number }[];
  perHour: { hour: string; status: QueryStatus; count: number }[];
}

export type ToggleAction = "BLOCK" | "UNBLOCK" | "ALLOW" | "UNALLOW";
export type TargetType = "DOMAIN" | "CATEGORY";
