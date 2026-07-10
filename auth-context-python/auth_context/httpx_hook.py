"""Outbound propagation for on-behalf-of calls (service-to-service).

Usable as an httpx event hook::

    client = httpx.Client(event_hooks={"request": [propagate_auth_context]})
"""

from __future__ import annotations

from .context import (
    HEADER_REQUEST_ID,
    HEADER_SCOPES,
    HEADER_TENANT,
    HEADER_USER,
    TENANT_NONE,
    current_context,
)


def propagate_auth_context(request) -> None:
    context = current_context()
    if context is None:
        return
    request.headers[HEADER_USER] = context.user_id
    request.headers[HEADER_SCOPES] = ",".join(sorted(context.scopes))
    request.headers[HEADER_TENANT] = context.tenant_id if context.tenant_id is not None else TENANT_NONE
    request.headers[HEADER_REQUEST_ID] = context.request_id

