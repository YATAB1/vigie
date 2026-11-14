from enum import StrEnum

from pydantic import BaseModel


class Severity(StrEnum):
    INFO = "info"
    LOW = "low"
    MEDIUM = "medium"
    HIGH = "high"
    CRITICAL = "critical"


class Status(StrEnum):
    PASS = "pass"  # noqa: S105 - a check outcome, not a credential
    FAIL = "fail"
    # The check could not be evaluated (lookup failure, cross-site redirect...).
    SKIPPED = "skipped"


class Check(BaseModel):
    id: str
    category: str
    title: str
    status: Status
    severity: Severity = Severity.INFO
    detail: str
    recommendation: str | None = None


def passed(check_id: str, category: str, title: str, detail: str) -> Check:
    return Check(id=check_id, category=category, title=title, status=Status.PASS, detail=detail)


def failed(
    check_id: str,
    category: str,
    title: str,
    severity: Severity,
    detail: str,
    recommendation: str,
) -> Check:
    return Check(
        id=check_id,
        category=category,
        title=title,
        status=Status.FAIL,
        severity=severity,
        detail=detail,
        recommendation=recommendation,
    )


def skipped(check_id: str, category: str, title: str, detail: str) -> Check:
    return Check(id=check_id, category=category, title=title, status=Status.SKIPPED, detail=detail)
