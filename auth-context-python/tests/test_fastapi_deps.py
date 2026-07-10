from fastapi import Depends, FastAPI
from fastapi.testclient import TestClient

from auth_context import AuthContextMiddleware, AuthContext
from auth_context.fastapi_deps import current_auth, require_scopes

FULL_CONTEXT = {
    "X-Auth-User": "jdoe",
    "X-Auth-Scopes": "ecommerce:read",
    "X-Auth-Tenant": "t_100",
    "X-Request-ID": "req-1",
}


def build_client() -> TestClient:
    app = FastAPI()

    @app.get("/me")
    def me(context: AuthContext = Depends(current_auth)):
        return {"user": context.user_id}

    @app.get("/admin")
    def admin(context: AuthContext = Depends(require_scopes("account:read"))):
        return {"user": context.user_id}

    @app.get("/shop")
    def shop(context: AuthContext = Depends(require_scopes("account:read", "ecommerce:read"))):
        return {"user": context.user_id}

    return TestClient(AuthContextMiddleware(app, public_paths=()), raise_server_exceptions=True)


def test_current_auth_returns_bound_context():
    response = build_client().get("/me", headers=FULL_CONTEXT)
    assert response.status_code == 200
    assert response.json() == {"user": "jdoe"}


def test_require_scopes_forbids_missing_scope():
    response = build_client().get("/admin", headers=FULL_CONTEXT)
    assert response.status_code == 403


def test_require_scopes_any_of_matches():
    response = build_client().get("/shop", headers=FULL_CONTEXT)
    assert response.status_code == 200

