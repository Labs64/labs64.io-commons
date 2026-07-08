from fastapi import Depends, FastAPI
from fastapi.testclient import TestClient

from auth_context import AuthContextMiddleware, UserContext
from auth_context.fastapi_deps import current_user, require_roles

FULL_CONTEXT = {
    "X-Auth-User": "jdoe",
    "X-Auth-Roles": "ecommerce-role",
    "X-Auth-Tenant": "t_100",
    "X-Request-ID": "req-1",
}


def build_client() -> TestClient:
    app = FastAPI()

    @app.get("/me")
    def me(context: UserContext = Depends(current_user)):
        return {"user": context.user_id}

    @app.get("/admin")
    def admin(context: UserContext = Depends(require_roles("admin-role"))):
        return {"user": context.user_id}

    @app.get("/shop")
    def shop(context: UserContext = Depends(require_roles("admin-role", "ecommerce-role"))):
        return {"user": context.user_id}

    return TestClient(AuthContextMiddleware(app, public_paths=()), raise_server_exceptions=True)


def test_current_user_returns_bound_context():
    response = build_client().get("/me", headers=FULL_CONTEXT)
    assert response.status_code == 200
    assert response.json() == {"user": "jdoe"}


def test_require_roles_forbids_missing_role():
    response = build_client().get("/admin", headers=FULL_CONTEXT)
    assert response.status_code == 403


def test_require_roles_any_of_matches():
    response = build_client().get("/shop", headers=FULL_CONTEXT)
    assert response.status_code == 200
