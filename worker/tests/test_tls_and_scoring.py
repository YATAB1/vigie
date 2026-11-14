from datetime import UTC, datetime, timedelta

from vigie_worker.checks.model import Severity, Status, failed, passed, skipped
from vigie_worker.checks.tls import TlsObservation, analyze_tls
from vigie_worker.scoring import compute_score, grade_for

NOW = datetime(2026, 11, 14, 12, 0, tzinfo=UTC)


def by_id(checks):
    return {check.id: check for check in checks}


def observation(days_left=90, protocol="TLSv1.3", verify_error=None):
    return TlsObservation(
        reachable=True,
        protocol=protocol,
        verify_error=verify_error,
        not_after=NOW + timedelta(days=days_left, hours=1),
    )


def test_healthy_tls_passes_everything():
    assert all(c.status is Status.PASS for c in analyze_tls(observation(), NOW))


def test_unreachable_https_is_critical_and_stops_there():
    checks = analyze_tls(TlsObservation(reachable=False, error="Connection refused"), NOW)
    assert [(c.id, c.severity) for c in checks] == [("tls.reachable", Severity.CRITICAL)]


def test_rejected_certificate_is_critical():
    checks = by_id(analyze_tls(observation(verify_error="self-signed certificate"), NOW))
    assert checks["tls.certificate"].severity is Severity.CRITICAL
    assert "self-signed" in checks["tls.certificate"].detail


def test_expiry_thresholds():
    def severity(days):
        check = by_id(analyze_tls(observation(days_left=days), NOW))["tls.expiry"]
        return (check.status, check.severity)

    assert severity(-1) == (Status.FAIL, Severity.CRITICAL)
    assert severity(5) == (Status.FAIL, Severity.HIGH)
    assert severity(20) == (Status.FAIL, Severity.MEDIUM)
    assert severity(60)[0] is Status.PASS


def test_obsolete_protocol_is_high():
    checks = by_id(analyze_tls(observation(protocol="TLSv1.1"), NOW))
    assert checks["tls.protocol"].severity is Severity.HIGH
    assert by_id(analyze_tls(observation(protocol="TLSv1.2"), NOW))["tls.protocol"].status is (
        Status.PASS
    )


def fail(severity):
    return failed("x", "c", "t", severity, "d", "r")


def test_score_starts_at_100_and_only_failures_cost_points():
    assert compute_score([]) == 100
    assert compute_score([passed("x", "c", "t", "d"), skipped("y", "c", "t", "d")]) == 100
    assert compute_score([fail(Severity.HIGH), fail(Severity.MEDIUM), fail(Severity.LOW)]) == 66
    assert compute_score([fail(Severity.INFO)]) == 100


def test_score_never_goes_below_zero():
    assert compute_score([fail(Severity.CRITICAL)] * 5) == 0


def test_grades():
    assert [grade_for(s) for s in (100, 90, 89, 75, 60, 40, 39, 0)] == [
        "A",
        "A",
        "B",
        "B",
        "C",
        "D",
        "F",
        "F",
    ]
