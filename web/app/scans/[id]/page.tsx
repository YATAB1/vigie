"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { ScanReport } from "@/components/scan-report";
import { api } from "@/lib/api";
import { formatDateTime } from "@/lib/report";
import { useRequireUser } from "@/lib/session";
import type { Domain, Scan } from "@/lib/types";

export default function ScanPage() {
  const user = useRequireUser();
  const { id } = useParams<{ id: string }>();
  const [scan, setScan] = useState<Scan | null>(null);
  const [domain, setDomain] = useState<Domain | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!user) return;
    let cancelled = false;
    (async () => {
      try {
        const loaded = await api.get<Scan>(`/api/v1/scans/${id}`);
        const owner = await api.get<Domain>(`/api/v1/domains/${loaded.domainId}`);
        if (!cancelled) {
          setScan(loaded);
          setDomain(owner);
        }
      } catch {
        if (!cancelled) setFailed(true);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [user, id]);

  if (failed) {
    return (
      <div className="notice notice-poor" role="alert">
        <strong>Ce rapport est introuvable.</strong>
        <p>
          <Link href="/domaines">Revenir à mes domaines</Link>
        </p>
      </div>
    );
  }
  if (!user || !scan || !domain) return <p className="loading">Chargement…</p>;

  return (
    <div className="domain-page">
      <p className="back">
        <Link href={`/domaines/${domain.id}`}>{domain.hostname}</Link>
      </p>
      <header className="domain-header">
        <h1>Rapport du {formatDateTime(scan.createdAt)}</h1>
      </header>
      {scan.status === "QUEUED" ? (
        <p className="notice notice-info">Ce scan est encore en cours.</p>
      ) : (
        <ScanReport scan={scan} />
      )}
    </div>
  );
}
