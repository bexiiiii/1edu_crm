import httpx
import time
import logging
from config import (
    API_BASE,
    KC_URL,
    KC_REALM,
    KC_CLIENT_ID,
    KC_CLIENT_SECRET,
    SUPER_ADMIN_USERNAME,
    SUPER_ADMIN_PASSWORD,
)

logger = logging.getLogger(__name__)

_token: str | None = None
_token_exp: float = 0


async def _get_token() -> str:
    global _token, _token_exp
    if _token and time.time() < _token_exp - 30:
        return _token

    payload = {
        "client_id": KC_CLIENT_ID,
    }
    if KC_CLIENT_SECRET:
        payload["client_secret"] = KC_CLIENT_SECRET

    if SUPER_ADMIN_USERNAME and SUPER_ADMIN_PASSWORD:
        payload.update({
            "grant_type": "password",
            "username": SUPER_ADMIN_USERNAME,
            "password": SUPER_ADMIN_PASSWORD,
        })
    else:
        payload["grant_type"] = "client_credentials"

    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.post(
            f"{KC_URL}/realms/{KC_REALM}/protocol/openid-connect/token",
            data=payload,
        )
        resp.raise_for_status()
        data = resp.json()
        _token = data["access_token"]
        _token_exp = time.time() + data.get("expires_in", 300)
        return _token


async def _headers() -> dict:
    token = await _get_token()
    return {"Authorization": f"Bearer {token}", "Content-Type": "application/json"}


async def get_platform_kpis() -> dict:
    async with httpx.AsyncClient(timeout=15) as client:
        r = await client.get(f"{API_BASE}/api/v1/admin/analytics/platform", headers=await _headers())
        r.raise_for_status()
        return r.json().get("data", {})


async def get_tenants(status: str | None = None) -> list[dict]:
    params = {}
    if status:
        params["status"] = status
    async with httpx.AsyncClient(timeout=15) as client:
        r = await client.get(f"{API_BASE}/api/v1/admin/tenants", params=params, headers=await _headers())
        r.raise_for_status()
        data = r.json().get("data", [])
        return data if isinstance(data, list) else []


async def get_tenant(tenant_id: str) -> dict:
    async with httpx.AsyncClient(timeout=15) as client:
        r = await client.get(f"{API_BASE}/api/v1/admin/tenants/{tenant_id}/overview", headers=await _headers())
        r.raise_for_status()
        return r.json().get("data", {})


async def ban_tenant(tenant_id: str, reason: str) -> bool:
    async with httpx.AsyncClient(timeout=10) as client:
        r = await client.post(
            f"{API_BASE}/api/v1/admin/tenants/{tenant_id}/ban",
            json={"reason": reason},
            headers=await _headers(),
        )
        return r.is_success


async def unban_tenant(tenant_id: str) -> bool:
    async with httpx.AsyncClient(timeout=10) as client:
        r = await client.post(f"{API_BASE}/api/v1/admin/tenants/{tenant_id}/unban", headers=await _headers())
        return r.is_success


async def change_plan(tenant_id: str, plan: str) -> bool:
    async with httpx.AsyncClient(timeout=10) as client:
        r = await client.patch(
            f"{API_BASE}/api/v1/admin/tenants/{tenant_id}/plan",
            json={"plan": plan},
            headers=await _headers(),
        )
        return r.is_success


async def activate_subscription(tenant_id: str, plan: str, billing: str) -> bool:
    async with httpx.AsyncClient(timeout=10) as client:
        r = await client.post(
            f"{API_BASE}/api/v1/admin/tenants/{tenant_id}/subscription/activate",
            json={"plan": plan, "billingPeriod": billing},
            headers=await _headers(),
        )
        return r.is_success


async def get_revenue_trend(months: int = 6) -> list:
    async with httpx.AsyncClient(timeout=15) as client:
        r = await client.get(
            f"{API_BASE}/api/v1/admin/analytics/revenue-trend",
            params={"months": months},
            headers=await _headers(),
        )
        r.raise_for_status()
        return r.json().get("data", [])
