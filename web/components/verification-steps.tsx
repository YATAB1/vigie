"use client";

import type { Domain } from "@/lib/types";
import { CopyButton } from "./copy-button";

interface Props {
  domain: Domain;
  busy: boolean;
  onVerify: () => void;
}

/** The three things to do, in order, to prove a domain is yours. */
export function VerificationSteps({ domain, busy, onVerify }: Props) {
  const { verification } = domain;
  const checking = busy || verification.checking;

  return (
    <section className="verify" aria-labelledby="verify-title">
      <h2 id="verify-title">Prouvez que ce domaine est à vous</h2>
      <p className="lead">
        Vigie n&apos;analyse que les domaines de leur propriétaire. La preuve se fait par un
        enregistrement DNS, que seul le gestionnaire du domaine peut créer.
      </p>
      <ol className="steps">
        <li>
          Ouvrez la gestion DNS de <strong>{domain.hostname}</strong> chez votre registraire ou
          votre hébergeur.
        </li>
        <li>
          Ajoutez cet enregistrement de type {verification.recordType} :
          <dl className="record">
            <div>
              <dt>Nom</dt>
              <dd>
                <code>{verification.recordName}</code>
                <CopyButton value={verification.recordName} label="Copier le nom" />
              </dd>
            </div>
            <div>
              <dt>Valeur</dt>
              <dd>
                <code>{verification.recordValue}</code>
                <CopyButton value={verification.recordValue} label="Copier la valeur" />
              </dd>
            </div>
          </dl>
        </li>
        <li>
          Lancez la vérification. Un nouvel enregistrement peut mettre quelques minutes à être
          visible.
          <div className="steps-action">
            <button
              type="button"
              className="button button-primary"
              onClick={onVerify}
              disabled={checking}
            >
              {checking ? "Vérification en cours…" : "Vérifier le domaine"}
            </button>
          </div>
        </li>
      </ol>
      <div aria-live="polite">
        {!checking && verification.message && (
          <p className="notice notice-fair">{verification.message}</p>
        )}
      </div>
    </section>
  );
}
