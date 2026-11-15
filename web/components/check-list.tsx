import { categoryLabel, SEVERITY_LABEL, sortChecks } from "@/lib/report";
import type { Check } from "@/lib/types";

export function CheckList({ checks }: { checks: Check[] }) {
  const sorted = sortChecks(checks);
  const failed = sorted.filter((check) => check.status === "fail");
  const skipped = sorted.filter((check) => check.status === "skipped");
  const passed = sorted.filter((check) => check.status === "pass");

  return (
    <div className="checks">
      {failed.length > 0 && (
        <section aria-labelledby="checks-failed">
          <h2 id="checks-failed">À corriger</h2>
          <ul className="check-list">
            {failed.map((check) => (
              <li key={check.id} className="check" data-severity={check.severity}>
                <details open={check.severity === "critical" || check.severity === "high"}>
                  <summary>
                    <span className="severity">{SEVERITY_LABEL[check.severity]}</span>
                    <span className="check-title">{check.title}</span>
                    <span className="check-category">{categoryLabel(check.category)}</span>
                  </summary>
                  <p>{check.detail}</p>
                  {check.recommendation && (
                    <p className="check-fix">
                      <strong>Correction.</strong> {check.recommendation}
                    </p>
                  )}
                </details>
              </li>
            ))}
          </ul>
        </section>
      )}

      {skipped.length > 0 && (
        <section aria-labelledby="checks-skipped">
          <h2 id="checks-skipped">Non vérifié</h2>
          <ul className="check-list check-list-plain">
            {skipped.map((check) => (
              <li key={check.id}>
                <span className="check-title">{check.title}</span>
                <span className="check-note">{check.detail}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {passed.length > 0 && (
        <section aria-labelledby="checks-passed">
          <h2 id="checks-passed">Conforme</h2>
          <ul className="check-list check-list-plain">
            {passed.map((check) => (
              <li key={check.id}>
                <span className="check-title">{check.title}</span>
                <span className="check-note">{check.detail}</span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
