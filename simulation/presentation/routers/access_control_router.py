import time
from datetime import datetime, timezone
from ipaddress import ip_address

from fastapi import APIRouter, Depends, HTTPException

from dependencies.admin_auth import require_admin_key
from infrastructure.config.redis import redis_client
from presentation.schemas.access_control_schemas import Severity, BlockRequest, RateLimitRequest

router = APIRouter(dependencies=[Depends(require_admin_key)])


def _parse_ip(ip: str) -> str:
    try:
        ip_address(ip)
    except ValueError:
        raise HTTPException(status_code=422, detail=f"Invalid IP address: {ip!r}")
    return ip

_WHITELIST_KEY = "whitelist:ips"
_BLOCKLIST_SET_KEY = "blocklist:ips"
_BLOCK_PREFIX = "blocklist:ip:"
_LIMIT_PREFIX = "ratelimit:ip:"
_LIMIT_SUFFIX = ":limit"
_WINDOW_END_SUFFIX = ":window_end"
_BRUTE_PREFIX = "brute:attempts:"
_WINDOW_SECONDS = 60


def _block_ttl(severity: Severity) -> int:
    return {Severity.LOW: 300, Severity.MEDIUM: 1800, Severity.HIGH: 7200, Severity.CRITICAL: 86400}[severity]


def _rpm(severity: Severity) -> int:
    return {Severity.LOW: 30, Severity.MEDIUM: 10, Severity.HIGH: 3, Severity.CRITICAL: 1}[severity]


# --- blocklist ---

@router.get("/blocklist")
async def list_blocked() -> dict:
    ips = await redis_client.smembers(_BLOCKLIST_SET_KEY)
    result = {}
    for ip in ips:
        meta = await redis_client.get(f"{_BLOCK_PREFIX}{ip}")
        ttl = await redis_client.ttl(f"{_BLOCK_PREFIX}{ip}")
        if meta:
            result[ip] = {"meta": meta, "ttl_seconds": ttl}
        else:
            await redis_client.srem(_BLOCKLIST_SET_KEY, ip)
    return result


@router.post("/blocklist/{ip}", status_code=201)
async def block_ip(ip: str, body: BlockRequest) -> dict:
    ip = _parse_ip(ip)
    ttl = _block_ttl(body.severity)
    meta = f"severity={body.severity.value};blocked_at={datetime.now(timezone.utc).isoformat()}"
    await redis_client.set(f"{_BLOCK_PREFIX}{ip}", meta, ex=ttl)
    await redis_client.sadd(_BLOCKLIST_SET_KEY, ip)
    return {"ip": ip, "severity": body.severity, "ttl_seconds": ttl}


@router.delete("/blocklist/{ip}")
async def unblock_ip(ip: str) -> dict:
    ip = _parse_ip(ip)
    await redis_client.delete(f"{_BLOCK_PREFIX}{ip}")
    await redis_client.srem(_BLOCKLIST_SET_KEY, ip)
    return {"ip": ip, "unblocked": True}


# --- whitelist ---

@router.get("/whitelist")
async def list_whitelisted() -> dict:
    ips = await redis_client.smembers(_WHITELIST_KEY)
    return {"ips": list(ips)}


@router.post("/whitelist/{ip}", status_code=201)
async def add_to_whitelist(ip: str) -> dict:
    ip = _parse_ip(ip)
    await redis_client.sadd(_WHITELIST_KEY, ip)
    return {"ip": ip, "whitelisted": True}


@router.delete("/whitelist/{ip}")
async def remove_from_whitelist(ip: str) -> dict:
    ip = _parse_ip(ip)
    await redis_client.srem(_WHITELIST_KEY, ip)
    return {"ip": ip, "removed": True}


