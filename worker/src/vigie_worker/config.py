from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Worker configuration, read from environment variables (VIGIE_ prefix)."""

    model_config = SettingsConfigDict(env_prefix="VIGIE_")

    redis_url: str = "redis://localhost:6379/0"
    jobs_queue: str = "vigie:jobs"
    results_queue: str = "vigie:results"
    dead_letter_queue: str = "vigie:jobs:dead"
    # How long one blocking pop waits before the loop re-checks for shutdown.
    poll_timeout_seconds: int = 5
