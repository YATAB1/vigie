"""Registry mapping a job type to the function that executes it."""

from collections.abc import Callable
from typing import Any

from vigie_worker import __version__
from vigie_worker.jobs import Job

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


@handler("ping")
def ping(job: Job) -> dict[str, Any]:
    """Round-trip check used to verify the API -> Redis -> worker pipeline."""
    return {"pong": True, "worker_version": __version__, "echo": job.payload}
