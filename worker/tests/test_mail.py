import pytest

from vigie_worker.checks.mail import check_dmarc, check_spf
from vigie_worker.checks.model import Severity, Status


def outcome(check):
    return (check.status, check.severity)


def test_spf_missing():
    assert outcome(check_spf(["google-site-verification=abc"])) == (Status.FAIL, Severity.MEDIUM)


@pytest.mark.parametrize(
    "record",
    [
        "v=spf1 include:_spf.google.com -all",
        "v=spf1 mx ~all",
        "V=SPF1 -all",
        "v=spf1 redirect=_spf.example.com",
    ],
)
def test_spf_with_a_restrictive_policy_passes(record):
    assert check_spf(["unrelated", record]).status is Status.PASS


@pytest.mark.parametrize("record", ["v=spf1 +all", "v=spf1 mx all"])
def test_spf_allowing_everyone_is_high(record):
    assert outcome(check_spf([record])) == (Status.FAIL, Severity.HIGH)


@pytest.mark.parametrize("record", ["v=spf1 mx ?all", "v=spf1 mx"])
def test_spf_without_a_decision_is_medium(record):
    assert outcome(check_spf([record])) == (Status.FAIL, Severity.MEDIUM)


def test_two_spf_records_is_an_error():
    check = check_spf(["v=spf1 -all", "v=spf1 mx -all"])
    assert outcome(check) == (Status.FAIL, Severity.MEDIUM)


def test_dmarc_missing():
    assert outcome(check_dmarc([])) == (Status.FAIL, Severity.MEDIUM)


@pytest.mark.parametrize("policy", ["quarantine", "reject"])
def test_dmarc_enforcing_policy_passes(policy):
    record = f"v=DMARC1; p={policy}; rua=mailto:d@example.com"
    assert check_dmarc([record]).status is Status.PASS


def test_dmarc_monitoring_only_is_low():
    assert outcome(check_dmarc(["v=DMARC1; p=none"])) == (Status.FAIL, Severity.LOW)


def test_dmarc_without_policy_is_medium():
    assert outcome(check_dmarc(["v=DMARC1; rua=mailto:d@example.com"])) == (
        Status.FAIL,
        Severity.MEDIUM,
    )
