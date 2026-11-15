import { formatDateTime, scanErrorText, summarize, summarySentence } from "@/lib/report";
import type { Scan } from "@/lib/types";
import { CheckList } from "./check-list";
import { GradeLens } from "./grade-lens";

/** Full report of one finished scan. */
export function ScanReport({ scan }: { scan: Scan }) {
  if (scan.status === "FAILED") {
    return (
      <div className="notice notice-poor" role="status">
        <strong>Le scan n&apos;a pas abouti.</strong>
        <p>{scanErrorText(scan.error)}</p>
      </div>
    );
  }
  if (scan.status !== "SUCCEEDED" || scan.grade === null || scan.score === null) {
    return null;
  }
  const checks = scan.checks ?? [];
  const summary = summarize(checks);

  return (
    <div className="report">
      <aside className="report-grade">
        <GradeLens grade={scan.grade} score={scan.score} />
        <p className="report-summary">{summarySentence(summary)}</p>
        <dl className="report-counts">
          <div>
            <dt>À corriger</dt>
            <dd>{summary.failed}</dd>
          </div>
          <div>
            <dt>Conforme</dt>
            <dd>{summary.passed}</dd>
          </div>
          <div>
            <dt>Non vérifié</dt>
            <dd>{summary.skipped}</dd>
          </div>
        </dl>
        <p className="report-date">Scan du {formatDateTime(scan.finishedAt ?? scan.createdAt)}</p>
      </aside>
      <CheckList checks={checks} />
    </div>
  );
}
