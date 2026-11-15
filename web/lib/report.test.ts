import { describe, expect, it } from "vitest";
import {
  gradeTone,
  scanErrorText,
  scoreHistory,
  sortChecks,
  summarize,
  summarySentence,
} from "./report";
import type { Check, Scan } from "./types";

function check(partial: Partial<Check>): Check {
  return {
    id: "x",
    category: "headers",
    title: "Titre",
    status: "pass",
    severity: "info",
    detail: "",
    recommendation: null,
    ...partial,
  };
}

function scan(partial: Partial<Scan>): Scan {
  return {
    id: "s",
    domainId: "d",
    status: "SUCCEEDED",
    score: 80,
    grade: "B",
    error: null,
    createdAt: "2026-11-14T10:00:00Z",
    finishedAt: null,
    checks: null,
    ...partial,
  };
}

describe("sortChecks", () => {
  it("puts failures first by severity, then skipped, then passed", () => {
    const sorted = sortChecks([
      check({ id: "ok", status: "pass" }),
      check({ id: "low", status: "fail", severity: "low" }),
      check({ id: "skipped", status: "skipped" }),
      check({ id: "critical", status: "fail", severity: "critical" }),
      check({ id: "medium", status: "fail", severity: "medium" }),
    ]);

    expect(sorted.map((c) => c.id)).toEqual(["critical", "medium", "low", "skipped", "ok"]);
  });

  it("does not modify the original list", () => {
    const original = [check({ id: "b", status: "pass" }), check({ id: "a", status: "fail" })];
    sortChecks(original);
    expect(original.map((c) => c.id)).toEqual(["b", "a"]);
  });
});

describe("summarize", () => {
  it("counts each outcome and leaves advice out of the problems", () => {
    const summary = summarize([
      check({ status: "fail", severity: "high" }),
      check({ status: "fail", severity: "info" }),
      check({ status: "pass" }),
      check({ status: "skipped" }),
    ]);

    expect(summary).toEqual({ failed: 2, passed: 1, skipped: 1, problems: 1 });
  });

  it("writes a sentence that agrees in number", () => {
    expect(summarySentence({ failed: 0, passed: 3, skipped: 0, problems: 0 })).toBe(
      "Aucun problème détecté.",
    );
    expect(summarySentence({ failed: 1, passed: 0, skipped: 0, problems: 1 })).toBe(
      "1 problème à corriger.",
    );
    expect(summarySentence({ failed: 4, passed: 0, skipped: 0, problems: 4 })).toBe(
      "4 problèmes à corriger.",
    );
  });
});

describe("gradeTone", () => {
  it("maps grades to three tones", () => {
    expect(["A", "B", "C", "D", "F", null].map(gradeTone)).toEqual([
      "good",
      "good",
      "fair",
      "poor",
      "poor",
      "poor",
    ]);
  });
});

describe("scoreHistory", () => {
  it("keeps finished scans only, oldest first", () => {
    const history = scoreHistory([
      scan({ id: "new", score: 90, createdAt: "2026-11-14T10:00:00Z" }),
      scan({ id: "failed", status: "FAILED", score: null, createdAt: "2026-11-13T10:00:00Z" }),
      scan({ id: "queued", status: "QUEUED", score: null, createdAt: "2026-11-15T10:00:00Z" }),
      scan({ id: "old", score: 60, createdAt: "2026-11-12T10:00:00Z" }),
    ]);

    expect(history.map((p) => [p.id, p.score])).toEqual([
      ["old", 60],
      ["new", 90],
    ]);
  });
});

describe("scanErrorText", () => {
  it("explains the failures a domain owner can fix", () => {
    expect(scanErrorText("TargetNotAllowedError: x")).toContain("adresse privée");
    expect(scanErrorText("TargetUnresolvableError: x")).toContain("aucune adresse IP");
    expect(scanErrorText("The scan timed out")).toContain("Relancez");
  });

  it("keeps unknown errors as they are", () => {
    expect(scanErrorText("Something else")).toBe("Something else");
    expect(scanErrorText(null)).toBe("Aucun détail disponible.");
  });
});
