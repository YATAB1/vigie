"""Scans real local HTTPS/HTTP servers, end to end, with no outside network."""

import json
import socket
import ssl
import threading
from datetime import UTC, datetime
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import fakeredis
import pytest
import trustme

from vigie_worker.checks.model import Status
from vigie_worker.config import Settings
from vigie_worker.dns import DnsLookupError
from vigie_worker.net import TargetNotAllowedError
from vigie_worker.scan import ScanEnvironment, run_scan
from vigie_worker.worker import Worker

HOSTNAME = "scan-target.example"

SECURE_HEADERS = {
    "Strict-Transport-Security": "max-age=31536000; includeSubDomains",
    "Content-Security-Policy": "default-src 'self'; frame-ancestors 'none'",
    "X-Content-Type-Options": "nosniff",
    "Referrer-Policy": "no-referrer",
    "Permissions-Policy": "camera=()",
}
GOOD_DNS = {
    HOSTNAME: ["v=spf1 -all"],
    f"_dmarc.{HOSTNAME}": ["v=DMARC1; p=reject"],
}


class Site:
    """A local web server whose behaviour each test configures."""

    def __init__(self, tls_context=None):
        self.status = 200
        self.headers: dict[str, str] = {}
        self.redirects: dict[str, str] = {}
        self.requests: list[tuple[str, str]] = []  # (path, Host header)
        site = self

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                site.requests.append((self.path, self.headers.get("Host", "")))
                if self.path in site.redirects:
                    self.send_response(302)
                    self.send_header("Location", site.redirects[self.path])
                else:
                    self.send_response(site.status)
                for name, value in site.headers.items():
                    self.send_header(name, value)
                self.send_header("Content-Length", "0")
                self.end_headers()

            def version_string(self):
                return "test-server"

            def log_message(self, *args):
                pass

        class QuietServer(ThreadingHTTPServer):
            def handle_error(self, request, client_address):
                pass  # aborted handshakes are expected while probing certificates

        self.server = QuietServer(("127.0.0.1", 0), Handler)
        if tls_context is not None:
            self.server.socket = tls_context.wrap_socket(self.server.socket, server_side=True)
        self.port = self.server.server_address[1]
        threading.Thread(
            target=self.server.serve_forever, kwargs={"poll_interval": 0.02}, daemon=True
        ).start()

    def close(self):
        self.server.shutdown()
        self.server.server_close()


@pytest.fixture(scope="module")
def ca():
    return trustme.CA()


@pytest.fixture
def https_site(ca):
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ca.issue_cert(HOSTNAME).configure_cert(context)
    site = Site(context)
    yield site
    site.close()


@pytest.fixture
def http_site():
    site = Site()
    yield site
    site.close()


def environment(ca, https_site, http_site, dns=GOOD_DNS, trust_ca=True, **overrides):
    def tls_context():
        context = ssl.create_default_context()
        if trust_ca:
            ca.configure_trust(context)
        return context

    def txt_lookup(name):
        if isinstance(dns, Exception):
            raise dns
        return dns.get(name, [])

    settings = {
        "allow_private": True,
        "timeout": 5.0,
        "https_port": https_site.port,
        "http_port": http_site.port,
        "resolver": lambda _host, _port: ["127.0.0.1"],
        "txt_lookup": txt_lookup,
        "tls_context": tls_context,
        "now": lambda: datetime.now(UTC),
    }
    return ScanEnvironment(**(settings | overrides))


def by_id(report):
    return {check["id"]: check for check in report["checks"]}


def test_well_configured_site_gets_the_top_grade(ca, https_site, http_site):
    https_site.headers = SECURE_HEADERS
    http_site.redirects = {"/": f"https://{HOSTNAME}/"}

    report = run_scan(HOSTNAME, environment(ca, https_site, http_site))

    failures = [c["id"] for c in report["checks"] if c["status"] != "pass"]
    assert failures == []
    assert (report["score"], report["grade"]) == (100, "A")
    assert report["scanned_ip"] == "127.0.0.1"
    # The connection went to the pinned IP but named the real host (SNI + Host header).
    assert https_site.requests == [("/", HOSTNAME)]


def test_badly_configured_site_is_reported_in_detail(ca, https_site, http_site):
    https_site.headers = {"X-Powered-By": "PHP/8.1.2", "Set-Cookie": "sid=1"}
    env = environment(ca, https_site, http_site, dns={}, trust_ca=False)

    report = run_scan(HOSTNAME, env)
    checks = by_id(report)

    assert checks["tls.certificate"]["severity"] == "critical"  # unknown authority
    assert checks["headers.hsts"]["status"] == "fail"
    assert checks["headers.csp"]["status"] == "fail"
    assert checks["headers.disclosure"]["status"] == "fail"
    assert checks["headers.cookies"]["status"] == "fail"
    assert checks["http.redirect"]["status"] == "fail"  # plain HTTP answers 200
    assert checks["mail.spf"]["status"] == "fail"
    assert checks["mail.dmarc"]["status"] == "fail"
    assert (report["score"], report["grade"]) == (0, "F")
    assert all(c["recommendation"] for c in report["checks"] if c["status"] == "fail")


