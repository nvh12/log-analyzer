from fastapi import Header, HTTPException

from infrastructure.config.settings import settings


async def require_admin_key(x_admin_key: str | None = Header(default=None)) -> None:
    if x_admin_key != settings.ADMIN_API_KEY:
        raise HTTPException(status_code=403, detail="Forbidden")
