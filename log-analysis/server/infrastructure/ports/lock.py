import asyncio
import logging
import uuid
from contextlib import asynccontextmanager
from application.ports.lock_port import LockPort, LockNotAcquiredError
from infrastructure.config.redis import redis_client

logger = logging.getLogger(__name__)

# Lua script: only delete the key if the stored token matches ours.
# Prevents a timed-out owner from releasing a lock acquired by another process.
_RELEASE_SCRIPT = """
if redis.call("get", KEYS[1]) == ARGV[1] then
    return redis.call("del", KEYS[1])
else
    return 0
end
"""

class RedisLockAdapter(LockPort):
    """
    Distributed lock implementation using Redis SET NX with UUID ownership.
    The lock value is a unique token so only the acquiring process can release it.
    """
    def __init__(self, key_prefix: str = "lock:"):
        self._prefix = key_prefix

    def _get_key(self, resource: str) -> str:
        """Generate a namespaced Redis key for the lock."""
        return f"{self._prefix}{resource}"

    @asynccontextmanager
    async def lock(self, resource: str, timeout: int = 10):
        """
        Attempts to acquire a lock in Redis.
        Raises RuntimeError if the lock is already held.
        Releases only if the stored token still matches, preventing accidental
        release of a lock re-acquired by another process after a timeout.
        """
        key = self._get_key(resource)
        token = str(uuid.uuid4())
        success = await redis_client.set(key, token, ex=timeout, nx=True)
        if not success:
            logger.debug("Failed to acquire lock for %s", resource)
            raise LockNotAcquiredError(f"Could not acquire lock for {resource}")

        try:
            yield
        finally:
            try:
                await redis_client.eval(_RELEASE_SCRIPT, 1, key, token)
            except Exception as e:
                logger.error("Failed to release lock for %s: %s — lock will expire after timeout", resource, e)
