"""Registry mapping a job type to the function that executes it."""

from collections.abc import Callable
from typing import Any

from vigie_worker import __version__
from vigie_worker.config import Settings
from vigie_worker.dns import lookup_txt
from vigie_worker.hostnames import is_valid_hostname, is_valid_record_name
from vigie_worker.jobs import Job
from vigie_worker.scan import ScanEnvironment, run_scan

Handler = Callable[[Job], dict[str, Any]]

_HANDLERS: dict[str, Handler] = {}


def handler(job_type: str) -> Callable[[Handler], Handler]:
    """Decorator registering ``func`` as the handler for ``job_type``."""

    def register(func: Handler) -> Handler:
        if job_type in _HANDLERS:
            raise ValueError(f"duplicate handler for job type {job_type!r}")
        _HANDLERS[job_type] = func
        return func

    return register


def get_handler(job_type: str) -> Handler | None:
    return _HANDLERS.get(job_type)


def _hostname_from(job: Job, key: str, is_valid: Callable[[str], bool]) -> str:
    value = job.payload.get(key)
    # The API validates hostnames too. The worker checks again because it is the
    # component that actually opens connections: it trusts nothing it receives.
    if not isinstance(value, str) or not is_valid(value):
        raise ValueError(f"invalid {key}")
    return value


@handler("ping")
def ping(job: Job) -> dict[str, Any]:
    """Round-trip check used to verify the API -> Redis -> worker pipeline."""
    return {"pong": True, "worker_version": __version__, "echo": job.payload}


@handler("dns_txt")
def dns_txt(job: Job) -> dict[str, Any]:
    """Reads TXT records. The API uses the answer to verify domain ownership."""
    name = _hostname_from(job, "name", is_valid_record_name)
    return {"name": name, "records": lookup_txt(name)}


@handler("scan")
def scan(job: Job) -> dict[str, Any]:
    hostname = _hostname_from(job, "hostname", is_valid_hostname)
    settings = Settings()
    env = ScanEnvironment(
        allow_private=settings.allow_private_targets,
        timeout=settings.scan_timeout_seconds,
    )
    return run_scan(hostname, env)