@router.put("/whitelist")
async def replace_whitelist(ips: list[str]) -> dict:
    async with redis_client.pipeline() as pipe:
        pipe.delete(_WHITELIST_KEY)
        if ips:
            parsed_ips = [_parse_ip(ip) for ip in ips]
            pipe.sadd(_WHITELIST_KEY, *parsed_ips)
        await pipe.execute()
    return {"replaced": True, "count": len(ips)}


# --- ratelimit ---

@router.get("/ratelimit")
async def list_rate_limits() -> dict:
    result = {}
    cursor = 0
    while True:
        cursor, keys = await redis_client.scan(cursor, match=f"{_LIMIT_PREFIX}*{_LIMIT_SUFFIX}", count=100)
        for key in keys:
            limit = await redis_client.get(key)
            ttl = await redis_client.ttl(key)
            ip = key.removeprefix(_LIMIT_PREFIX).removesuffix(_LIMIT_SUFFIX)
            window_end = await redis_client.get(f"{_LIMIT_PREFIX}{ip}{_WINDOW_END_SUFFIX}")
            result[ip] = {
                "rpm": int(limit) if limit else None,
                "ttl_seconds": ttl,
                "window_end": int(window_end) if window_end else None,
            }
        if cursor == 0:
            break
    return result


@router.post("/ratelimit/{ip}", status_code=201)
async def set_rate_limit(ip: str, body: RateLimitRequest) -> dict:
    ip = _parse_ip(ip)
    rpm = _rpm(body.severity)
    ttl = _block_ttl(body.severity)
    window_end = int(time.time()) + _WINDOW_SECONDS
    pipe = redis_client.pipeline()
    pipe.set(f"{_LIMIT_PREFIX}{ip}{_LIMIT_SUFFIX}", rpm, ex=ttl)
    pipe.set(f"{_LIMIT_PREFIX}{ip}", 0, ex=_WINDOW_SECONDS)
    pipe.set(f"{_LIMIT_PREFIX}{ip}{_WINDOW_END_SUFFIX}", window_end, ex=ttl)
    await pipe.execute()
    return {"ip": ip, "severity": body.severity, "rpm": rpm, "ttl_seconds": ttl, "window_end": window_end}


@router.delete("/ratelimit/{ip}")
async def clear_rate_limit(ip: str) -> dict:
    ip = _parse_ip(ip)
    await redis_client.delete(
        f"{_LIMIT_PREFIX}{ip}{_LIMIT_SUFFIX}",
        f"{_LIMIT_PREFIX}{ip}",
        f"{_LIMIT_PREFIX}{ip}{_WINDOW_END_SUFFIX}",
    )
    return {"ip": ip, "cleared": True}


# --- brute force ---

@router.get("/brute")
async def list_brute_force() -> dict:
    result = {}
    cursor = 0
    while True:
        cursor, keys = await redis_client.scan(cursor, match=f"{_BRUTE_PREFIX}*", count=100)
        for key in keys:
            attempts = await redis_client.get(key)
            ttl = await redis_client.ttl(key)
            ip = key.removeprefix(_BRUTE_PREFIX)
            result[ip] = {"attempts": int(attempts) if attempts else 0, "ttl_seconds": ttl}
        if cursor == 0:
            break
    return result


@router.get("/brute/{ip}")
async def get_brute_force(ip: str) -> dict:
    ip = _parse_ip(ip)
    attempts = await redis_client.get(f"{_BRUTE_PREFIX}{ip}")
    if attempts is None:
        raise HTTPException(status_code=404, detail=f"No active brute force tracking for {ip!r}")
    ttl = await redis_client.ttl(f"{_BRUTE_PREFIX}{ip}")
    return {"ip": ip, "attempts": int(attempts), "ttl_seconds": ttl}


@router.delete("/brute/{ip}")
async def reset_brute_force(ip: str) -> dict:
    ip = _parse_ip(ip)
    deleted = await redis_client.delete(f"{_BRUTE_PREFIX}{ip}")
    return {"ip": ip, "reset": deleted > 0}
