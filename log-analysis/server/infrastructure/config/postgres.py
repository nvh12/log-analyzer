import asyncpg
from infrastructure.config.settings import settings

pool: asyncpg.Pool | None = None


async def connect() -> None:
    global pool
    pool = await asyncpg.create_pool(
        dsn=settings.POSTGRES_DSN,
        min_size=settings.POSTGRES_MIN_CONNECTIONS,
        max_size=settings.POSTGRES_MAX_CONNECTIONS,
    )


async def close() -> None:
    global pool
    if pool:
        await pool.close()
        pool = None
