"""Background worker scaler: polls scale:replicas and adjusts gunicorn workers.

One worker instance holds the Redis lock at a time. If that worker is killed
(e.g. during a scale-down), the lock expires and another worker takes over within
one poll interval.
"""
import asyncio
import contextlib
import logging
import os
import signal
import uuid

from redis.asyncio import Redis

logger = logging.getLogger(__name__)

_LOCK_KEY = "scale:scaler_lock"
_CURRENT_KEY = "scale:current_workers"
_REPLICAS_KEY = "scale:replicas"
_LOCK_TTL = 90  # seconds — must be > poll_interval

# POSIX-only signals used by gunicorn for dynamic worker scaling.
# None on Windows; gunicorn runs on Linux in production so this is fine.
_SIGTTIN = getattr(signal, "SIGTTIN", None)
_SIGTTOU = getattr(signal, "SIGTTOU", None)


async def init(redis: Redis, default_workers: int) -> None:
    """Resets tracked worker count to the startup default, unless a scaler lock is already held.

    Skipping the reset when the lock exists prevents a respawned worker from
    overwriting a count that the active scaler has already advanced past the default.
    """
    if not await redis.exists(_LOCK_KEY):
        await redis.set(_CURRENT_KEY, str(default_workers))


async def run(
    redis: Redis,
    pid_file: str,
    default_workers: int,
    min_workers: int,
    max_workers: int,
    poll_interval: int,
) -> None:
    """Persistent background task. Competes for the scaler lock; only the holder
    polls Redis and sends gunicorn signals. Others wait and retry each poll_interval."""
    worker_id = str(uuid.uuid4())
    held = False  # tracks whether this worker currently owns the lock

    while True:
        try:
            acquired = await redis.set(_LOCK_KEY, worker_id, nx=True, ex=_LOCK_TTL)
            if not acquired:
                await asyncio.sleep(poll_interval)
                continue

            held = True
            logger.info("Scaler lock acquired (worker=%s)", worker_id[:8])

            while True:
                await asyncio.sleep(poll_interval)

                owner = await redis.get(_LOCK_KEY)
                if owner != worker_id:
                    logger.warning("Scaler lock lost — yielding to new holder")
                    break

                await redis.expire(_LOCK_KEY, _LOCK_TTL)

                target_str = await redis.get(_REPLICAS_KEY)
                current_str = await redis.get(_CURRENT_KEY)
                target = max(min_workers, min(max_workers,
                             int(target_str) if target_str else default_workers))
                current = int(current_str) if current_str else default_workers

                if target == current:
                    continue

                if _SIGTTIN is None or _SIGTTOU is None:
                    logger.debug("Dynamic scaling requires POSIX — unavailable on this platform")
                    continue

                master_pid = _read_pid(pid_file)
                if master_pid is None:
                    logger.debug(
                        "No gunicorn PID file at %s — scaling deferred "
                        "(gunicorn still booting, or running under plain uvicorn in dev/test)",
                        pid_file,
                    )
                    continue

                delta = target - current
                sig = _SIGTTIN if delta > 0 else _SIGTTOU
                logger.info(
                    "Scaling workers %d → %d (gunicorn pid=%d, %d x %s)",
                    current, target, master_pid, abs(delta), sig,
                )
                for _ in range(abs(delta)):
                    os.kill(master_pid, sig)
                    await asyncio.sleep(0.1)  # let gunicorn drain its signal queue between sends

                await redis.set(_CURRENT_KEY, str(target))

            held = False  # broke out of inner loop normally; lock was lost or expired

        except asyncio.CancelledError:
            if held:
                with contextlib.suppress(Exception):
                    await redis.delete(_LOCK_KEY)
            raise
        except Exception as e:
            if held:
                with contextlib.suppress(Exception):
                    await redis.delete(_LOCK_KEY)
            held = False
            logger.error("Scaler error: %s — retrying in %ds", e, poll_interval, exc_info=True)
            await asyncio.sleep(poll_interval)


def _read_pid(pid_file: str) -> int | None:
    try:
        with open(pid_file) as f:
            return int(f.read().strip())
    except (FileNotFoundError, ValueError, OSError):
        return None
