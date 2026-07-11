import httpx

from auth_context.httpx_hook import propagate_auth_context
from auth_context.testing import set_auth_context


def test_propagates_all_contract_headers():
    request = httpx.Request("GET", "http://auditflow-sink/process")
    with set_auth_context(user="jdoe", tenant="t_100", scopes=("account:read",), request_id="req-1"):
        propagate_auth_context(request)
    assert request.headers["X-Auth-User"] == "jdoe"
    assert request.headers["X-Auth-Scopes"] == "account:read"
    assert request.headers["X-Auth-Tenant"] == "t_100"
    assert request.headers["X-Request-ID"] == "req-1"


def test_tenantless_context_propagates_dash():
    request = httpx.Request("GET", "http://auditflow-sink/process")
    with set_auth_context(user="svc:auditflow-be", tenant=None, request_id="req-2"):
        propagate_auth_context(request)
    assert request.headers["X-Auth-Tenant"] == "-"
    assert request.headers["X-Auth-Scopes"] == ""


def test_no_context_means_no_headers():
    request = httpx.Request("GET", "http://auditflow-sink/process")
    propagate_auth_context(request)
    assert "X-Auth-User" not in request.headers
    assert "X-Request-ID" not in request.headers

