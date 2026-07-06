"""Trusted gateway auth-context contract (RFC-03) — mirror of the Java library.

Behavior is pinned by the shared vectors in test-vectors/auth-context-vectors.json;
a change here must update the vectors and the Java implementation together.
"""

from __future__ import annotations

import re
import uuid
from contextvars import ContextVar
from dataclasses import dataclass, field

HEADER_USER = "X-Auth-User"
HEADER_ROLES = "X-Auth-Roles"
HEADER_TENANT = "X-Auth-Tenant"
HEADER_REQUEST_ID = "X-Request-ID"

TENANT_NONE = "-"
SERVICE_PRINCIPAL_PREFIX = "svc:"

VALUE_PATTERN = re.compile(r"[a-zA-Z0-9_.:-]+")

DEFAULT_PUBLIC_PATHS: tuple[str, ...] = ("/health", "/ready", "/live", "/docs", "/openapi.json")


@dataclass(frozen=True)
class UserContext:
    """Immutable per-request identity derived from the trusted gateway headers."""

    user_id: str
    tenant_id: str | None
    roles: frozenset[str] = field(default_factory=frozenset)
    request_id: str = ""

    def has_role(self, role: str) -> bool:
        return role in self.roles

    def has_any_role(self, *candidates: str) -> bool:
        return any(role in self.roles for role in candidates)

    @property
    def is_service_principal(self) -> bool:
        return self.user_id.startswith(SERVICE_PRINCIPAL_PREFIX)


_current: ContextVar[UserContext | None] = ContextVar("l64_auth_context", default=None)


def current_context() -> UserContext | None:
    return _current.get()


def require_context() -> UserContext:
    context = _current.get()
    if context is None:
        raise RuntimeError("No UserContext bound to the current request")
    return context


def is_valid_value(value: str | None) -> bool:
    return bool(value) and VALUE_PATTERN.fullmatch(value) is not None


def parse_roles(csv: str | None) -> list[str] | None:
    """Parse the roles CSV; trimmed items, empty items dropped.

    Returns None when any non-empty item violates the value pattern.
    """
    roles: list[str] = []
    if not csv or not csv.strip():
        return roles
    for item in csv.split(","):
        role = item.strip()
        if not role:
            continue
        if not is_valid_value(role):
            return None
        roles.append(role)
    return roles


def parse_context(headers) -> UserContext | None:
    """Parse a UserContext from a case-insensitive-ready header mapping.

    ``headers`` must support ``.get(name)`` with the canonical header names
    already normalized to lower-case keys (ASGI style) or original casing —
    both are tried.

    Returns None when the identity is missing or malformed (the caller
    decides whether that means 401 or anonymous, by path).
    """

    def get(name: str) -> str | None:
        value = headers.get(name)
        if value is None:
            value = headers.get(name.lower())
        return value

    user = get(HEADER_USER)
    if not is_valid_value(user):
        return None

    roles = parse_roles(get(HEADER_ROLES))
    if roles is None:
        return None

    tenant = get(HEADER_TENANT)
    if not tenant or tenant == TENANT_NONE:
        tenant = None
    elif not is_valid_value(tenant):
        return None

    request_id = get(HEADER_REQUEST_ID)
    if not is_valid_value(request_id):
        request_id = str(uuid.uuid4())

    return UserContext(user_id=user, tenant_id=tenant, roles=frozenset(roles), request_id=request_id)
