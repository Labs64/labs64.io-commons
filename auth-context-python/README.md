# auth-context-python

Trusted gateway auth-context (`X-Auth-*`) parsing, enforcement and propagation for Labs64.IO Python services.

Mirror of `auth-context-spring-boot-starter`; behavior is pinned by the shared vectors in `../test-vectors/`.

## Install

```bash
pip install "auth-context-python @ git+https://github.com/Labs64/labs64.io-commons.git@master#subdirectory=auth-context-python"
```

## Usage

```python
from fastapi import Depends, FastAPI
from auth_context import AuthContextMiddleware, AuthContext
from auth_context.fastapi_deps import current_auth, require_scopes

app = FastAPI()

@app.get("/process")
def process(context: AuthContext = Depends(require_scopes("auditflow-scope"))):
    return {"tenant": context.tenant_id}

app = AuthContextMiddleware(app, public_paths=("/health", "/ready", "/live"))
```

Outbound propagation (on-behalf-of calls):

```python
import httpx
from auth_context.httpx_hook import propagate_auth_context

client = httpx.Client(event_hooks={"request": [propagate_auth_context]})
```

Tests:

```python
from auth_context.testing import set_auth_context

with set_auth_context(user="jdoe", tenant="t_100", scopes=("account:read",)):
    ...
```

