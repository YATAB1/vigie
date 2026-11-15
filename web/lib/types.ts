/** Shapes returned by the Vigie API. */

export interface User {
  id: string;
  email: string;
  role: "USER" | "ADMIN";
  createdAt: string;
}

export interface Verification {
  recordType: string;
  recordName: string;
  recordValue: string;
  /** True while the worker is reading the DNS record. */
  checking: boolean;
  message: string | null;
}

export interface Domain {
  id: string;
  hostname: string;
  status: "PENDING" | "VERIFIED";
  verification: Verification;
  verifiedAt: string | null;
  createdAt: string;
  latestScan: Scan | null;
}

export type Severity = "info" | "low" | "medium" | "high" | "critical";
export type CheckStatus = "pass" | "fail" | "skipped";

export interface Check {
  id: string;
  category: string;
  title: string;
  status: CheckStatus;
  severity: Severity;
  detail: string;
  recommendation: string | null;
}

export interface Scan {
  id: string;
  domainId: string;
  status: "QUEUED" | "SUCCEEDED" | "FAILED";
  score: number | null;
  grade: string | null;
  error: string | null;
  createdAt: string;
  finishedAt: string | null;
  /** Only present on the detailed view of a finished scan. */
  checks: Check[] | null;
}
