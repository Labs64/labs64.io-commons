"""Executes the shared cross-language behavior vectors against the ASGI middleware.

The Java library runs the same file; a change in behavior must update the
vectors, not just one implementation.
"""

import json
from pathlib import Path

import pytest
from fastapi import FastAPI
from fastapi.testclient import TestClient

from l64_auth_context import AuthContextMiddleware, current_context

VECTORS = json.loads(
    (Path(__file__).resolve().parents[2] / "test-vectors" / "auth-context-vectors.json").read_text()
)

PROTECTED_PATH = "/customers"
PUBLIC_PATH = "/v3/api-docs"


def build_client() -> TestClient:
    app = FastAPI()

    @app.get(PROTECTED_PATH)
    @app.get(PUBLIC_PATH)
    def echo():
        context = current_context()
        if context is None:
            return {"bound": False}
        return {
            "bound": True,
            "user": context.user_id,
            "tenant": context.tenant_id,
            "roles": sorted(context.roles),
            "requestId": context.request_id,
            "servicePrincipal": context.is_service_principal,
        }

    wrapped = AuthContextMiddleware(app, public_paths=(PUBLIC_PATH,))
    return TestClient(wrapped, raise_server_exceptions=True)


@pytest.mark.parametrize("case", VECTORS["cases"], ids=lambda case: case["name"])
def test_vector(case):
    client = build_client()
    path = PUBLIC_PATH if case["public"] else PROTECTED_PATH
    response = client.get(path, headers=case["headers"])

    expect = case["expect"]
    outcome = expect["outcome"]

    if outcome == "reject":
        assert response.status_code == 401
        return

    assert response.status_code == 200
    body = response.json()

    if outcome == "anonymous":
        assert body == {"bound": False}
        return

    assert outcome == "accept"
    assert body["bound"] is True
    assert body["user"] == expect["user"]
    assert body["tenant"] == expect["tenant"]
    assert body["roles"] == sorted(expect["roles"])
    if expect["requestId"] == "GENERATED":
        assert body["requestId"]
        assert body["requestId"] != case["headers"].get("X-Request-ID")
    else:
        assert body["requestId"] == expect["requestId"]
    if "servicePrincipal" in expect:
        assert body["servicePrincipal"] is expect["servicePrincipal"]

    # The context must never leak past the request
    assert current_context() is None
