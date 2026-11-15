"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { cleanHostname, domainErrorMessage } from "@/lib/forms";
import { formatDateTime, gradeTone } from "@/lib/report";
import { useRequireUser } from "@/lib/session";
import type { Domain } from "@/lib/types";

export default function DomainsPage() {
  const user = useRequireUser();
  const [domains, setDomains] = useState<Domain[] | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [hostname, setHostname] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [reloadKey, setReloadKey] = useState(0);
  const reload = useCallback(() => setReloadKey((key) => key + 1), []);

  useEffect(() => {
    if (!user) return;
    let cancelled = false;
    api
      .get<Domain[]>("/api/v1/domains")
      .then((loaded) => {
        if (cancelled) return;
        setDomains(loaded);
        setLoadFailed(false);
      })
      .catch(() => !cancelled && setLoadFailed(true));
    return () => {
      cancelled = true;
    };
  }, [user, reloadKey]);

  async function handleAdd(event: React.FormEvent) {
    event.preventDefault();
    const cleaned = cleanHostname(hostname);
    if (!cleaned) {
      setError("Entrez un nom de domaine, par exemple exemple.com.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await api.post<Domain>("/api/v1/domains", { hostname: cleaned });
      setHostname("");
      reload();
    } catch (caught) {
      setError(domainErrorMessage(caught));
    } finally {
      setBusy(false);
    }
  }

  if (!user) return <p className="loading">Chargement…</p>;

  return (
    <div className="domains">
      <h1>Mes domaines</h1>

      <form className="add-domain" onSubmit={handleAdd} noValidate>
        <label className="field">
          <span>Ajouter un domaine</span>
          <div className="field-row">
            <input
              type="text"
              name="hostname"
              inputMode="url"
              autoCapitalize="none"
              autoCorrect="off"
              spellCheck={false}
              placeholder="exemple.com"
              value={hostname}
              onChange={(event) => setHostname(event.target.value)}
            />
            <button type="submit" className="button button-primary" disabled={busy}>
              {busy ? "Ajout…" : "Ajouter"}
            </button>
          </div>
        </label>
        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}
      </form>

      {loadFailed && (
        <p className="notice notice-poor" role="alert">
          La liste n&apos;a pas pu être chargée.{" "}
          <button type="button" className="link-button" onClick={reload}>
            Réessayer
          </button>
        </p>
      )}

      {domains && domains.length === 0 && (
        <p className="empty">
          Aucun domaine pour l&apos;instant. Ajoutez le premier ci-dessus : vous prouverez ensuite
          qu&apos;il est à vous, puis vous pourrez l&apos;analyser.
        </p>
      )}

      {domains && domains.length > 0 && (
        <ul className="domain-list">
          {domains.map((domain) => (
            <li key={domain.id}>
              <Link href={`/domaines/${domain.id}`} className="domain-row">
                <span className="domain-host">{domain.hostname}</span>
                <DomainState domain={domain} />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function DomainState({ domain }: { domain: Domain }) {
  if (domain.status === "PENDING") {
    return <span className="domain-state">Propriété à prouver</span>;
  }
  const scan = domain.latestScan;
  if (!scan || scan.grade === null) {
    return <span className="domain-state">Pas encore analysé</span>;
  }
  return (
    <span className="domain-state">
      <span className="domain-date">{formatDateTime(scan.finishedAt ?? scan.createdAt)}</span>
      <span className="grade-chip" data-tone={gradeTone(scan.grade)}>
        <span className="sr-only">Note </span>
        {scan.grade}
      </span>
    </span>
  );
}
