from auth_context import current_context, require_context
from auth_context.testing import set_user_context


def test_set_user_context_binds_and_resets():
    assert current_context() is None
    with set_user_context(user="jdoe", tenant="t_9", roles=("admin-role",)) as context:
        assert require_context() is context
        assert context.has_role("admin-role")
        assert context.tenant_id == "t_9"
    assert current_context() is None


def test_fixture_binds_default_context(user_context):
    assert require_context() is user_context
    assert user_context.user_id == "test-user"
