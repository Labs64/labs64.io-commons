# AGENTS.md — Labs64.IO Commons

Guidance for AI agents working in this repository. Read this before making changes.

## What this is

Shared cross-service libraries for the Labs64.IO Ecosystem. Currently: the RFC-03 auth-context libraries (Java + Python), which parse, enforce and propagate the trusted gateway header contract.

## Critical guardrails

1. **The two implementations must stay behaviorally identical.** Any behavior change MUST update `test-vectors/auth-context-vectors.json` and BOTH implementations in the same commit. The vectors are the contract; code follows.
2. **The header contract is owned by RFC-03** (`labs64.io-docs-internal/rfc/`). Do not add, rename or reinterpret `X-Auth-*` headers here without an RFC change.
3. **No Spring Security dependency in the Java starter.** The whole point is header-trust without an in-app security stack.
4. **No mandatory runtime dependencies in the Python package.** FastAPI/httpx integrations import lazily; the core must stay dependency-free.
5. **Fail closed.** Non-public paths without a valid user identity return 401. Never weaken this default.
6. **Value sanitization pattern `^[a-zA-Z0-9_.:-]+$`** must match the ACS (traefik-authproxy) exactly.

## Layout

| Path | What |
|---|---|
| `auth-context-java/` | `io.labs64:l64-auth-context-spring-boot-starter` |
| `auth-context-python/` | `l64-auth-context` (package `l64_auth_context`) |
| `test-vectors/` | Canonical cross-language behavior vectors |
| `jitpack.yml` | JitPack build (Java consumption without a registry) |

## Commands

| Task | Command |
|---|---|
| Build + test everything | `just build` |
| Java tests | `cd auth-context-java && mvn test` |
| Python tests | `cd auth-context-python && .venv/bin/pytest` (after `just python-venv`) |
| Install Java lib locally | `just install-java` |

## Conventions

- Java 17 bytecode target (consumers run 17+), Spring Boot 4.x parent.
- Python ≥ 3.13, no runtime deps, `pyproject.toml` packaging.
- Versioning: bump both libraries together; consumers pin a git tag/commit (JitPack / git+https).
