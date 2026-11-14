import json

import fakeredis
import pytest

from vigie_worker.config import Settings
from vigie_worker.handlers import handler
from vigie_worker.worker import Worker


@pytest.fixture
def settings() -> Settings:
    return Settings(poll_timeout_seconds=1)


@pytest.fixture
def redis() -> fakeredis.FakeRedis:
    return fakeredis.FakeRedis(decode_responses=True)


@pytest.fixture
def worker(redis: fakeredis.FakeRedis, settings: Settings) -> Worker:
    return Worker(redis, settings)


def push_job(redis: fakeredis.FakeRedis, settings: Settings, job: dict) -> None:
    redis.rpush(settings.jobs_queue, json.dumps(job))


def pop_result(redis: fakeredis.FakeRedis, settings: Settings) -> dict:
    raw = redis.lpop(settings.results_queue)
    assert raw is not None, "expected a result on the results queue"
    return json.loads(raw)


def test_ping_job_produces_a_successful_result(redis, settings, worker):
    push_job(redis, settings, {"id": "job-1", "type": "ping", "payload": {"n": 1}})

    assert worker.run_once() is True

    result = pop_result(redis, settings)
    assert result["job_id"] == "job-1"
    assert result["status"] == "succeeded"
    assert result["data"]["pong"] is True
    assert result["data"]["echo"] == {"n": 1}
    assert result["error"] is None


def test_empty_queue_returns_false_without_publishing(redis, settings, worker):
    assert worker.run_once() is False
    assert redis.llen(settings.results_queue) == 0


def test_unknown_job_type_is_reported_as_failed(redis, settings, worker):
    push_job(redis, settings, {"id": "job-2", "type": "does-not-exist"})

    worker.run_once()

    result = pop_result(redis, settings)
    assert result["status"] == "failed"
    assert "unknown job type" in result["error"]


@pytest.mark.parametrize(
    "raw",
    [
        "not json at all",
        json.dumps({"type": "ping"}),  # missing id
        json.dumps({"id": "", "type": "ping"}),  # empty id
        json.dumps({"id": "x", "type": "ping", "admin": True}),  # unexpected field
    ],
)
def test_malformed_job_goes_to_dead_letter_queue(redis, settings, worker, raw):
    redis.rpush(settings.jobs_queue, raw)

    assert worker.run_once() is True

    assert redis.lrange(settings.dead_letter_queue, 0, -1) == [raw]
    assert redis.llen(settings.results_queue) == 0


def test_handler_exception_does_not_crash_the_worker(redis, settings, worker):
    @handler("explode-for-test")
    def explode(_job):
        raise RuntimeError("boom")

    push_job(redis, settings, {"id": "job-3", "type": "explode-for-test"})
    push_job(redis, settings, {"id": "job-4", "type": "ping"})

    worker.run_once()
    worker.run_once()

    failed = pop_result(redis, settings)
    succeeded = pop_result(redis, settings)
    assert failed["status"] == "failed"
    assert failed["error"] == "RuntimeError: boom"
    assert succeeded["job_id"] == "job-4"
    assert succeeded["status"] == "succeeded"


def test_jobs_are_processed_in_fifo_order(redis, settings, worker):
    for n in range(3):
        push_job(redis, settings, {"id": f"job-{n}", "type": "ping"})

    for _ in range(3):
        worker.run_once()

    ids = [pop_result(redis, settings)["job_id"] for _ in range(3)]
    assert ids == ["job-0", "job-1", "job-2"]


def test_stop_ends_the_loop(redis, settings, worker):
    worker.stop()
    worker.run_forever()  # must return immediately instead of blocking
