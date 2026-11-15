"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useCallback, useEffect, useState } from "react";
import { ScanReport } from "@/components/scan-report";
import { ScoreHistory } from "@/components/score-history";
import { VerificationSteps } from "@/components/verification-steps";
import { api, ApiError } from "@/lib/api";
import { scanErrorMessage } from "@/lib/forms";
import { useRequireUser } from "@/lib/session";
import type { Domain, Scan } from "@/lib/types";
import { usePolling } from "@/lib/use-polling";

type LoadState = "loading" | "ready" | "missing" | "failed";

interface DomainPageData {
  domain: Domain;
  scans: Scan[];
  /** Detailed view of the most recent finished scan, if any. */
  report: Scan | null;
}

async function fetchDomainPage(id: string): Promise<DomainPageData> {
  const [domain, scans] = await Promise.all([
    api.get<Domain>(`/api/v1/domains/${id}`),
    api.get<Scan[]>(`/api/v1/domains/${id}/scans`),
  ]);
  const latestFinished = scans.find((scan) => scan.status !== "QUEUED");
  const report = latestFinished ? await api.get<Scan>(`/api/v1/scans/${latestFinished.id}`) : null;
  return { domain, scans, report };
}

export default function DomainPage() {
  const user = useRequireUser();
  const router = useRouter();
  const { id } = useParams<{ id: string }>();

  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [domain, setDomain] = useState<Domain | null>(null);
  const [scans, setScans] = useState<Scan[]>([]);
  const [report, setReport] = useState<Scan | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [verifying, setVerifying] = useState(false);
  const [starting, setStarting] = useState(false);
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  const apply = useCallback((page: DomainPageData) => {
    setDomain(page.domain);
    setScans(page.scans);
    setReport(page.report);
    setLoadState("ready");
  }, []);

  const applyFailure = useCallback((caught: unknown) => {
    const missing = caught instanceof ApiError && (caught.status === 404 || caught.status === 400);
    setLoadState(missing ? "missing" : "failed");
  }, []);

  const load = useCallback(
    () => fetchDomainPage(id).then(apply).catch(applyFailure),
    [id, apply, applyFailure],
  );

  useEffect(() => {
    if (!user) return;
    let cancelled = false;
    fetchDomainPage(id)
      .then((page) => !cancelled && apply(page))
      .catch((caught: unknown) => !cancelled && applyFailure(caught));
    return () => {
      cancelled = true;
    };
  }, [user, id, apply, applyFailure]);

  // While the worker reads the DNS record, ask the API for the outcome.
  usePolling(domain?.verification.checking === true, load, { maxTicks: 20 });

  // While a scan is running, wait for its report.
  const scanRunning = scans[0]?.status === "QUEUED";
  usePolling(scanRunning, load, { maxTicks: 90 });

  async function handleVerify() {
    setVerifying(true);
    setActionError(null);
    try {
      setDomain(await api.post<Domain>(`/api/v1/domains/${id}/verify`));
    } catch {
      setActionError("La vérification n'a pas pu être lancée. Réessayez dans un instant.");
    } finally {
      setVerifying(false);
    }
  }

  async function handleScan() {
    setStarting(true);
    setActionError(null);
    try {
      await api.post<Scan>(`/api/v1/domains/${id}/scans`);
      await load();
    } catch (caught) {
      setActionError(scanErrorMessage(caught));
    } finally {
      setStarting(false);
    }
  }

  async function handleDelete() {
    try {
      await api.delete(`/api/v1/domains/${id}`);
      router.replace("/domaines");
    } catch {
      setActionError("Le domaine n'a pas pu être supprimé. Réessayez dans un instant.");
      setConfirmingDelete(false);
    }
  }

  if (!user || loadState === "loading") return <p className="loading">Chargement…</p>;

  if (loadState === "missing" || loadState === "failed" || !domain) {
    return (
      <div className="notice notice-poor" role="alert">
        <strong>
          {loadState === "missing" ? "Ce domaine est introuvable." : "Le domaine n'a pas pu être chargé."}
        </strong>
        <p>
          <Link href="/domaines">Revenir à mes domaines</Link>
        </p>
      </div>
    );
  }

  const verified = domain.status === "VERIFIED";

  return (
    <div className="domain-page">
      <p className="back">
        <Link href="/domaines">Mes domaines</Link>
      </p>
      <header className="domain-header">
        <h1>{domain.hostname}</h1>
        {verified && (
          <button
            type="button"
            className="button button-primary"
            onClick={handleScan}
            disabled={starting || scanRunning}
          >
            {scanRunning ? "Scan en cours…" : report ? "Relancer un scan" : "Lancer un scan"}
          </button>
        )}
      </header>

      <div aria-live="polite">
        {actionError && <p className="notice notice-poor">{actionError}</p>}
        {scanRunning && (
          <p className="notice notice-info">
            Analyse de {domain.hostname} en cours. Le rapport s&apos;affichera ici, en général en
            moins d&apos;une minute.
          </p>
        )}
      </div>

      {!verified && <VerificationSteps domain={domain} busy={verifying} onVerify={handleVerify} />}

      {verified && !report && !scanRunning && (
        <p className="empty">
          Ce domaine est vérifié. Lancez un premier scan pour obtenir sa note et la liste des
          points à corriger.
        </p>
      )}

      {report && <ScanReport scan={report} />}

      <ScoreHistory scans={scans} />

      <section className="danger">
        {confirmingDelete ? (
          <p>
            Supprimer {domain.hostname} et tous ses rapports ?{" "}
            <button type="button" className="button button-danger" onClick={handleDelete}>
              Supprimer définitivement
            </button>{" "}
            <button type="button" className="button" onClick={() => setConfirmingDelete(false)}>
              Annuler
            </button>
          </p>
        ) : (
          <button type="button" className="link-button" onClick={() => setConfirmingDelete(true)}>
            Supprimer ce domaine
          </button>
        )}
      </section>
    </div>
  );
}
