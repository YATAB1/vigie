import logging

from pydantic import ValidationError
from redis import Redis

from vigie_worker.config import Settings
from vigie_worker.handlers import get_handler
from vigie_worker.jobs import Job, JobResult

logger = logging.getLogger(__name__)

# Never echo an oversized or binary message back into logs or queues.
_MAX_LOGGED_CHARS = 200


class Worker:
    """Pops jobs from Redis one at a time and publishes their results."""

    def __init__(self, redis: Redis, settings: Settings) -> None:
        self._redis = redis
        self._settings = settings
        self._running = True

    def stop(self) -> None:
        """Ask the loop to exit after the job in progress (used on SIGTERM)."""
        self._running = False

    def run_forever(self) -> None:
        logger.info("worker listening on %s", self._settings.jobs_queue)
        while self._running:
            self.run_once()
        logger.info("worker stopped")

    def run_once(self) -> bool:
        """Process at most one job. Returns False if the queue stayed empty."""
        popped = self._redis.blpop(
            [self._settings.jobs_queue], timeout=self._settings.poll_timeout_seconds
        )
        if popped is None:
            return False

        _, raw = popped
        job = self._parse(raw)
        if job is None:
            return True

        result = self._execute(job)
        self._redis.rpush(self._settings.results_queue, result.model_dump_json())
        return True

    def _parse(self, raw: bytes | str) -> Job | None:
        try:
            return Job.model_validate_json(raw)
        except ValidationError as exc:
            # A message we cannot even parse has no job id to report against,
            # so park it for inspection instead of dropping it silently.
            self._redis.rpush(self._settings.dead_letter_queue, raw)
            logger.warning(
                "unparseable job moved to dead-letter queue: %s",
                str(exc)[:_MAX_LOGGED_CHARS],
            )
            return None

    def _execute(self, job: Job) -> JobResult:
        run = get_handler(job.type)
        if run is None:
            return JobResult(
                job_id=job.id,
                type=job.type,
                status="failed",
                error=f"unknown job type: {job.type}",
            )
        try:
            data = run(job)
        except Exception as exc:  # one bad job must not kill the worker
            logger.exception("job %s (%s) failed", job.id, job.type)
            return JobResult(
                job_id=job.id,
                type=job.type,
                status="failed",
                error=f"{type(exc).__name__}: {exc}"[:_MAX_LOGGED_CHARS],
            )
        return JobResult(job_id=job.id, type=job.type, status="succeeded", data=data)
