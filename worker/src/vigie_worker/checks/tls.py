"""TLS checks: certificate validity, expiry, and negotiated protocol version."""

import ssl
from dataclasses import dataclass
from datetime import datetime

from cryptography import x509

from vigie_worker.checks.model import Check, Severity, failed, passed
from vigie_worker.net import open_tls

CATEGORY = "tls"

_EXPIRY_HIGH_DAYS = 14
_EXPIRY_MEDIUM_DAYS = 30


@dataclass(frozen=True)
class TlsObservation:
    """What one TLS handshake revealed. ``reachable`` is False if none succeeded."""

    reachable: bool
    error: str | None = None
    protocol: str | None = None
    verify_error: str | None = None
    not_after: datetime | None = None


def observe_tls(
    hostname: str, ip: str, port: int, timeout: float, context: ssl.SSLContext
) -> TlsObservation:
    """Handshakes with certificate verification on, then without if it fails,
    so an invalid certificate can still be described."""
    verify_error = None
    try:
        with open_tls(hostname, ip, port, context, timeout) as sock:
            return _describe(sock, None)
    except ssl.SSLCertVerificationError as exc:
        verify_error = exc.verify_message or "certificat refusé"
    except (OSError, ssl.SSLError) as exc:
        return TlsObservation(reachable=False, error=_short(exc))

    unverified = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    unverified.check_hostname = False
    unverified.verify_mode = ssl.CERT_NONE  # only to read the rejected certificate
    try:
        with open_tls(hostname, ip, port, unverified, timeout) as sock:
            return _describe(sock, verify_error)
    except (OSError, ssl.SSLError):
        return TlsObservation(reachable=True, verify_error=verify_error)


def _describe(sock: ssl.SSLSocket, verify_error: str | None) -> TlsObservation:
    not_after = None
    der = sock.getpeercert(binary_form=True)
    if der:
        not_after = x509.load_der_x509_certificate(der).not_valid_after_utc
    return TlsObservation(
        reachable=True, protocol=sock.version(), verify_error=verify_error, not_after=not_after
    )


def _short(exc: BaseException) -> str:
    return (str(exc) or type(exc).__name__)[:200]


def analyze_tls(observation: TlsObservation, now: datetime) -> list[Check]:
    if not observation.reachable:
        return [
            failed(
                "tls.reachable",
                CATEGORY,
                "Connexion HTTPS",
                Severity.CRITICAL,
                f"Impossible d'établir une connexion HTTPS sur le port 443 ({observation.error}).",
                "Activer HTTPS avec un certificat valide et TLS 1.2 ou plus récent.",
            )
        ]
    return [
        passed("tls.reachable", CATEGORY, "Connexion HTTPS", "Le site répond en HTTPS."),
        _check_certificate(observation),
        _check_expiry(observation, now),
        _check_protocol(observation),
    ]


def _check_certificate(observation: TlsObservation) -> Check:
    title = "Validité du certificat"
    if observation.verify_error:
        return failed(
            "tls.certificate",
            CATEGORY,
            title,
            Severity.CRITICAL,
            f"Le certificat est refusé par les navigateurs : {observation.verify_error}.",
            "Installer un certificat valide pour ce nom de domaine, émis par une autorité "
            "reconnue (Let's Encrypt est gratuit).",
        )
    return passed(
        "tls.certificate",
        CATEGORY,
        title,
        "Le certificat est valide et correspond au nom de domaine.",
    )


def _check_expiry(observation: TlsObservation, now: datetime) -> Check:
    title = "Expiration du certificat"
    if observation.not_after is None:
        return failed(
            "tls.expiry",
            CATEGORY,
            title,
            Severity.INFO,
            "La date d'expiration du certificat n'a pas pu être lue.",
            "Vérifier manuellement la date d'expiration.",
        )
    days = (observation.not_after - now).days
    expires = observation.not_after.date().isoformat()
    if days < 0:
        return failed(
            "tls.expiry",
            CATEGORY,
            title,
            Severity.CRITICAL,
            f"Le certificat a expiré le {expires}.",
            "Renouveler le certificat immédiatement.",
        )
    if days < _EXPIRY_HIGH_DAYS:
        return failed(
            "tls.expiry",
            CATEGORY,
            title,
            Severity.HIGH,
            f"Le certificat expire dans {days} jour(s), le {expires}.",
            "Renouveler le certificat et automatiser le renouvellement.",
        )
    if days < _EXPIRY_MEDIUM_DAYS:
        return failed(
            "tls.expiry",
            CATEGORY,
            title,
            Severity.MEDIUM,
            f"Le certificat expire dans {days} jours, le {expires}.",
            "Planifier le renouvellement, idéalement de façon automatique.",
        )
    return passed("tls.expiry", CATEGORY, title, f"Expire dans {days} jours, le {expires}.")


def _check_protocol(observation: TlsObservation) -> Check:
    title = "Version de TLS"
    protocol = observation.protocol or "inconnue"
    if protocol in ("TLSv1.3", "TLSv1.2"):
        return passed("tls.protocol", CATEGORY, title, f"Connexion négociée en {protocol}.")
    return failed(
        "tls.protocol",
        CATEGORY,
        title,
        Severity.HIGH,
        f"Connexion négociée en {protocol}, une version obsolète.",
        "Désactiver TLS 1.0 et 1.1 ; n'accepter que TLS 1.2 et 1.3.",
    )
