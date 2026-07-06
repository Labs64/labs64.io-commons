"""FastAPI dependencies over the bound auth context."""

from __future__ import annotations

from fastapi import HTTPException

from .context import UserContext, current_context


def current_user() -> UserContext:
    context = current_context()
    if context is None:
        raise HTTPException(status_code=401, detail="Unauthorized")
    return context


def require_roles(*roles: str):
    """Dependency factory: caller must hold at least one of ``roles`` (403 otherwise)."""

    def dependency() -> UserContext:
        context = current_user()
        if not context.has_any_role(*roles):
            raise HTTPException(status_code=403, detail="Forbidden")
        return context

    return dependency
