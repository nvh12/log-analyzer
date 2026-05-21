import os
import asyncpg

_VERSIONS_DIR = os.path.join(os.path.dirname(__file__), "versions")

_BOOTSTRAP = """
    CREATE TABLE IF NOT EXISTS schema_migrations (
        version    VARCHAR(20) PRIMARY KEY,
        applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
    )
"""


async def run(pool: asyncpg.Pool) -> None:
    async with pool.acquire() as conn:
        await conn.execute(_BOOTSTRAP)
        applied = {
            row["version"]
            for row in await conn.fetch("SELECT version FROM schema_migrations")
        }
        for filename in sorted(f for f in os.listdir(_VERSIONS_DIR) if f.endswith(".sql")):
            version = filename.split("_")[0]
            if version not in applied:
                with open(os.path.join(_VERSIONS_DIR, filename), encoding="utf-8") as f:
                    sql = f.read()
                async with conn.transaction():
                    await conn.execute(sql)
                    await conn.execute(
                        "INSERT INTO schema_migrations (version) VALUES ($1)", version
                    )
