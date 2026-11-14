"""Orchestrates one scan: resolve the target once, then run every check group."""

import http.client
import ssl
import time
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Any
from urllib.parse import urlsplit

from vigie_worker import __version__
from vigie_worker.checks.headers import Headers, analyze_headers
from vigie_worker.checks.mail import check_dmarc, check_spf
from vigie_worker.checks.model import Check, Severity, failed, passed, skipped
from vigie_worker.checks.tls import analyze_tls, observe_tls
from vigie_worker.dns import DnsLookupError, TxtLookup, lookup_txt
from vigie_worker.net import Resolver, open_tcp, open_tls, resolve_target, system_resolver
from vigie_worker.scoring import compute_score, grade_for

_USER_AGENT = f"Vigie/{__version__} (security scan requested by the domain owner)"
_MAX_REDIRECTS = 3


@dataclass(frozen=True)
class ScanEnvironment:
    """Everything a scan needs from the outside world. Tests replace these parts."""

    allow_private: bool = False
    timeout: float = 10.0
    https_port: int = 443
    http_port: int = 80
    resolver: Resolver = system_resolver
    txt_lookup: TxtLookup = lookup_txt
    tls_context: Callable[[], ssl.SSLContext] = ssl.create_default_context
    now: Callable[[], datetime] = field(default=lambda: datetime.now(UTC))


@dataclass(frozen=True)
class HttpResponse:
    status: int
    headers: Headers


def run_scan(hostname: str, env: ScanEnvironment) -> dict[str, Any]:
    started = time.monotonic()
    # Resolved once. Every connection below goes to this vetted address.
    ip = resolve_target(
        hostname, env.https_port, allow_private=env.allow_private, resolver=env.resolver
    )

    checks: list[Check] = []
    observation = observe_tls(hostname, ip, env.https_port, env.timeout, env.tls_context())
    checks.extend(analyze_tls(observation, env.now()))
    if observation.reachable:
        checks.extend(_header_checks(hostname, ip, env))
    checks.append(_http_redirect_check(hostname, ip, env))
    checks.extend(_mail_checks(hostname, env))

    score = compute_score(checks)
    return {
        "hostname": hostname,
        "scanned_ip": ip,
        "score": score,
        "grade": grade_for(score),
        "checks": [check.model_dump(mode="json") for check in checks],
        "duration_ms": int((time.monotonic() - started) * 1000),
    }


def _fetch_https(hostname: str, ip: str, path: str, env: ScanEnvironment) -> HttpResponse:
    # The report describes the site's headers even when its certificate is invalid
    # (that problem is reported by the TLS checks), so verification is off here.
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    context.check_hostname = False
    context.verify_mode = ssl.CERT_NONE
    connection = http.client.HTTPSConnection(hostname, env.https_port, timeout=env.timeout)
    connection.sock = open_tls(hostname, ip, env.https_port, context, env.timeout)
    return _request(connection, hostname, path)


def _fetch_http(hostname: str, ip: str, env: ScanEnvironment) -> HttpResponse:
    connection = http.client.HTTPConnection(hostname, env.http_port, timeout=env.timeout)
    connection.sock = open_tcp(ip, env.http_port, env.timeout)
    return _request(connection, hostname, "/")


def _request(connection: http.client.HTTPConnection, hostname: str, path: str) -> HttpResponse:
    try:
        connection.request(
            "GET",
            path,
            headers={
                "Host": hostname,
                "User-Agent": _USER_AGENT,
                "Accept": "*/*",
                "Connection": "close",
            },
        )
        response = connection.getresponse()  # reads the headers only, never the body
        return HttpResponse(response.status, response.getheaders())
    finally:
        connection.close()


def _location(response: HttpResponse) -> str | None:
    for name, value in response.headers:
        if name.lower() == "location":
            return value.strip()
    return None


def _header_checks(hostname: str, ip: str, env: ScanEnvironment) -> list[Check]:
    path = "/"
    try:
        for _ in range(_MAX_REDIRECTS + 1):
            response = _fetch_https(hostname, ip, path, env)
            location = _location(response)
            if not (300 <= response.status < 400 and location):
                return analyze_headers(response.headers)
            target = urlsplit(location)
            same_site = target.scheme in ("", "https") and target.hostname in (None, hostname)
            if not same_site:
                # Redirects to other hosts are never followed: the scanner only
                # contacts the domain whose ownership was proven.
                return analyze_headers(response.headers, redirects_to=location[:200])
            path = (target.path or "/") + (f"?{target.query}" if target.query else "")
        return analyze_headers(response.headers)
    except (OSError, http.client.HTTPException) as exc:
        return [
            skipped(
                "headers.fetch",
                "headers",
                "En-têtes HTTP",
                f"La page d'accueil n'a pas pu être lue en HTTPS ({type(exc).__name__}).",
            )
        ]


def _http_redirect_check(hostname: str, ip: str, env: ScanEnvironment) -> Check:
    check_id, category, title = "http.redirect", "transport", "Redirection HTTP vers HTTPS"
    try:
        response = _fetch_http(hostname, ip, env)
    except (OSError, http.client.HTTPException):
        return passed(
            check_id, category, title, "Le port 80 ne répond pas : rien n'est servi en clair."
        )
    location = _location(response) or ""
    if 300 <= response.status < 400 and location.lower().startswith("https://"):
        return passed(check_id, category, title, "Les requêtes HTTP sont redirigées vers HTTPS.")
    return failed(
        check_id,
        category,
        title,
        Severity.MEDIUM,
        f"Le site répond en HTTP non chiffré (code {response.status}) sans rediriger vers HTTPS.",
        "Rediriger tout le trafic du port 80 vers https:// avec un code 301.",
    )


def _mail_checks(hostname: str, env: ScanEnvironment) -> list[Check]:
    checks = []
    for name, check, check_id, title in (
        (hostname, check_spf, "mail.spf", "SPF"),
        (f"_dmarc.{hostname}", check_dmarc, "mail.dmarc", "DMARC"),
    ):
        try:
            checks.append(check(env.txt_lookup(name)))
        except DnsLookupError as exc:
            checks.append(skipped(check_id, "courriel", title, f"{exc}."))
    return checks
