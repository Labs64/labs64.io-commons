from auth_context import current_context, require_context
from auth_context.testing import set_auth_context


def test_set_auth_context_binds_and_resets():
    assert current_context() is None
    with set_auth_context(user="jdoe", tenant="t_9", scopes=("account:read",)) as context:
        assert require_context() is context
        assert context.has_scope("account:read")
        assert context.tenant_id == "t_9"
    assert current_context() is None


def test_fixture_binds_default_context(auth_context):
    assert require_context() is auth_context
    assert auth_context.user_id == "test-user"

