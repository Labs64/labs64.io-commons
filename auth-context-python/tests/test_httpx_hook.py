import httpx

from l64_auth_context.httpx_hook import propagate_auth_context
from l64_auth_context.testing import set_user_context


def test_propagates_all_contract_headers():
    request = httpx.Request("GET", "http://auditflow-sink/process")
    with set_user_context(user="jdoe", tenant="t_100", roles=("admin-role",), request_id="req-1"):
        propagate_auth_context(request)
    assert request.headers["X-Auth-User"] == "jdoe"
    assert request.headers["X-Auth-Roles"] == "admin-role"
    assert request.headers["X-Auth-Tenant"] == "t_100"
    assert request.headers["X-Request-ID"] == "req-1"


def test_tenantless_context_propagates_dash():
    request = httpx.Request("GET", "http://auditflow-sink/process")
    with set_user_context(user="svc:auditflow-be", tenant=None, request_id="req-2"):
        propagate_auth_context(request)
    assert request.headers["X-Auth-Tenant"] == "-"
    assert request.headers["X-Auth-Roles"] == ""


def test_no_context_means_no_headers():
    request = httpx.Request("GET", "http://auditflow-sink/process")
    propagate_auth_context(request)
    assert "X-Auth-User" not in request.headers
    assert "X-Request-ID" not in request.headers
