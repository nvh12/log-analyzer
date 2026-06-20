"""Atomic ownership-checked Redis lock primitives.

A blind EXPIRE/DEL on a lock key is a classic distributed-lock bug: if the TTL
lapses while the holder is still alive (e.g. a Redis blip delaying a refresh),
a second process can acquire the "freed" lock — and the original holder's next
blind refresh/release then stomps on the new owner. These Lua scripts make the
ownership check and the mutation atomic, so a holder that's lost the lock
notices (via a falsy return) instead of clobbering whoever holds it now.
"""
from redis.asyncio import Redis

_REFRESH_IF_OWNER = """
if redis.call("get", KEYS[1]) == ARGV[1] then
    return redis.call("expire", KEYS[1], ARGV[2])
else
    return 0
end
"""
_RELEASE_IF_OWNER = """
if redis.call("get", KEYS[1]) == ARGV[1] then
    return redis.call("del", KEYS[1])
else
    return 0
end
"""


class RedisLock:
    """A single named lock key, with ownership-checked refresh/release bound to a redis client."""

    def __init__(self, redis: Redis, key: str):
        self._redis = redis
        self._key = key
        self._refresh_if_owner = redis.register_script(_REFRESH_IF_OWNER)
        self._release_if_owner = redis.register_script(_RELEASE_IF_OWNER)

    async def acquire(self, owner_id: str, ttl: int) -> bool:
        return bool(await self._redis.set(self._key, owner_id, nx=True, ex=ttl))

    async def refresh_if_owner(self, owner_id: str, ttl: int) -> bool:
        return bool(await self._refresh_if_owner(keys=[self._key], args=[owner_id, ttl]))

    async def release_if_owner(self, owner_id: str) -> bool:
        return bool(await self._release_if_owner(keys=[self._key], args=[owner_id]))
