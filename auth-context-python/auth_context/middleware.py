"""Pure-ASGI middleware binding the gateway auth context per request.

Non-public paths fail closed with 401 when no valid identity is present, so
an accidentally exposed service rejects unauthenticated traffic.
"""

from __future__ import annotations

import json
from collections.abc import Iterable

from .context import DEFAULT_PUBLIC_PATHS, _current, parse_context

_UNAUTHORIZED_BODY = json.dumps({"status": 401, "error": "Unauthorized"}).encode()


class AuthContextMiddleware:
    def __init__(self, app, public_paths: Iterable[str] = DEFAULT_PUBLIC_PATHS):
        self.app = app
        self.public_paths = tuple(public_paths)

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        headers = {key.decode("latin-1").lower(): value.decode("latin-1") for key, value in scope.get("headers", [])}
        path = scope.get("path", "")
        public = any(path.startswith(prefix) for prefix in self.public_paths)

        context = parse_context(headers)
        if context is None and not public:
            await self._unauthorized(send)
            return

        if context is None:
            await self.app(scope, receive, send)
            return

        token = _current.set(context)
        try:
            await self.app(scope, receive, send)
        finally:
            _current.reset(token)

    @staticmethod
    async def _unauthorized(send):
        await send(
            {
                "type": "http.response.start",
                "status": 401,
                "headers": [(b"content-type", b"application/json")],
            }
        )
        await send({"type": "http.response.body", "body": _UNAUTHORIZED_BODY})

