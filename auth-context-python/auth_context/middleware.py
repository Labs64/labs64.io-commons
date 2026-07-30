"""Pure-ASGI middleware binding the gateway auth context per request.

Non-public paths fail closed with 401 when no valid identity is present, so
an accidentally exposed service rejects unauthenticated traffic.
"""

from __future__ import annotations

from collections.abc import Iterable
from datetime import datetime, timezone
import json

from .context import DEFAULT_PUBLIC_PATHS, _current, parse_context

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

        standardizing_send = self._standardizing_send(
            send, context.request_id if context is not None else None
        )
        if context is None:
            await self.app(scope, receive, standardizing_send)
            return

        token = _current.set(context)
        try:
            await self.app(scope, receive, standardizing_send)
        finally:
            _current.reset(token)

    @staticmethod
    def _error_body(code: str, message: str, trace_id: str | None = None) -> bytes:
        body = {
            "code": code,
            "message": message,
            "timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        }
        if trace_id is not None:
            body["traceId"] = trace_id
        return json.dumps(body, separators=(",", ":")).encode()

    @classmethod
    async def _unauthorized(cls, send):
        body = cls._error_body("UNAUTHORIZED", "Unauthorized")
        await send(
            {
                "type": "http.response.start",
                "status": 401,
                "headers": [(b"content-type", b"application/json")],
            }
        )
        await send({"type": "http.response.body", "body": body})

    @classmethod
    def _standardizing_send(cls, send, trace_id: str | None = None):
        rejected_status = None
        body_sent = False

        async def standardizing_send(message):
            nonlocal rejected_status, body_sent
            if message["type"] == "http.response.start" and message["status"] in (401, 403):
                rejected_status = message["status"]
                await send(
                    {
                        **message,
                        "headers": [(b"content-type", b"application/json")],
                    }
                )
                return
            if message["type"] == "http.response.body" and rejected_status is not None:
                if not body_sent:
                    code = "UNAUTHORIZED" if rejected_status == 401 else "FORBIDDEN"
                    await send(
                        {
                            "type": "http.response.body",
                            "body": cls._error_body(code, code.title(), trace_id),
                        }
                    )
                    body_sent = True
                return
            await send(message)

        return standardizing_send

