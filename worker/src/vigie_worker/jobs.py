"""Message contract between the API and the worker.

The API pushes a ``Job`` as JSON onto the jobs queue. The worker replies with a
``JobResult`` on the results queue. The worker never touches the database: the
API is the only service that owns persistent state.
"""

from datetime import UTC, datetime
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field


class Job(BaseModel):
    model_config = ConfigDict(extra="forbid")

    id: str = Field(min_length=1, max_length=64)
    type: str = Field(min_length=1, max_length=64)
    payload: dict[str, Any] = Field(default_factory=dict)


class JobResult(BaseModel):
    job_id: str
    type: str
    status: Literal["succeeded", "failed"]
    data: dict[str, Any] = Field(default_factory=dict)
    error: str | None = None
    finished_at: datetime = Field(default_factory=lambda: datetime.now(UTC))
