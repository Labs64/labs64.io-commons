# AGENTS.md — Labs64.IO Commons

Guidance for AI agents working in this repository. Read this before making changes.

## What this is

Shared cross-service libraries for the Labs64.IO Ecosystem. Includes: the auth-context libraries (Java + Python), which parse, enforce and propagate the trusted gateway header contract, shared Spring Boot starters, plus the unified Cedar authorization assets: the shared schema/policies in `auth-policy-cedar/` and the `@Authorize` Cedar domain PEP in the Java starter (`io.labs64.authcontext.cedar`, feature-flagged via `labs64.auth.cedar.*`, engine = optional `com.cedarpolicy:cedar-java:uber` dependency).

## Critical guardrails

1. **The two implementations must stay behaviorally identical.** Any behavior change MUST update `test-vectors/auth-context-vectors.json` and BOTH implementations in the same commit. The vectors are the contract; code follows.
2. **The header contract is owned by the auth gateway** (`labs64.io-authproxy/traefik-authproxy/`). Do not add, rename or reinterpret `X-Auth-*` headers here without an RFC change.
3. **No Spring Security dependency in the Java starter.** The whole point is header-trust without an in-app security stack.
4. **No mandatory runtime dependencies in the Python package.** FastAPI/httpx integrations import lazily; the core must stay dependency-free.
5. **Fail closed.** Non-public paths without a valid user identity return 401. Never weaken this default.
6. **Value sanitization pattern `^[a-zA-Z0-9_.:-]+$`** must match the ACS (traefik-authproxy) exactly.
7. **One Cedar schema.** `auth-policy-cedar/schema.cedarschema` is the single type system for BOTH authorization tiers (edge + every module's domain policies). Schema changes must keep `just cedar` green and stay in sync with `test-vectors/cedar-request-vectors.json` — the AuthContext→Cedar request contract the Java/Python PEPs are pinned to.

## Layout

| Path | What |
|---|---|
| `auth-context-java/` | `io.labs64:auth-context-spring-boot-starter` |
| `auth-context-python/` | `auth-context-python` (package `auth_context`) |
| `auth-policy-cedar/` | THE shared Cedar schema + reference policies + `validate.sh` CI gate |
| `test-vectors/` | Canonical cross-language behavior vectors (headers + Cedar request construction) |
| `openapi-spring-boot-starter/` | `io.labs64:openapi-spring-boot-starter` |

## Commands

| Task | Command |
|---|---|
| Build + test everything | `just build` |
| Java tests | `cd auth-context-java && mvn test` |
| OpenAPI starter tests | `cd openapi-spring-boot-starter && mvn test` |
| Python tests | `cd auth-context-python && .venv/bin/pytest` (after `just python-venv`) |
| Install Java lib locally | `just install-java` |
| Cedar schema/policy gate | `just cedar` (requires `cargo install cedar-policy-cli`) |

## Conventions

- Java 17 bytecode target (consumers run 17+), Spring Boot 4.x parent.
- Python ≥ 3.13, no runtime deps, `pyproject.toml` packaging.
- Versioning: bump both libraries together; consumers consume from Labs64 Nexus and git+https.
