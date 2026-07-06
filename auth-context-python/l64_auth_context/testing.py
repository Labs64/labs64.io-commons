"""Test support: bind a UserContext without going through headers."""

from __future__ import annotations

from contextlib import contextmanager

from .context import UserContext, _current


@contextmanager
def set_user_context(
    user: str = "test-user",
    tenant: str | None = "t_test",
    roles: tuple[str, ...] = (),
    request_id: str = "test-request-id",
):
    context = UserContext(
        user_id=user,
        tenant_id=tenant,
        roles=frozenset(roles),
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
    def user_context():
        with set_user_context() as context:
            yield context

except ImportError:  # pragma: no cover
    pass
