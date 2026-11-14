"""Turns a list of checks into a score out of 100 and a letter grade."""

from vigie_worker.checks.model import Check, Severity, Status

PENALTIES = {
    Severity.CRITICAL: 40,
    Severity.HIGH: 20,
    Severity.MEDIUM: 10,
    Severity.LOW: 4,
    Severity.INFO: 0,
}

_GRADES = ((90, "A"), (75, "B"), (60, "C"), (40, "D"))


def compute_score(checks: list[Check]) -> int:
    """Only failed checks cost points. Skipped checks are neutral."""
    penalty = sum(PENALTIES[c.severity] for c in checks if c.status is Status.FAIL)
    return max(0, 100 - penalty)


def grade_for(score: int) -> str:
    for threshold, grade in _GRADES:
        if score >= threshold:
            return grade
    return "F"
