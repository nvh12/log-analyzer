import asyncio
import logging
from contextlib import asynccontextmanager
from application.ports.lock_port import LockPort
from infrastructure.config.redis import redis_client

logger = logging.getLogger(__name__)

class RedisLockAdapter(LockPort):
    """
    Distributed lock implementation using Redis SET NX PX.
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
        """
        key = self._get_key(resource)
        # Simple SET NX PX lock
        # In a real production app, use something like Redlock for safety
        success = await redis_client.set(key, "locked", ex=timeout, nx=True)
        if not success:
            logger.debug(f"Failed to acquire lock for {resource}")
            # Raise exception to skip job execution if lock is not acquired
            raise RuntimeError(f"Could not acquire lock for {resource}")
        
        try:
            yield
        finally:
            await redis_client.delete(key)
