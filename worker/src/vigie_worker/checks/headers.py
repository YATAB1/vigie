"""Analysis of HTTP response headers. Pure functions: no network access here."""

import re

from vigie_worker.checks.model import Check, Severity, failed, passed, skipped

CATEGORY = "headers"
Headers = list[tuple[str, str]]

# 180 days, the minimum commonly recommended for HSTS.
_MIN_HSTS_MAX_AGE = 15_552_000
_MAX_AGE = re.compile(r"max-age\s*=\s*\"?(\d+)", re.IGNORECASE)
_VERSION = re.compile(r"\d+\.\d+")
_SEVERITY_ORDER = [Severity.INFO, Severity.LOW, Severity.MEDIUM, Severity.HIGH, Severity.CRITICAL]


def _values(headers: Headers, name: str) -> list[str]:
    return [value.strip() for key, value in headers if key.lower() == name]


def _first(headers: Headers, name: str) -> str | None:
    values = _values(headers, name)
    return values[0] if values else None


def check_hsts(headers: Headers) -> Check:
    title = "HSTS (Strict-Transport-Security)"
    value = _first(headers, "strict-transport-security")
    if value is None:
        return failed(
            "headers.hsts",
            CATEGORY,
            title,
            Severity.HIGH,
            "L'en-tête Strict-Transport-Security est absent : un attaquant sur le réseau "
            "peut forcer une connexion HTTP non chiffrée.",
            "Ajouter : Strict-Transport-Security: max-age=31536000; includeSubDomains",
        )
    match = _MAX_AGE.search(value)
    max_age = int(match.group(1)) if match else 0
    if max_age < _MIN_HSTS_MAX_AGE:
        return failed(
            "headers.hsts",
            CATEGORY,
            title,
            Severity.MEDIUM,
            f"HSTS est présent mais sa durée est trop courte (max-age={max_age}).",
            "Utiliser max-age=31536000 (un an).",
        )
    return passed("headers.hsts", CATEGORY, title, f"HSTS actif (max-age={max_age}).")


def check_csp(headers: Headers) -> Check:
    title = "Content-Security-Policy"
    if _first(headers, "content-security-policy") is None:
        return failed(
            "headers.csp",
            CATEGORY,
            title,
            Severity.MEDIUM,
            "Aucune Content-Security-Policy : rien ne limite les scripts qu'une page peut "
            "charger, ce qui aggrave l'impact d'une faille XSS.",
            "Définir une politique, par exemple : Content-Security-Policy: default-src 'self'",
        )
    return passed("headers.csp", CATEGORY, title, "Une Content-Security-Policy est définie.")


def check_content_type_options(headers: Headers) -> Check:
    title = "X-Content-Type-Options"
    value = _first(headers, "x-content-type-options")
    if value is None or value.lower() != "nosniff":
        return failed(
            "headers.content_type_options",
            CATEGORY,
            title,
            Severity.LOW,
            "Le navigateur peut deviner le type d'un fichier au lieu de respecter celui "
            "annoncé par le serveur.",
            "Ajouter : X-Content-Type-Options: nosniff",
        )
    return passed("headers.content_type_options", CATEGORY, title, "nosniff est actif.")


def check_clickjacking(headers: Headers) -> Check:
    title = "Protection contre le clickjacking"
    frame_options = (_first(headers, "x-frame-options") or "").lower()
    csp = (_first(headers, "content-security-policy") or "").lower()
    if frame_options in ("deny", "sameorigin") or "frame-ancestors" in csp:
        return passed(
            "headers.clickjacking",
            CATEGORY,
            title,
            "Le site contrôle qui peut l'afficher dans une iframe.",
        )
    return failed(
        "headers.clickjacking",
        CATEGORY,
        title,
        Severity.MEDIUM,
        "Le site peut être affiché dans une iframe par n'importe quel autre site.",
        "Ajouter : X-Frame-Options: DENY, ou la directive CSP frame-ancestors 'none'",
    )


def check_referrer_policy(headers: Headers) -> Check:
    title = "Referrer-Policy"
    value = _first(headers, "referrer-policy")
    if value is None:
        return failed(
            "headers.referrer_policy",
            CATEGORY,
            title,
            Severity.LOW,
            "Sans Referrer-Policy, l'adresse complète des pages peut être transmise "
            "aux sites externes.",
            "Ajouter : Referrer-Policy: strict-origin-when-cross-origin",
        )
    if "unsafe-url" in value.lower():
        return failed(
            "headers.referrer_policy",
            CATEGORY,
            title,
            Severity.LOW,
            "La politique unsafe-url transmet l'adresse complète des pages à tous les sites.",
            "Utiliser : Referrer-Policy: strict-origin-when-cross-origin",
        )
    return passed("headers.referrer_policy", CATEGORY, title, f"Politique définie : {value}.")


