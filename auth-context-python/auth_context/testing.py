"""Test support: bind an AuthContext without going through headers."""

from __future__ import annotations

from contextlib import contextmanager

from .context import AuthContext, _current


@contextmanager
def set_auth_context(
    user: str = "test-user",
    tenant: str | None = "t_test",
    scopes: tuple[str, ...] = (),
    request_id: str = "test-request-id",
):
    context = AuthContext(
        user_id=user,
        tenant_id=tenant,
        scopes=frozenset(scopes),
        request_id=request_id,
    )
    token = _current.set(context)
    try:
        yield context
    finally:
        _current.reset(token)


try:  # pytest fixture, available when pytest is installed
    import pytest

    @pytest.fixture
    def auth_context():
        with set_auth_context() as context:
            yield context

except ImportError:  # pragma: no cover
    pass

