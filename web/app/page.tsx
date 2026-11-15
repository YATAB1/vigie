"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { CheckList } from "@/components/check-list";
import { GradeLens } from "@/components/grade-lens";
import { useSession } from "@/lib/session";
import type { Check } from "@/lib/types";

const SAMPLE: Check[] = [
  {
    id: "headers.hsts",
    category: "headers",
    title: "HSTS (Strict-Transport-Security)",
    status: "fail",
    severity: "high",
    detail:
      "L'en-tête Strict-Transport-Security est absent : un attaquant sur le réseau peut forcer une connexion HTTP non chiffrée.",
    recommendation: "Ajouter : Strict-Transport-Security: max-age=31536000; includeSubDomains",
  },
  {
    id: "mail.dmarc",
    category: "courriel",
    title: "DMARC",
    status: "fail",
    severity: "low",
    detail: "DMARC est publié avec p=none : les courriels usurpés sont signalés mais toujours livrés.",
    recommendation: "Passer à p=quarantine, puis p=reject une fois les rapports vérifiés.",
  },
  {
    id: "tls.certificate",
    category: "tls",
    title: "Validité du certificat",
    status: "pass",
    severity: "info",
    detail: "Le certificat est valide et correspond au nom de domaine.",
    recommendation: null,
  },
];

export default function HomePage() {
  const { state } = useSession();
  const router = useRouter();

  useEffect(() => {
    if (state.status === "in") router.replace("/domaines");
  }, [state.status, router]);

  return (
    <div className="landing">
      <section className="landing-intro">
        <h1>Voyez ce que votre site expose, avant que quelqu&apos;un d&apos;autre le remarque.</h1>
        <p className="lead">
          Vigie examine le chiffrement, les en-têtes de sécurité et la protection du courriel de
          vos domaines, puis vous dit quoi corriger et comment.
        </p>
        <div className="landing-actions">
          <Link className="button button-primary" href="/inscription">
            Créer un compte
          </Link>
          <Link className="button" href="/connexion">
            Se connecter
          </Link>
        </div>
      </section>

      <section className="landing-sample" aria-labelledby="sample-title">
        <h2 id="sample-title">Un extrait de rapport</h2>
        <p className="sample-host">boutique-exemple.ca</p>
        <div className="report">
          <aside className="report-grade">
            <GradeLens grade="B" score={76} />
          </aside>
          <CheckList checks={SAMPLE} />
        </div>
      </section>
    </div>
  );
}
