import logging
import signal
from types import FrameType

from redis import Redis

from vigie_worker.config import Settings
from vigie_worker.worker import Worker


def main() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    settings = Settings()
    redis = Redis.from_url(settings.redis_url, decode_responses=True)
    worker = Worker(redis, settings)

    def shutdown(signum: int, _frame: FrameType | None) -> None:
        logging.getLogger(__name__).info("received signal %s, shutting down", signum)
        worker.stop()

    # Docker sends SIGTERM on `docker compose stop`; finish the current job first.
    signal.signal(signal.SIGTERM, shutdown)
    signal.signal(signal.SIGINT, shutdown)

    worker.run_forever()


if __name__ == "__main__":
    main()
