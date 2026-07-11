"""FastAPI dependencies over the bound auth context."""

from __future__ import annotations

from fastapi import HTTPException

from .context import AuthContext, current_context


def current_auth() -> AuthContext:
    context = current_context()
    if context is None:
        raise HTTPException(status_code=401, detail="Unauthorized")
    return context


def require_scopes(*scopes: str):
    """Dependency factory: caller must hold at least one of ``scopes`` (403 otherwise)."""

    def dependency() -> AuthContext:
        context = current_auth()
        if not context.has_any_scope(*scopes):
            raise HTTPException(status_code=403, detail="Forbidden")
        return context

    return dependency