def test_report_is_json_serializable(ca, https_site, http_site):
    report = run_scan(HOSTNAME, environment(ca, https_site, http_site))
    assert json.loads(json.dumps(report))["hostname"] == HOSTNAME


def test_private_target_is_refused_before_any_connection(ca, https_site, http_site):
    env = environment(ca, https_site, http_site, allow_private=False)

    with pytest.raises(TargetNotAllowedError):
        run_scan(HOSTNAME, env)

    assert https_site.requests == [] and http_site.requests == []


def test_redirect_on_the_same_host_is_followed(ca, https_site, http_site):
    https_site.headers = SECURE_HEADERS
    https_site.redirects = {"/": "/accueil"}

    report = run_scan(HOSTNAME, environment(ca, https_site, http_site))

    assert [path for path, _ in https_site.requests] == ["/", "/accueil"]
    assert by_id(report)["headers.csp"]["status"] == "pass"


def test_redirect_to_another_host_is_never_followed(ca, https_site, http_site):
    # An attacker-controlled site could redirect the scanner to an internal service.
    https_site.redirects = {"/": "https://169.254.169.254/latest/meta-data/"}

    report = run_scan(HOSTNAME, environment(ca, https_site, http_site))

    assert https_site.requests == [("/", HOSTNAME)]
    assert by_id(report)["headers.csp"]["status"] == "skipped"


def test_redirect_loop_terminates(ca, https_site, http_site):
    https_site.redirects = {"/": "/a", "/a": "/"}

    run_scan(HOSTNAME, environment(ca, https_site, http_site))

    assert len(https_site.requests) == 4  # first request + 3 redirects, then stop


def test_https_down_is_critical_and_scan_still_completes(ca, https_site, http_site):
    env = environment(ca, https_site, http_site, https_port=http_site.port)  # no TLS there

    report = run_scan(HOSTNAME, env)
    checks = by_id(report)

    assert checks["tls.reachable"]["severity"] == "critical"
    assert "headers.hsts" not in checks
    assert "mail.spf" in checks


def test_closed_http_port_is_not_a_problem(ca, https_site, http_site):
    http_site.close()

    report = run_scan(HOSTNAME, environment(ca, https_site, http_site))

    assert by_id(report)["http.redirect"]["status"] == "pass"


def test_dns_failure_skips_mail_checks_without_failing_the_scan(ca, https_site, http_site):
    env = environment(ca, https_site, http_site, dns=DnsLookupError("recherche DNS impossible"))

    checks = by_id(run_scan(HOSTNAME, env))

    assert checks["mail.spf"]["status"] == Status.SKIPPED
    assert checks["mail.dmarc"]["status"] == Status.SKIPPED


# ---------- through the worker ----------


def run_job(job: dict) -> dict:
    settings = Settings(poll_timeout_seconds=1)
    redis = fakeredis.FakeRedis(decode_responses=True)
    redis.rpush(settings.jobs_queue, json.dumps(job))
    Worker(redis, settings).run_once()
    return json.loads(redis.lpop(settings.results_queue))


@pytest.mark.parametrize(
    "hostname", ["127.0.0.1", "localhost", "example.com/admin", "http://example.com", "", None, 42]
)
def test_scan_job_with_an_invalid_hostname_fails_without_scanning(hostname):
    result = run_job({"id": "j1", "type": "scan", "payload": {"hostname": hostname}})

    assert result["status"] == "failed"
    assert "invalid hostname" in result["error"]


def test_scan_job_targeting_an_internal_address_fails(monkeypatch):
    def internal_dns_answer(*_args, **_kwargs):
        return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("10.0.0.5", 443))]

    monkeypatch.setattr("vigie_worker.net.socket.getaddrinfo", internal_dns_answer)

    result = run_job({"id": "j2", "type": "scan", "payload": {"hostname": "intranet.example.com"}})

    assert result["status"] == "failed"
    assert "TargetNotAllowedError" in result["error"]


def test_dns_txt_job_returns_records(monkeypatch):
    monkeypatch.setattr(
        "vigie_worker.handlers.lookup_txt", lambda name: [f"vigie-verification=token-for-{name}"]
    )

    result = run_job(
        {"id": "j3", "type": "dns_txt", "payload": {"name": "_vigie-challenge.example.com"}}
    )

    assert result["status"] == "succeeded"
    assert result["data"]["records"] == [
        "vigie-verification=token-for-_vigie-challenge.example.com"
    ]


def test_dns_txt_job_rejects_an_invalid_name():
    result = run_job({"id": "j4", "type": "dns_txt", "payload": {"name": "10.0.0.1"}})

    assert result["status"] == "failed"
