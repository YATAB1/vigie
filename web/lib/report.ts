import type { Check, Scan, Severity } from "./types";

const SEVERITY_RANK: Record<Severity, number> = {
  critical: 4,
  high: 3,
  medium: 2,
  low: 1,
  info: 0,
};

export const SEVERITY_LABEL: Record<Severity, string> = {
  critical: "Critique",
  high: "Élevée",
  medium: "Moyenne",
  low: "Faible",
  info: "Conseil",
};

const CATEGORY_LABEL: Record<string, string> = {
  tls: "Chiffrement",
  headers: "En-têtes HTTP",
  transport: "Transport",
  courriel: "Courriel",
};

export function categoryLabel(category: string): string {
  return CATEGORY_LABEL[category] ?? category;
}

/** Problems first, most severe at the top; then what could not be checked; then what passed. */
export function sortChecks(checks: Check[]): Check[] {
  const group = (check: Check) => (check.status === "fail" ? 0 : check.status === "skipped" ? 1 : 2);
  return [...checks].sort(
    (a, b) =>
      group(a) - group(b) ||
      SEVERITY_RANK[b.severity] - SEVERITY_RANK[a.severity] ||
      a.title.localeCompare(b.title, "fr"),
  );
}

export interface ReportSummary {
  failed: number;
  passed: number;
  skipped: number;
  /** Failed checks that cost points (everything except advice). */
  problems: number;
}

export function summarize(checks: Check[]): ReportSummary {
  const failed = checks.filter((c) => c.status === "fail");
  return {
    failed: failed.length,
    passed: checks.filter((c) => c.status === "pass").length,
    skipped: checks.filter((c) => c.status === "skipped").length,
    problems: failed.filter((c) => c.severity !== "info").length,
  };
}

export type GradeTone = "good" | "fair" | "poor";

export function gradeTone(grade: string | null): GradeTone {
  if (grade === "A" || grade === "B") return "good";
  if (grade === "C") return "fair";
  return "poor";
}

export function summarySentence(summary: ReportSummary): string {
  if (summary.problems === 0) return "Aucun problème détecté.";
  if (summary.problems === 1) return "1 problème à corriger.";
  return `${summary.problems} problèmes à corriger.`;
}

/** Scores of finished scans, oldest first, for the history chart. */
export function scoreHistory(scans: Scan[]): Array<{ id: string; score: number; at: string }> {
  return scans
    .filter((scan) => scan.status === "SUCCEEDED" && scan.score !== null)
    .map((scan) => ({ id: scan.id, score: scan.score as number, at: scan.createdAt }))
    .sort((a, b) => a.at.localeCompare(b.at));
}

const DATE_TIME = new Intl.DateTimeFormat("fr-CA", { dateStyle: "long", timeStyle: "short" });

export function formatDateTime(iso: string): string {
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? "" : DATE_TIME.format(date);
}

/**
 * The worker reports failures as "ExceptionName: detail". Turn the common ones
 * into an explanation the domain owner can act on.
 */
export function scanErrorText(error: string | null): string {
  if (!error) return "Aucun détail disponible.";
  if (error.startsWith("TargetNotAllowedError")) {
    return "Ce domaine pointe vers une adresse privée ou réservée. Vigie n'analyse que les sites accessibles publiquement.";
  }
  if (error.startsWith("TargetUnresolvableError")) {
    return "Le nom de domaine n'a aucune adresse IP. Vérifiez qu'un enregistrement A ou AAAA existe.";
  }
  if (error === "The scan timed out") {
    return "Le scan n'a reçu aucune réponse à temps. Relancez-le dans quelques minutes.";
  }
  return error;
}