def check_permissions_policy(headers: Headers) -> Check:
    title = "Permissions-Policy"
    if _first(headers, "permissions-policy") is None:
        return failed(
            "headers.permissions_policy",
            CATEGORY,
            title,
            Severity.INFO,
            "Aucune Permissions-Policy : les fonctions du navigateur (caméra, micro, "
            "géolocalisation) ne sont pas restreintes.",
            "Ajouter : Permissions-Policy: camera=(), microphone=(), geolocation=()",
        )
    return passed("headers.permissions_policy", CATEGORY, title, "Une politique est définie.")


def check_disclosure(headers: Headers) -> Check:
    title = "Divulgation de versions"
    leaks = []
    server = _first(headers, "server")
    if server and _VERSION.search(server):
        leaks.append(f"Server: {server[:80]}")
    powered_by = _first(headers, "x-powered-by")
    if powered_by:
        leaks.append(f"X-Powered-By: {powered_by[:80]}")
    if leaks:
        return failed(
            "headers.disclosure",
            CATEGORY,
            title,
            Severity.LOW,
            "Le serveur annonce sa technologie ou sa version (" + " ; ".join(leaks) + "), "
            "ce qui aide à cibler des failles connues.",
            "Retirer X-Powered-By et masquer la version dans l'en-tête Server.",
        )
    return passed("headers.disclosure", CATEGORY, title, "Aucune version exposée.")


def check_cookies(headers: Headers) -> Check:
    title = "Attributs des cookies"
    cookies = _values(headers, "set-cookie")
    if not cookies:
        return passed("headers.cookies", CATEGORY, title, "Aucun cookie défini sur cette page.")
    problems: list[str] = []
    worst = Severity.INFO
    for cookie in cookies:
        name = cookie.split("=", 1)[0].strip()[:40]
        attributes = {part.strip().split("=", 1)[0].lower() for part in cookie.split(";")[1:]}
        missing = []
        if "secure" not in attributes:
            missing.append("Secure")
            worst = _worse(worst, Severity.MEDIUM)
        if "httponly" not in attributes:
            missing.append("HttpOnly")
            worst = _worse(worst, Severity.LOW)
        if "samesite" not in attributes:
            missing.append("SameSite")
            worst = _worse(worst, Severity.LOW)
        if missing:
            problems.append(f"{name} (sans {', '.join(missing)})")
    if not problems:
        return passed(
            "headers.cookies",
            CATEGORY,
            title,
            f"{len(cookies)} cookie(s), tous avec Secure, HttpOnly et SameSite.",
        )
    return failed(
        "headers.cookies",
        CATEGORY,
        title,
        worst,
        "Cookies mal protégés : " + " ; ".join(problems[:10]) + ".",
        "Ajouter Secure, HttpOnly et SameSite aux cookies de session.",
    )


def _worse(current: Severity, candidate: Severity) -> Severity:
    if _SEVERITY_ORDER.index(candidate) > _SEVERITY_ORDER.index(current):
        return candidate
    return current


_CONTENT_CHECKS = (
    ("headers.csp", "Content-Security-Policy", check_csp),
    ("headers.content_type_options", "X-Content-Type-Options", check_content_type_options),
    ("headers.clickjacking", "Protection contre le clickjacking", check_clickjacking),
    ("headers.referrer_policy", "Referrer-Policy", check_referrer_policy),
    ("headers.permissions_policy", "Permissions-Policy", check_permissions_policy),
)


def analyze_headers(headers: Headers, *, redirects_to: str | None = None) -> list[Check]:
    """Runs every header check.

    ``redirects_to`` is set when the site answers with a redirect to another host.
    A redirect carries no page, so the headers that protect page content are not
    expected on it: those checks are skipped rather than reported as failures.
    """
    checks = [check_hsts(headers)]
    for check_id, title, check in _CONTENT_CHECKS:
        if redirects_to is None:
            checks.append(check(headers))
        else:
            checks.append(
                skipped(
                    check_id,
                    CATEGORY,
                    title,
                    f"Le site redirige vers {redirects_to}. Ajoutez ce domaine pour l'analyser.",
                )
            )
    checks.append(check_disclosure(headers))
    checks.append(check_cookies(headers))
    return checks
