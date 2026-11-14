from vigie_worker.checks.headers import (
    analyze_headers,
    check_clickjacking,
    check_cookies,
    check_csp,
    check_disclosure,
    check_hsts,
)
from vigie_worker.checks.model import Severity, Status

GOOD = [
    ("Strict-Transport-Security", "max-age=31536000; includeSubDomains"),
    ("Content-Security-Policy", "default-src 'self'; frame-ancestors 'none'"),
    ("X-Content-Type-Options", "nosniff"),
    ("Referrer-Policy", "strict-origin-when-cross-origin"),
    ("Permissions-Policy", "camera=()"),
    ("Server", "nginx"),
    ("Set-Cookie", "session=abc; Path=/; Secure; HttpOnly; SameSite=Lax"),
]


def by_id(checks):
    return {check.id: check for check in checks}


def test_well_configured_site_passes_every_check():
    assert all(check.status is Status.PASS for check in analyze_headers(GOOD))


def test_site_without_any_header_fails_the_important_checks():
    checks = by_id(analyze_headers([]))
    assert checks["headers.hsts"].severity is Severity.HIGH
    assert checks["headers.csp"].severity is Severity.MEDIUM
    assert checks["headers.clickjacking"].severity is Severity.MEDIUM
    assert checks["headers.content_type_options"].status is Status.FAIL
    assert checks["headers.cookies"].status is Status.PASS  # no cookie, nothing to protect


def test_header_names_are_case_insensitive():
    assert check_hsts([("strict-transport-security", "max-age=31536000")]).status is Status.PASS


def test_short_hsts_is_reported_but_less_severe_than_missing():
    check = check_hsts([("Strict-Transport-Security", "max-age=300")])
    assert (check.status, check.severity) == (Status.FAIL, Severity.MEDIUM)


def test_hsts_without_max_age_is_a_failure():
    assert check_hsts([("Strict-Transport-Security", "includeSubDomains")]).status is Status.FAIL


def test_frame_options_or_csp_frame_ancestors_both_prevent_clickjacking():
    assert check_clickjacking([("X-Frame-Options", "DENY")]).status is Status.PASS
    assert (
        check_clickjacking([("Content-Security-Policy", "frame-ancestors 'self'")]).status
        is Status.PASS
    )
    assert check_clickjacking([("X-Frame-Options", "ALLOWALL")]).status is Status.FAIL


def test_csp_presence():
    assert check_csp([("Content-Security-Policy", "default-src 'self'")]).status is Status.PASS
    assert check_csp([]).status is Status.FAIL


def test_version_disclosure():
    assert check_disclosure([("Server", "Apache/2.4.41 (Ubuntu)")]).status is Status.FAIL
    assert check_disclosure([("X-Powered-By", "PHP/8.1.2")]).status is Status.FAIL
    assert check_disclosure([("Server", "cloudflare")]).status is Status.PASS


def test_cookie_without_secure_is_medium_and_names_the_cookie():
    check = check_cookies([("Set-Cookie", "sid=1; HttpOnly; SameSite=Strict")])
    assert (check.status, check.severity) == (Status.FAIL, Severity.MEDIUM)
    assert "sid" in check.detail
    assert "abc" not in check.detail


def test_cookie_values_never_appear_in_the_report():
    check = check_cookies([("Set-Cookie", "sid=SECRET-SESSION-VALUE; Path=/")])
    assert "SECRET-SESSION-VALUE" not in check.detail


def test_every_cookie_is_examined():
    check = check_cookies(
        [
            ("Set-Cookie", "good=1; Secure; HttpOnly; SameSite=Lax"),
            ("Set-Cookie", "bad=1; Secure; SameSite=Lax"),
        ]
    )
    assert (check.status, check.severity) == (Status.FAIL, Severity.LOW)
    assert "bad" in check.detail and "good" not in check.detail


def test_cross_site_redirect_skips_page_content_checks_only():
    checks = by_id(analyze_headers([], redirects_to="https://www.example.com/"))
    assert checks["headers.csp"].status is Status.SKIPPED
    assert checks["headers.clickjacking"].status is Status.SKIPPED
    assert "www.example.com" in checks["headers.csp"].detail
    # HSTS still applies to a redirect response.
    assert checks["headers.hsts"].status is Status.FAIL
