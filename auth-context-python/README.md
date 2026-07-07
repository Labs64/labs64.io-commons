# l64-auth-context

Trusted gateway auth-context (`X-Auth-*`) parsing, enforcement and propagation for Labs64.IO Python services.

Mirror of `l64-auth-context-spring-boot-starter`; behavior is pinned by the shared vectors in `../test-vectors/`.

## Install

```bash
pip install "l64-auth-context @ git+https://github.com/Labs64/labs64.io-commons.git@master#subdirectory=auth-context-python"
```

## Usage

```python
from fastapi import Depends, FastAPI
from l64_auth_context import AuthContextMiddleware, UserContext
from l64_auth_context.fastapi_deps import current_user, require_roles

app = FastAPI()

@app.get("/process")
def process(context: UserContext = Depends(require_roles("auditflow-role"))):
    return {"tenant": context.tenant_id}

app = AuthContextMiddleware(app, public_paths=("/health", "/ready", "/live"))
```

Outbound propagation (on-behalf-of calls):

```python
import httpx
from l64_auth_context.httpx_hook import propagate_auth_context

client = httpx.Client(event_hooks={"request": [propagate_auth_context]})
```

Tests:

```python
from l64_auth_context.testing import set_user_context

with set_user_context(user="jdoe", tenant="t_100", roles=("admin-role",)):
    ...
```
